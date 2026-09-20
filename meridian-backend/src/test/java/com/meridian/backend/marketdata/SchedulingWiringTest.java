package com.meridian.backend.marketdata;

import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.scheduler.FxRatePollingScheduler;
import com.meridian.backend.scheduler.PricePollingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Starts the app with the background jobs ENABLED (the other tests disable
// them). The FX job reads its interval from a Spring expression referencing
// MarketDataProperties; if that were wrong, startup would fail here rather
// than in production. The provider is mocked so nothing touches the network.
@SpringBootTest(properties = "meridian.scheduling.enabled=true")
@ActiveProfiles("test")
class SchedulingWiringTest {

    @MockBean MarketDataProvider provider;
    @Autowired FxRatePollingScheduler fxScheduler;
    @Autowired PricePollingScheduler priceScheduler;
    @Autowired ThreadPoolTaskScheduler taskScheduler;

    @Test
    void backgroundJobsStartWithTheConfiguredIntervals() {
        assertThat(fxScheduler).isNotNull();
        assertThat(priceScheduler).isNotNull();
    }

    @Test
    void scheduledJobsDoNotShareASingleThread() {
        // With Spring's default of one thread, a price poll stuck on a slow provider would hold up
        // the snapshot, recurring-order and cleanup jobs as well.
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThanOrEqualTo(2);
    }
}
