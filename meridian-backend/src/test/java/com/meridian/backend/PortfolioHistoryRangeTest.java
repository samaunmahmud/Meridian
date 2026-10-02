package com.meridian.backend;

import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.PortfolioSnapshot;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioHistoryRangeTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;
    @Autowired PortfolioSnapshotRepository snapshotRepository;

    @Test
    void aRangeKeepsOnlyTheSnapshotsInsideIt() {
        User user = newUser("1000.00");
        Instant now = Instant.now();
        for (int days : new int[]{40, 10, 3, 0}) {
            snapshotRepository.save(new PortfolioSnapshot(portfolioOf(user), new BigDecimal(1000 + days), now.minus(Duration.ofDays(days))));
        }

        List<PortfolioSnapshotResponse> week = portfolioService.getPortfolioHistory(user, null, "1W");
        List<PortfolioSnapshotResponse> month = portfolioService.getPortfolioHistory(user, null, "1m");
        List<PortfolioSnapshotResponse> all = portfolioService.getPortfolioHistory(user, null, "ALL");

        assertThat(week).extracting(PortfolioSnapshotResponse::totalValue).map(BigDecimal::intValue).containsExactly(1003, 1000);
        assertThat(month).hasSize(3);
        assertThat(all).hasSize(4);
        assertThat(portfolioService.getPortfolioHistory(user, null, null)).hasSize(4);
        assertThatThrownBy(() -> portfolioService.getPortfolioHistory(user, null, "2D")).isInstanceOf(InvalidRequestException.class);
    }
}
