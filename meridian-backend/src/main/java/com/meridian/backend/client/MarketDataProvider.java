package com.meridian.backend.client;

import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.model.SupportedCurrency;

import java.math.BigDecimal;
import java.util.List;

// Where prices come from. The rest of the app only talks to this interface,
// so switching provider (marketdata.provider=finnhub) needs no other change.
//
// Methods return null when the provider simply has no data for the symbol,
// and throw MarketDataUnavailableException when we can't ask right now
// (budget used up / provider rate limit).
public interface MarketDataProvider {

    BigDecimal fetchStockPrice(String symbol);

    /** Latest price of a crypto asset (e.g. "BTC") in USD. */
    BigDecimal fetchCryptoPrice(String symbol);

    /** How many USD one unit of the currency is worth (e.g. EUR -> 1.09). */
    BigDecimal fetchUsdRate(SupportedCurrency currency);

    List<TickerSearchResult> searchSymbols(String query);
}
