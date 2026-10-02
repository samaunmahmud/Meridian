package com.meridian.backend;

import com.meridian.backend.dto.BenchmarkResponse;
import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.PortfolioSnapshot;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.service.BenchmarkService;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkTest extends IntegrationTestBase {

    @Autowired BenchmarkService benchmarkService;
    @Autowired PortfolioService portfolioService;
    @Autowired PortfolioSnapshotRepository snapshotRepository;

    private void snapshot(User user, String value, Instant at) {
        snapshotRepository.save(new PortfolioSnapshot(portfolioOf(user), new BigDecimal(value), at));
    }

    private void priceAt(Ticker ticker, String price, Instant at) {
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal(price), at));
    }

    @Test
    void comparesTheTimeWeightedReturnWithTheTickersMove() {
        User user = newUser("1000.00");
        Ticker spy = newTicker("100.00"); // priced "now", inside the window
        Instant now = Instant.now();
        priceAt(spy, "100.00", now.minusSeconds(600));
        priceAt(spy, "120.00", now.plusSeconds(90));

        snapshot(user, "1000.00", now.minusSeconds(300));
        portfolioService.deposit(new BigDecimal("500.00"), user); // not a gain
        snapshot(user, "1600.00", now.plusSeconds(60));            // (1600 - 500) / 1000: +10%
        snapshot(user, "1650.00", now.plusSeconds(120));           // 1.1 * 1650 / 1600

        BenchmarkResponse r = benchmarkService.compare(user, spy.getSymbol().toLowerCase(), "ALL", null);

        assertThat(r.symbol()).isEqualTo(spy.getSymbol());
        assertThat(r.points()).hasSize(3);
        assertThat(r.points().get(0).portfolio()).isEqualByComparingTo("0.00");
        assertThat(r.points().get(1).portfolio()).isEqualByComparingTo("10.00");
        assertThat(r.portfolioReturn()).isEqualByComparingTo("13.44");
        assertThat(r.points().get(1).benchmark()).isEqualByComparingTo("0.00");
        assertThat(r.benchmarkReturn()).isEqualByComparingTo("20.00");
    }

    @Test
    void aShorterRangeIsMeasuredFromItsOwnFirstPoint() {
        User user = newUser("1000.00");
        Ticker spy = newTicker("50.00");
        Instant now = Instant.now();
        priceAt(spy, "40.00", now.minus(Duration.ofDays(9)));
        priceAt(spy, "50.00", now.minus(Duration.ofDays(5)));
        priceAt(spy, "55.00", now.minus(Duration.ofDays(1)));

        snapshot(user, "500.00", now.minus(Duration.ofDays(9)));
        snapshot(user, "1000.00", now.minus(Duration.ofDays(5)));
        snapshot(user, "1100.00", now.minus(Duration.ofDays(1)));

        BenchmarkResponse week = benchmarkService.compare(user, spy.getSymbol(), "1W", null);
        assertThat(week.points()).hasSize(2);
        assertThat(week.portfolioReturn()).isEqualByComparingTo("10.00");
        assertThat(week.benchmarkReturn()).isEqualByComparingTo("10.00");

        BenchmarkResponse all = benchmarkService.compare(user, spy.getSymbol(), null, null);
        assertThat(all.portfolioReturn()).isEqualByComparingTo("120.00");
        assertThat(all.benchmarkReturn()).isEqualByComparingTo("37.50");
    }

    @Test
    void movingMoneyBetweenWalletsIsNotAGainOrALoss() {
        User user = newUser("1000.00");
        setFxRate(SupportedCurrency.EUR, "1.10");
        Ticker spy = newTicker("10.00");
        Instant now = Instant.now();

        snapshot(user, "1000.00", now.minusSeconds(60));
        walletService.convert(new ConvertRequest(SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("400.00")), user);
        snapshot(user, "600.00", now.plusSeconds(60)); // the USD total dropped by what went to EUR

        BenchmarkResponse r = benchmarkService.compare(user, spy.getSymbol(), "ALL", null);
        assertThat(r.portfolioReturn()).isEqualByComparingTo("0.00");
    }

    @Test
    void tooLittleHistoryGivesNoPoints() {
        User user = newUser("1000.00");
        Ticker spy = newTicker("10.00");
        snapshot(user, "1000.00", Instant.now());

        BenchmarkResponse r = benchmarkService.compare(user, spy.getSymbol(), "ALL", null);
        assertThat(r.points()).isEmpty();
        assertThat(r.portfolioReturn()).isNull();
    }

    @Test
    void rejectsUnknownTickersAndBadArguments() {
        User user = newUser("1000.00");
        Ticker spy = newTicker("10.00");
        assertThatThrownBy(() -> benchmarkService.compare(user, "NOPE123", null, null)).isInstanceOf(TickerNotFoundException.class);
        assertThatThrownBy(() -> benchmarkService.compare(user, " ", null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> benchmarkService.compare(user, spy.getSymbol(), "2D", null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> benchmarkService.compare(user, spy.getSymbol(), null, 1)).isInstanceOf(InvalidRequestException.class);
    }
}
