package com.meridian.backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// A flat trading commission, applied on top of every buy/sell notional —
// this is what makes execution feel like a real brokerage instead of
// frictionless paper trading.
@Service
public class FeeService {

    // Markup on every currency conversion (manual converts and trades settled
    // from a non-USD wallet alike) — how FX revenue works in an app like this.
    public static final BigDecimal FX_SPREAD = new BigDecimal("0.005");

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.0025"); // 0.25%
    private static final BigDecimal MINIMUM_FEE = new BigDecimal("1.00");

    public BigDecimal commissionFor(BigDecimal notional) {
        BigDecimal fee = notional.multiply(COMMISSION_RATE).setScale(4, RoundingMode.HALF_UP);
        return fee.max(MINIMUM_FEE);
    }
}
