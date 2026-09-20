package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.client.MarketDataProvider;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketDataProvider marketDataProvider;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final PortfolioService portfolioService;
    private final Clock clock;

    // Price history: how much the chart endpoint returns at most when the caller does not say.
    static final int DEFAULT_MAX_POINTS = 2000;
    static final int MAX_POINTS_LIMIT = 5000;

    public MarketDataService(TickerRepository tickerRepository,
                              PriceHistoryRepository priceHistoryRepository,
                              MarketDataProvider marketDataProvider,
                              PriceWebSocketHandler priceWebSocketHandler,
                              ObjectMapper objectMapper,
                              AlertService alertService,
                              PortfolioService portfolioService,
                              Clock clock) {
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketDataProvider = marketDataProvider;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
        this.portfolioService = portfolioService;
        this.clock = clock;
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
            return; // the provider already logged why there is no price
        }

        Instant recordedAt = Instant.now();
        PriceHistory priceHistory = new PriceHistory(ticker, price, recordedAt);
        priceHistoryRepository.save(priceHistory);

        log.info("Saved price for {}: {}", symbol, price);

        broadcastUpdate(symbol, price, recordedAt);
        alertService.checkAlertsForTicker(ticker, price);
        portfolioService.checkPendingOrders(ticker, price);
    }

    // The provider returns null when it has no price for the symbol (it
    // logs why), and throws MarketDataUnavailableException when we can't ask.
    private BigDecimal fetchStockPrice(String symbol) {
        return marketDataProvider.fetchStockPrice(symbol);
    }

    private BigDecimal fetchCryptoPrice(String symbol) {
        return marketDataProvider.fetchCryptoPrice(symbol);
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
        return marketDataProvider.searchSymbols(query);
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

    /**
     * Prices for a chart, newest first.
     *
     * @param range  "1D", "1W", "1M", "3M", "1Y" or "ALL" (also when null): only prices since then
     * @param points at most this many prices, spread evenly over the range (the newest and the oldest
     *               are always kept); null means {@value #DEFAULT_MAX_POINTS}
     * @param limit  just the newest N prices (for "what is it now"); overrides range and points
     */
    public List<PricePointResponse> getPriceHistory(String symbol, String range, Integer points, Integer limit) {
        Ticker ticker = tickerRepository.findBySymbol(symbol)
                .orElseThrow(() -> new TickerNotFoundException(symbol));

        if (limit != null) {
            if (limit < 1 || limit > MAX_POINTS_LIMIT) {
                throw new InvalidRequestException("limit must be between 1 and " + MAX_POINTS_LIMIT);
            }
            return priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId(), PageRequest.of(0, limit)).stream()
                    .map(p -> new PricePointResponse(p.getPrice(), p.getRecordedAt()))
                    .toList();
        }

        int maxPoints = points == null ? DEFAULT_MAX_POINTS : points;
        if (maxPoints < 2 || maxPoints > MAX_POINTS_LIMIT) {
            throw new InvalidRequestException("points must be between 2 and " + MAX_POINTS_LIMIT);
        }
        Duration window = windowFor(range);
        List<PriceHistory> rows = window == null
                ? priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId())
                : priceHistoryRepository.findByTickerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                        ticker.getId(), clock.instant().minus(window));

        return downsample(rows, maxPoints).stream()
                .map(p -> new PricePointResponse(p.getPrice(), p.getRecordedAt()))
                .toList();
    }

    private static Duration windowFor(String range) {
        if (range == null || range.isBlank() || range.equalsIgnoreCase("ALL")) return null;
        return switch (range.toUpperCase()) {
            case "1D" -> Duration.ofDays(1);
            case "1W" -> Duration.ofDays(7);
            case "1M" -> Duration.ofDays(30);
            case "3M" -> Duration.ofDays(91);
            case "1Y" -> Duration.ofDays(365);
            default -> throw new InvalidRequestException("range must be one of 1D, 1W, 1M, 3M, 1Y, ALL");
        };
    }

    // Evenly spaced picks (by position), always including the first and the last.
    static <T> List<T> downsample(List<T> rows, int maxPoints) {
        int n = rows.size();
        if (n <= maxPoints) return rows;
        List<T> picked = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            picked.add(rows.get((int) Math.round((double) i * (n - 1) / (maxPoints - 1))));
        }
        return picked;
    }
}
