package com.meridian.backend.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.PortfolioSnapshot;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@ConditionalOnProperty(prefix = "meridian.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class PortfolioSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotScheduler.class);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioService portfolioService;

    public PortfolioSnapshotScheduler(PortfolioRepository portfolioRepository,
                                       PortfolioSnapshotRepository snapshotRepository,
                                       PortfolioService portfolioService) {
        this.portfolioRepository = portfolioRepository;
        this.snapshotRepository = snapshotRepository;
        this.portfolioService = portfolioService;
    }

    // Every 60 seconds, record what EVERY user's portfolio is actually worth
    // right now. This is what turns "total value" from a single live number
    // into a real chart over time — no interpolation, no fake data, just a
    // periodic real measurement, the same idea as the price-history table.
    @Scheduled(fixedRate = 60000)
    public void recordSnapshots() {
        Instant now = Instant.now();
        for (Portfolio portfolio : portfolioRepository.findAll()) {
            try {
                PortfolioResponse valuation = portfolioService.getPortfolioValuation(portfolio.getUser());
                snapshotRepository.save(new PortfolioSnapshot(portfolio, valuation.totalValue(), now));
            } catch (Exception e) {
                // One user's bad data (e.g. a ticker with no price yet) shouldn't
                // stop every other user's snapshot from being recorded.
                log.warn("Failed to snapshot portfolio {}", portfolio.getId(), e);
            }
        }
    }
}
