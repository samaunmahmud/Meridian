package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.dto.FxRateResponse;
import com.meridian.backend.dto.FxRateUpdateMessage;
import com.meridian.backend.exception.FxRateUnavailableException;
import com.meridian.backend.model.FxRate;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.repository.FxRateRepository;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class FxRateService {

    private static final Logger log = LoggerFactory.getLogger(FxRateService.class);

    private final MarketDataProvider marketDataProvider;
    private final FxRateRepository fxRateRepository;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final PriceFreshness priceFreshness;

    public FxRateService(MarketDataProvider marketDataProvider,
                          FxRateRepository fxRateRepository,
                          PriceWebSocketHandler priceWebSocketHandler,
                          ObjectMapper objectMapper,
                          PriceFreshness priceFreshness) {
        this.marketDataProvider = marketDataProvider;
        this.fxRateRepository = fxRateRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
        this.priceFreshness = priceFreshness;
    }

    // Only non-USD currencies against USD are actually polled — enough to
    // price a convert between any two supported currencies via USD as the
    // common leg (see getRate below).
    public void pollAndStore(SupportedCurrency currency) {
        if (currency == SupportedCurrency.USD) return;

        BigDecimal parsedRate = marketDataProvider.fetchUsdRate(currency);
        if (parsedRate == null) {
            return; // the provider already logged why there is no rate
        }

        Instant updatedAt = Instant.now();
        FxRate fxRate = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(currency, SupportedCurrency.USD)
                .orElseGet(() -> new FxRate(currency, SupportedCurrency.USD, parsedRate, updatedAt));
        fxRate.setRate(parsedRate);
        fxRate.setUpdatedAt(updatedAt);
        fxRateRepository.save(fxRate);

        log.info("Saved FX rate {}/USD: {}", currency, parsedRate);
        broadcastUpdate(currency, SupportedCurrency.USD, parsedRate, updatedAt);
    }

    private void broadcastUpdate(SupportedCurrency base, SupportedCurrency quote, BigDecimal rate, Instant updatedAt) {
        try {
            FxRateUpdateMessage message = new FxRateUpdateMessage("FX_RATE_UPDATE", base, quote, rate, updatedAt);
            String json = objectMapper.writeValueAsString(message);
            priceWebSocketHandler.broadcast(json);
        } catch (Exception e) {
            log.warn("Failed to broadcast FX rate update for {}/{}", base, quote, e);
        }
    }

    // The rate to convert 1 unit of `from` into `to`, always derived via USD
    // as the common leg so only N-1 pairs (not N^2) ever need polling.
    public BigDecimal getRate(SupportedCurrency from, SupportedCurrency to) {
        if (from == to) return BigDecimal.ONE;

        BigDecimal fromToUsd = from == SupportedCurrency.USD ? BigDecimal.ONE : latestRate(from);
        BigDecimal toToUsd = to == SupportedCurrency.USD ? BigDecimal.ONE : latestRate(to);

        return fromToUsd.divide(toToUsd, 8, RoundingMode.HALF_UP);
    }

    // getRate is only used to execute something (a conversion, a trade paid from a foreign
    // wallet), so a rate the feed has stopped refreshing is refused rather than used.
    // The last known rates are still shown as they are by getAllRates.
    private BigDecimal latestRate(SupportedCurrency currency) {
        FxRate fx = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(currency, SupportedCurrency.USD)
                .orElseThrow(() -> new FxRateUnavailableException(currency.name()));
        priceFreshness.requireFreshRate(currency, fx.getUpdatedAt());
        return fx.getRate();
    }

    public List<FxRateResponse> getAllRates() {
        return fxRateRepository.findAll().stream()
                .map(r -> new FxRateResponse(r.getBaseCurrency(), r.getQuoteCurrency(), r.getRate(), r.getUpdatedAt()))
                .toList();
    }
}
