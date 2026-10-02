package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.client.AlphaVantageClient;
import com.meridian.backend.client.AlphaVantageProvider;
import com.meridian.backend.client.FinnhubProvider;
import com.meridian.backend.client.RequestBudget;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.dto.NewsArticleResponse;
import com.meridian.backend.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewsProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private MockRestServiceServer server;

    private AlphaVantageProvider alphaVantage() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        return new AlphaVantageProvider(new AlphaVantageClient(builder, "test-key"), new RequestBudget(props, new MutableClock(NOW)));
    }

    private FinnhubProvider finnhub() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketDataProperties props = new MarketDataProperties();
        props.setProvider("finnhub");
        MutableClock clock = new MutableClock(NOW);
        return new FinnhubProvider(builder, new RequestBudget(props, clock), "secret-key", "https://finnhub.io/api/v1", clock);
    }

    @Test
    void alphaVantageReadsTheFeedAndThisTickersSentiment() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(allOf(containsString("function=NEWS_SENTIMENT"), containsString("tickers=AAPL"), containsString("sort=LATEST"))))
                .andRespond(withSuccess("""
                        {"items":"2","feed":[
                          {"title":"Apple beats estimates","url":"https://news.example/apple","time_published":"20261001T143000",
                           "summary":"Revenue rose.","source":"Example Wire","banner_image":"https://img.example/a.jpg",
                           "ticker_sentiment":[{"ticker":"MSFT","ticker_sentiment_label":"Bearish"},
                                               {"ticker":"AAPL","ticker_sentiment_label":"Somewhat-Bullish"}]},
                          {"title":"Chip supply update","url":"https://news.example/chips","time_published":"bad",
                           "source":"Example Wire","banner_image":"http://img.example/plain.jpg","ticker_sentiment":[]}
                        ]}""", MediaType.APPLICATION_JSON));

        List<NewsArticleResponse> news = provider.fetchNews("AAPL", false);

        assertThat(news).hasSize(2);
        NewsArticleResponse first = news.get(0);
        assertThat(first.headline()).isEqualTo("Apple beats estimates");
        assertThat(first.summary()).isEqualTo("Revenue rose.");
        assertThat(first.source()).isEqualTo("Example Wire");
        assertThat(first.url()).isEqualTo("https://news.example/apple");
        assertThat(first.imageUrl()).isEqualTo("https://img.example/a.jpg");
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-10-01T14:30:00Z"));
        assertThat(first.sentiment()).isEqualTo("Bullish");
        NewsArticleResponse second = news.get(1);
        assertThat(second.publishedAt()).isNull();
        assertThat(second.imageUrl()).isNull(); // http images would be blocked on an https site
        assertThat(second.sentiment()).isNull();
    }

    @Test
    void cryptoNewsIsAskedForWithTheCryptoPrefix() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(containsString("tickers=CRYPTO:BTC"))).andRespond(withSuccess("""
                {"feed":[{"title":"Bitcoin rallies","url":"https://news.example/btc","time_published":"20261001T090000",
                  "ticker_sentiment":[{"ticker":"CRYPTO:BTC","ticker_sentiment_label":"Neutral"}]}]}""", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchNews("BTC", true)).singleElement()
                .satisfies(a -> assertThat(a.sentiment()).isEqualTo("Neutral"));
    }

    @Test
    void linksThatAreNotWebAddressesAreDropped() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(containsString("NEWS_SENTIMENT"))).andRespond(withSuccess("""
                {"feed":[{"title":"Script","url":"javascript:alert(1)"},
                         {"title":"Data","url":"data:text/html,hi"},
                         {"title":"Relative","url":"/news/1"},
                         {"title":"  ","url":"https://news.example/blank"},
                         {"title":"Fine","url":"http://news.example/ok","banner_image":"javascript:alert(2)"}]}""", MediaType.APPLICATION_JSON));

        List<NewsArticleResponse> news = provider.fetchNews("AAPL", false);

        assertThat(news).extracting(NewsArticleResponse::headline).containsExactly("Fine");
        assertThat(news.get(0).imageUrl()).isNull();
    }

    @Test
    void alphaVantageRateLimitsAreReportedNotReadAsNoNews() {
        AlphaVantageProvider provider = alphaVantage();
        server.expect(requestTo(containsString("NEWS_SENTIMENT"))).andRespond(withSuccess(
                "{\"Information\":\"Our standard API rate limit is 25 requests per day.\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchNews("AAPL", false)).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void finnhubAsksForTheLastWeekOfCompanyNews() {
        FinnhubProvider provider = finnhub();
        server.expect(requestTo(allOf(containsString("/company-news?symbol=AAPL"), containsString("from=2026-09-25"), containsString("to=2026-10-02"))))
                .andRespond(withSuccess("""
                        [{"category":"company","datetime":1759329000,"headline":"Apple event","id":1,
                          "image":"https://img.example/e.jpg","related":"AAPL","source":"Example","summary":"New phones.",
                          "url":"https://news.example/event"},
                         {"headline":"No link","url":""}]""", MediaType.APPLICATION_JSON));

        List<NewsArticleResponse> news = provider.fetchNews("AAPL", false);

        assertThat(news).singleElement().satisfies(a -> {
            assertThat(a.headline()).isEqualTo("Apple event");
            assertThat(a.publishedAt()).isEqualTo(Instant.ofEpochSecond(1759329000));
            assertThat(a.imageUrl()).isEqualTo("https://img.example/e.jpg");
            assertThat(a.sentiment()).isNull();
        });
    }

    @Test
    void finnhubCryptoUsesTheCryptoCategory() {
        FinnhubProvider provider = finnhub();
        server.expect(requestTo(containsString("/news?category=crypto"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(provider.fetchNews("BTC", true)).isEmpty();
        server.verify();
    }
}
