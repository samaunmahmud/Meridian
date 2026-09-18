package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.CurrencyExchangeRate;
import com.meridian.backend.client.CurrencyExchangeRateResponse;
import com.meridian.backend.client.GlobalQuote;
import com.meridian.backend.client.GlobalQuoteResponse;
import com.meridian.backend.client.SymbolMatch;
import com.meridian.backend.dto.AddTickerRequest;
import com.meridian.backend.dto.PricePointResponse;
import com.meridian.backend.dto.PriceUpdateMessage;
import com.meridian.backend.dto.TickerResponse;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    // Crypto quotes are always priced against USD — Alpha Vantage's
    // CURRENCY_EXCHANGE_RATE endpoint needs an explicit quote currency.
    private static final String CRYPTO_QUOTE_CURRENCY = "USD";

    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AlphaVantageClient alphaVantageClient;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final PortfolioService portfolioService;

    public MarketDataService(TickerRepository tickerRepository,
                              PriceHistoryRepository priceHistoryRepository,
                              AlphaVantageClient alphaVantageClient,
                              PriceWebSocketHandler priceWebSocketHandler,
                              ObjectMapper objectMapper,
                              AlertService alertService,
                              PortfolioService portfolioService) {
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.alphaVantageClient = alphaVantageClient;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
        this.portfolioService = portfolioService;
    }

    public void pollAndStore(String symbol, String name, String exchange) {
        pollAndStore(symbol, name, exchange, AssetType.STOCK);
    }

    public void pollAndStore(String symbol, String name, String exchange, AssetType assetType) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseGet(() -> tickerRepository.save(new Ticker(symbol, name, exchange, assetType)));

        BigDecimal price = ticker.getAssetType() == AssetType.CRYPTO
                ? fetchCryptoPrice(symbol)
                : fetchStockPrice(symbol);

        if (price == null) {
            return; // already logged by the fetch method
        }

        Instant recordedAt = Instant.now();
        PriceHistory priceHistory = new PriceHistory(ticker, price, recordedAt);
        priceHistoryRepository.save(priceHistory);

        log.info("Saved price for {}: {}", symbol, price);

        broadcastUpdate(symbol, price, recordedAt);
        alertService.checkAlertsForTicker(ticker, price);
        portfolioService.checkPendingOrders(ticker, price);
    }

    private BigDecimal fetchStockPrice(String symbol) {
        GlobalQuoteResponse response = alphaVantageClient.fetchQuote(symbol);
        GlobalQuote quote = response.getGlobalQuote();

        if (quote == null || quote.getPrice() == null || quote.getPrice().isBlank()) {
            log.warn("No quote data returned for {}", symbol);
            return null;
        }

        try {
            return new BigDecimal(quote.getPrice());
        } catch (NumberFormatException e) {
            log.warn("Malformed price '{}' returned for {}", quote.getPrice(), symbol);
            return null;
        }
    }

    private BigDecimal fetchCryptoPrice(String symbol) {
        CurrencyExchangeRateResponse response = alphaVantageClient.fetchCryptoQuote(symbol, CRYPTO_QUOTE_CURRENCY);
        CurrencyExchangeRate rate = response.getExchangeRate();

        if (rate == null || rate.getExchangeRate() == null || rate.getExchangeRate().isBlank()) {
            log.warn("No exchange rate data returned for {}", symbol);
            return null;
        }

        try {
            return new BigDecimal(rate.getExchangeRate());
        } catch (NumberFormatException e) {
            log.warn("Malformed exchange rate '{}' returned for {}", rate.getExchangeRate(), symbol);
            return null;
        }
    }

    private void broadcastUpdate(String symbol, BigDecimal price, Instant recordedAt) {
        try {
            PriceUpdateMessage message = new PriceUpdateMessage("PRICE_UPDATE", symbol, price, recordedAt);
            String json = objectMapper.writeValueAsString(message);
            priceWebSocketHandler.broadcast(json);
        } catch (Exception e) {
            log.warn("Failed to broadcast price update for {}", symbol, e);
        }
    }

    public List<TickerResponse> getAllTickers() {
        return tickerRepository.findAll().stream()
                .map(t -> new TickerResponse(t.getSymbol(), t.getName(), t.getExchange(), t.getAssetType()))
                .toList();
    }

    // Real search against Alpha Vantage — lets a user find any actual stock
    // by company name or partial ticker (e.g. "apple" or "AAP") and get back
    // real matching symbols with their actual names — this is what makes
    // "add any stock" possible instead of a fixed list. Alpha Vantage's
    // SYMBOL_SEARCH only covers stocks/forex, so crypto tickers are added by
    // symbol directly instead of through search.
    public List<TickerSearchResult> searchTickers(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<SymbolMatch> matches = alphaVantageClient.searchSymbols(query).getBestMatches();
        if (matches == null) return Collections.emptyList();

        return matches.stream()
                .map(m -> new TickerSearchResult(m.getSymbol(), m.getName(), m.getRegion()))
                .toList();
    }

    // Adds a new ticker to the tracked list and polls it once immediately,
    // so the user sees a real price right away instead of waiting up to a
    // full scheduler cycle for the first data point.
    public TickerResponse addTicker(AddTickerRequest request) {
        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new InvalidRequestException("Symbol is required");
        }

        String symbol = request.symbol().toUpperCase();
        AssetType assetType = request.assetType() != null ? request.assetType() : AssetType.STOCK;
        boolean alreadyTracked = tickerRepository.findBySymbol(symbol).isPresent();

        pollAndStore(symbol, request.name(), request.exchange(), assetType);

        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InvalidRequestException("Could not find price data for " + symbol));

        log.info("{} ticker: {}", alreadyTracked ? "Re-polled existing" : "Added new", symbol);
        return new TickerResponse(ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType());
    }

    public List<PricePointResponse> getPriceHistory(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new TickerNotFoundException(symbol));

        return priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId()).stream()
                .map(p -> new PricePointResponse(p.getPrice(), p.getRecordedAt()))
                .toList();
    }
}
