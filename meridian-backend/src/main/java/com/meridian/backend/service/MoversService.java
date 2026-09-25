package com.meridian.backend.service;

import com.meridian.backend.dto.MoverResponse;
import com.meridian.backend.dto.MoversResponse;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Top movers: every tracked ticker's change over its trading day, biggest rises and falls first.
 * A stock is measured from the close of the session before its latest price's session (so over a weekend it
 * shows Friday's move, as a broker does); crypto, which never closes, from 24 hours before its latest price.
 * A ticker with no price that old yet (just added) has no change to show and is left out.
 */
@Service
public class MoversService {

    static final int MAX_LIMIT = 20;
    private static final Duration CRYPTO_WINDOW = Duration.ofHours(24);

    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketCalendar marketCalendar;

    public MoversService(TickerRepository tickerRepository, PriceHistoryRepository priceHistoryRepository,
                         MarketCalendar marketCalendar) {
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.marketCalendar = marketCalendar;
    }

    @Transactional(readOnly = true)
    public MoversResponse movers(int limit) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<MoverResponse> moves = changes();
        List<MoverResponse> gainers = moves.stream()
                .filter(m -> m.change().signum() > 0)
                .sorted(Comparator.comparing(MoverResponse::changePercent).reversed())
                .limit(n).toList();
        List<MoverResponse> losers = moves.stream()
                .filter(m -> m.change().signum() < 0)
                .sorted(Comparator.comparing(MoverResponse::changePercent))
                .limit(n).toList();
        return new MoversResponse(gainers, losers);
    }

    /** Every tracked ticker's move today, unsorted, including those that did not move (the watchlist shows them all). */
    @Transactional(readOnly = true)
    public List<MoverResponse> changes() {
        List<MoverResponse> moves = new ArrayList<>();
        for (Ticker ticker : tickerRepository.findAll()) {
            move(ticker).ifPresent(moves::add);
        }
        return moves;
    }

    private Optional<MoverResponse> move(Ticker ticker) {
        Optional<PriceHistory> latest = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId());
        if (latest.isEmpty()) return Optional.empty();
        Instant from = referenceMoment(ticker.getAssetType(), latest.get().getRecordedAt());
        return priceHistoryRepository
                .findFirstByTickerIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(ticker.getId(), from)
                .filter(reference -> reference.getPrice().signum() > 0)
                .map(reference -> toResponse(ticker, latest.get(), reference));
    }

    private Instant referenceMoment(AssetType type, Instant latestAt) {
        Instant previousClose = type == AssetType.STOCK ? marketCalendar.previousStockClose(latestAt) : null;
        return previousClose != null ? previousClose : latestAt.minus(CRYPTO_WINDOW);
    }

    private static MoverResponse toResponse(Ticker ticker, PriceHistory latest, PriceHistory reference) {
        BigDecimal change = latest.getPrice().subtract(reference.getPrice());
        BigDecimal percent = change.multiply(BigDecimal.valueOf(100))
                .divide(reference.getPrice(), 2, RoundingMode.HALF_UP);
        return new MoverResponse(ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType(),
                latest.getPrice(), reference.getPrice(), change, percent,
                latest.getRecordedAt(), reference.getRecordedAt());
    }
}
