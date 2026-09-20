package com.meridian.backend.controller;

import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.market.MarketStatus;
import com.meridian.backend.model.AssetType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Whether stocks and crypto can be traded right now (see MarketCalendar).
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketCalendar calendar;

    public MarketController(MarketCalendar calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/status")
    public Map<String, MarketStatus> status() {
        return Map.of("stocks", calendar.status(AssetType.STOCK), "crypto", calendar.status(AssetType.CRYPTO));
    }
}
