package com.meridian.backend;

import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.dto.ConvertResponse;
import com.meridian.backend.dto.WalletResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.User;
import com.meridian.backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest extends IntegrationTestBase {

    @Autowired WalletService walletService;

    private BigDecimal balance(User user, SupportedCurrency currency) {
        return walletService.getWallets(user).stream()
                .filter(w -> w.currency() == currency).findFirst().map(WalletResponse::balance).orElseThrow();
    }

    @Test
    void convertAppliesHalfPercentSpreadAndMovesBothBalances() {
        setFxRate(SupportedCurrency.EUR, "1.1000");
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("100.00"), user);

        ConvertResponse result = walletService.convert(
                new ConvertRequest(SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("100.00")), user);

        // 100 EUR * 1.10 = 110.00 mid-market; minus 0.5% spread = 109.45
        assertThat(result.amountCredited()).isEqualByComparingTo("109.45");
        assertThat(result.fee()).isEqualByComparingTo("0.55");
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("0.00");
        assertThat(balance(user, SupportedCurrency.USD)).isEqualByComparingTo("109.45");
    }

    @Test
    void cannotConvertMoreThanTheBalance() {
        setFxRate(SupportedCurrency.EUR, "1.1000");
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("10.00"), user);

        assertThatThrownBy(() -> walletService.convert(
                new ConvertRequest(SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("10.01")), user))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("10.00");
    }

    @Test
    void concurrentConversionsCannotSpendTheSameBalanceTwice() throws Exception {
        setFxRate(SupportedCurrency.EUR, "1.1000");
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("100.00"), user);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    walletService.convert(new ConvertRequest(SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("50.00")), user);
                    succeeded.incrementAndGet();
                } catch (Exception expected) {
                    // out of EUR
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(succeeded.get()).isEqualTo(2); // 100 EUR / 50 EUR
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("0.00");
        assertThat(balance(user, SupportedCurrency.USD)).isEqualByComparingTo("109.45"); // 2 x 54.725 -> each credit rounds to 54.7250
    }
}
