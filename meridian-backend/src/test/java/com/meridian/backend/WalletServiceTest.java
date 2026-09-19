package com.meridian.backend;

import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.dto.ConvertResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest extends IntegrationTestBase {

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

        int succeeded = runConcurrently(10, () -> walletService.convert(
                new ConvertRequest(SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("50.00")), user));

        assertThat(succeeded).isEqualTo(2); // 100 EUR / 50 EUR
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("0.00");
        assertThat(balance(user, SupportedCurrency.USD)).isEqualByComparingTo("109.45"); // 2 x 54.725 -> each credit rounds to 54.7250
    }
}
