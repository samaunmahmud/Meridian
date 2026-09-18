package com.meridian.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.CurrencyExchangeRate;
import com.meridian.backend.client.CurrencyExchangeRateResponse;
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

    private final AlphaVantageClient alphaVantageClient;
    private final FxRateRepository fxRateRepository;
    private final PriceWebSocketHandler priceWebSocketHandler;
    private final ObjectMapper objectMapper;

    public FxRateService(AlphaVantageClient alphaVantageClient,
                          FxRateRepository fxRateRepository,
                          PriceWebSocketHandler priceWebSocketHandler,
                          ObjectMapper objectMapper) {
        this.alphaVantageClient = alphaVantageClient;
        this.fxRateRepository = fxRateRepository;
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    // Only non-USD currencies against USD are actually polled — enough to
    // price a convert between any two supported currencies via USD as the
    // common leg (see getRate below).
    public void pollAndStore(SupportedCurrency currency) {
        if (currency == SupportedCurrency.USD) return;

        CurrencyExchangeRateResponse response = alphaVantageClient.fetchExchangeRate(currency.name(), SupportedCurrency.USD.name());
        CurrencyExchangeRate rate = response.getExchangeRate();

        if (rate == null || rate.getExchangeRate() == null || rate.getExchangeRate().isBlank()) {
            log.warn("No FX rate returned for {}/USD", currency);
            return;
        }

        BigDecimal parsedRate;
        try {
            parsedRate = new BigDecimal(rate.getExchangeRate());
        } catch (NumberFormatException e) {
            log.warn("Malformed FX rate '{}' for {}/USD", rate.getExchangeRate(), currency);
            return;
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

    private BigDecimal latestRate(SupportedCurrency currency) {
        return fxRateRepository.findByBaseCurrencyAndQuoteCurrency(currency, SupportedCurrency.USD)
                .map(FxRate::getRate)
                .orElseThrow(() -> new FxRateUnavailableException(currency.name()));
    }

    public List<FxRateResponse> getAllRates() {
        return fxRateRepository.findAll().stream()
                .map(r -> new FxRateResponse(r.getBaseCurrency(), r.getQuoteCurrency(), r.getRate(), r.getUpdatedAt()))
                .toList();
    }
}
