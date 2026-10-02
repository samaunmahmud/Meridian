package com.meridian.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** The whole set of targets, replacing the old one. An empty list clears them. */
public record TargetsRequest(List<Target> targets) {
    public record Target(String symbol, BigDecimal percent) {
    }
}
