package com.meridian.backend.scheduler;

import com.meridian.backend.service.RecurringOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringOrderScheduler.class);

    private final RecurringOrderService recurringOrderService;

    public RecurringOrderScheduler(RecurringOrderService recurringOrderService) {
        this.recurringOrderService = recurringOrderService;
    }

    @Scheduled(fixedRate = 300000, initialDelay = 15000)
    public void runDueOrders() {
        try {
            recurringOrderService.runDue();
        } catch (Exception e) {
            log.warn("Scheduled recurring-order run failed", e);
        }
    }
}
