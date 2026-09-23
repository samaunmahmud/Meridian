package com.meridian.backend.controller;

import com.meridian.backend.dto.MoversResponse;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.market.MarketStatus;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.service.MoversService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Whether stocks and crypto can be traded right now (see MarketCalendar), and what moved most today.
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketCalendar calendar;
    private final MoversService moversService;

    public MarketController(MarketCalendar calendar, MoversService moversService) {
        this.calendar = calendar;
        this.moversService = moversService;
    }

    @GetMapping("/status")
    public Map<String, MarketStatus> status() {
        return Map.of("stocks", calendar.status(AssetType.STOCK), "crypto", calendar.status(AssetType.CRYPTO));
    }

    @GetMapping("/movers")
    public MoversResponse movers(@RequestParam(defaultValue = "5") int limit) {
        return moversService.movers(limit);
    }
}
