package com.meridian.backend.controller;

import com.meridian.backend.dto.AddTickerRequest;
import com.meridian.backend.dto.PricePointResponse;
import com.meridian.backend.dto.TickerResponse;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.service.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/tickers")
    public List<TickerResponse> getTickers() {
        return marketDataService.getAllTickers();
    }

    @GetMapping("/tickers/search")
    public List<TickerSearchResult> searchTickers(@RequestParam String q) {
        return marketDataService.searchTickers(q);
    }

    @PostMapping("/tickers")
    public TickerResponse addTicker(@RequestBody AddTickerRequest request) {
        return marketDataService.addTicker(request);
    }

    @GetMapping("/prices/{symbol}")
    public List<PricePointResponse> getPrices(@PathVariable String symbol) {
        return marketDataService.getPriceHistory(symbol);
    }
}
