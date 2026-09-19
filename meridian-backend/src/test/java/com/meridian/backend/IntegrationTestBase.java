package com.meridian.backend;

import com.meridian.backend.model.FxRate;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.FxRateRepository;
import com.meridian.backend.repository.HoldingRepository;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base for tests that run against the real Spring context and an in-memory
 * database. Every test creates its own uniquely-named users and tickers, so
 * tests are independent without needing to clean the database.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    private static final AtomicLong CLOCK = new AtomicLong();

    @Autowired protected UserRepository userRepository;
    @Autowired protected PortfolioRepository portfolioRepository;
    @Autowired protected TickerRepository tickerRepository;
    @Autowired protected PriceHistoryRepository priceHistoryRepository;
    @Autowired protected HoldingRepository holdingRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected FxRateRepository fxRateRepository;

    protected User newUser(String cash) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@test.io", "not-a-real-hash"));
        portfolioRepository.save(new Portfolio(user, new BigDecimal(cash)));
        return user;
    }

    protected Ticker newTicker(String price) {
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        Ticker ticker = tickerRepository.save(new Ticker(symbol, "Test " + symbol, "TEST"));
        setPrice(ticker, price);
        return ticker;
    }

    /** Records a newer price point; the "current price" is always the latest one. */
    protected void setPrice(Ticker ticker, String price) {
        Instant at = Instant.now().plusMillis(CLOCK.incrementAndGet());
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal(price), at));
    }

    protected Portfolio portfolioOf(User user) {
        return portfolioRepository.findByUserId(user.getId()).orElseThrow();
    }

    /** Sets the stored "1 <currency> = <rate> USD" rate, creating it if needed. */
    protected void setFxRate(SupportedCurrency currency, String rate) {
        FxRate fx = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(currency, SupportedCurrency.USD)
                .orElseGet(() -> new FxRate(currency, SupportedCurrency.USD, new BigDecimal(rate), Instant.now()));
        fx.setRate(new BigDecimal(rate));
        fx.setUpdatedAt(Instant.now());
        fxRateRepository.save(fx);
    }
}
