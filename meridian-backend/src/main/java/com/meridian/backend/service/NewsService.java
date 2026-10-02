package com.meridian.backend.service;

import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.NewsArticleResponse;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recent news for a tracked ticker. News costs provider requests from the same allowance as prices, so each
 * ticker's articles are kept for a while (marketdata.news-cache-minutes) and only asked for when someone looks.
 * If the provider can't be asked, the last articles are shown again rather than nothing.
 */
@Service
public class NewsService {

    public static final int MAX_ARTICLES = 10;
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private record Cached(List<NewsArticleResponse> articles, Instant fetchedAt) {
    }

    private final MarketDataProvider provider;
    private final TickerRepository tickerRepository;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public NewsService(MarketDataProvider provider, TickerRepository tickerRepository,
                       MarketDataProperties properties, Clock clock) {
        this.provider = provider;
        this.tickerRepository = tickerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Newest first, at most {@value #MAX_ARTICLES}. Only tracked tickers, so nobody can spend the allowance on random symbols. */
    public List<NewsArticleResponse> getNews(String symbol) {
        Ticker ticker = tickerRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));
        String key = ticker.getSymbol();

        Cached cached = cache.get(key);
        if (fresh(cached)) return cached.articles();
        // One request per ticker at a time: people opening the same stock together share the answer.
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            cached = cache.get(key);
            if (fresh(cached)) return cached.articles();
            try {
                List<NewsArticleResponse> articles = tidy(provider.fetchNews(key, ticker.getAssetType() == AssetType.CRYPTO));
                cache.put(key, new Cached(articles, clock.instant()));
                return articles;
            } catch (MarketDataUnavailableException | MarketDataUnreachableException e) {
                if (cached == null) throw e;
                log.info("Showing older news for {}: {}", key, e.getMessage());
                return cached.articles();
            }
        }
    }

    private boolean fresh(Cached cached) {
        return cached != null && clock.instant().isBefore(cached.fetchedAt().plus(properties.getNewsCacheTtl()));
    }

    // Newest first (undated last), the same story only once (by link or headline), at most MAX_ARTICLES.
    private static List<NewsArticleResponse> tidy(List<NewsArticleResponse> articles) {
        List<NewsArticleResponse> sorted = new ArrayList<>(articles == null ? List.of() : articles);
        sorted.sort(Comparator.comparing(NewsArticleResponse::publishedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())).reversed());
        Set<String> seen = new HashSet<>();
        List<NewsArticleResponse> picked = new ArrayList<>();
        for (NewsArticleResponse a : sorted) {
            if (picked.size() >= MAX_ARTICLES) break;
            boolean newLink = seen.add(a.url());
            boolean newHeadline = seen.add("headline:" + a.headline().toLowerCase());
            if (!newLink || !newHeadline) continue;
            picked.add(a);
        }
        return List.copyOf(picked);
    }
}
