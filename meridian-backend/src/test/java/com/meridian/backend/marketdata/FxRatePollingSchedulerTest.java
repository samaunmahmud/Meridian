package com.meridian.backend.marketdata;

import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.scheduler.FxRatePollingScheduler;
import com.meridian.backend.service.FxRateService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FxRatePollingSchedulerTest {

    @Test
    void whenTheProviderIsDownItDoesNotAskAgainForTheNextCurrency() {
        FxRateService service = mock(FxRateService.class);
        doThrow(new MarketDataUnreachableException("down", new RuntimeException("HTTP 500")))
                .when(service).pollAndStore(SupportedCurrency.EUR);

        new FxRatePollingScheduler(service).pollRates();

        verify(service).pollAndStore(SupportedCurrency.EUR);
        verify(service, never()).pollAndStore(SupportedCurrency.GBP);
    }

    @Test
    void anUnexpectedFailureForOneCurrencyStillLetsTheOtherBeRefreshed() {
        FxRateService service = mock(FxRateService.class);
        doThrow(new IllegalStateException("bad row")).when(service).pollAndStore(SupportedCurrency.EUR);

        new FxRatePollingScheduler(service).pollRates();

        verify(service).pollAndStore(SupportedCurrency.GBP);
    }
}
