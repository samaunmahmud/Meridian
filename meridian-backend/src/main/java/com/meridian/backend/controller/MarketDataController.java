package com.meridian.backend.controller;

import com.meridian.backend.dto.AddTickerRequest;
import com.meridian.backend.dto.NewsArticleResponse;
import com.meridian.backend.dto.PricePointResponse;
import com.meridian.backend.dto.TickerResponse;
import com.meridian.backend.dto.TickerSearchResult;
import com.meridian.backend.service.MarketDataService;
import com.meridian.backend.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final NewsService newsService;

    public MarketDataController(MarketDataService marketDataService, NewsService newsService) {
        this.marketDataService = marketDataService;
        this.newsService = newsService;
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
    public List<PricePointResponse> getPrices(@PathVariable String symbol,
                                              @RequestParam(required = false) String range,
                                              @RequestParam(required = false) Integer points,
                                              @RequestParam(required = false) Integer limit) {
        return marketDataService.getPriceHistory(symbol, range, points, limit);
    }

    @GetMapping("/news/{symbol}")
    public List<NewsArticleResponse> getNews(@PathVariable String symbol) {
        return newsService.getNews(symbol);
    }
}
