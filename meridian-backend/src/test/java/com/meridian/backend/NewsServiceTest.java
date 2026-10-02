package com.meridian.backend;

import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.NewsArticleResponse;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.NewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private MarketDataProvider provider;
    private TickerRepository tickers;
    private MutableClock clock;
    private NewsService service;

    private static NewsArticleResponse article(String headline, String url, Instant at) {
        return new NewsArticleResponse(headline, null, "Wire", url, null, at, null);
    }

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        tickers = mock(TickerRepository.class);
        clock = new MutableClock(NOW);
        MarketDataProperties props = new MarketDataProperties(); // Alpha Vantage: news kept for 6 hours
        service = new NewsService(provider, tickers, props, clock);
        when(tickers.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(tickers.findBySymbol("AAPL")).thenReturn(Optional.of(new Ticker("AAPL", "Apple", "NASDAQ")));
        Ticker btc = new Ticker("BTC", "Bitcoin", "CRYPTO");
        btc.setAssetType(AssetType.CRYPTO);
        when(tickers.findBySymbol("BTC")).thenReturn(Optional.of(btc));
    }

    @Test
    void newestFirstWithoutRepeatsAndAtMostTen() {
        List<NewsArticleResponse> raw = new ArrayList<>();
        IntStream.range(0, 12).forEach(i -> raw.add(article("Story " + i, "https://n.example/" + i, NOW.minusSeconds(600L * i))));
        raw.add(article("Story 0", "https://other.example/copy", NOW.plusSeconds(5)));      // same headline
        raw.add(article("Another", "https://n.example/1", NOW.plusSeconds(10)));            // same link
        raw.add(article("Undated", "https://n.example/undated", null));
        when(provider.fetchNews("AAPL", false)).thenReturn(raw);

        List<NewsArticleResponse> news = service.getNews("aapl");

        assertThat(news).hasSize(10);
        assertThat(news.get(0).headline()).isEqualTo("Another");
        assertThat(news.get(1).headline()).isEqualTo("Story 0");
        assertThat(news.get(1).url()).isEqualTo("https://other.example/copy");
        assertThat(news).extracting(NewsArticleResponse::headline).doesNotHaveDuplicates().doesNotContain("Undated", "Story 1");
    }

    @Test
    void articlesAreReusedUntilTheyGoStale() {
        when(provider.fetchNews("AAPL", false)).thenReturn(List.of(article("A", "https://n.example/a", NOW)));

        service.getNews("AAPL");
        clock.advance(Duration.ofHours(5));
        service.getNews("AAPL");
        verify(provider, times(1)).fetchNews("AAPL", false);

        clock.advance(Duration.ofHours(2));
        service.getNews("AAPL");
        verify(provider, times(2)).fetchNews("AAPL", false);
    }

    @Test
    void whenTheProviderCantBeAskedTheLastArticlesAreShownAgain() {
        when(provider.fetchNews("AAPL", false))
                .thenReturn(List.of(article("A", "https://n.example/a", NOW)))
                .thenThrow(new MarketDataUnavailableException("allowance used up"));

        service.getNews("AAPL");
        clock.advance(Duration.ofDays(1));

        assertThat(service.getNews("AAPL")).extracting(NewsArticleResponse::headline).containsExactly("A");
    }

    @Test
    void withNothingToFallBackOnTheProblemIsReported() {
        when(provider.fetchNews("AAPL", false)).thenThrow(new MarketDataUnavailableException("allowance used up"));
        assertThatThrownBy(() -> service.getNews("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void cryptoIsAskedForAsCrypto() {
        when(provider.fetchNews("BTC", true)).thenReturn(List.of());
        assertThat(service.getNews("BTC")).isEmpty();
        verify(provider).fetchNews("BTC", true);
    }

    @Test
    void untrackedSymbolsNeverReachTheProvider() {
        assertThatThrownBy(() -> service.getNews("ZZZZ")).isInstanceOf(TickerNotFoundException.class);
        verify(provider, never()).fetchNews(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
