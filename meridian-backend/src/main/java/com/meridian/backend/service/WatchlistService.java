package com.meridian.backend.service;

import com.meridian.backend.dto.WatchlistItemResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.exception.WatchlistItemNotFoundException;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.model.WatchlistItem;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public WatchlistService(WatchlistItemRepository watchlistItemRepository,
                             TickerRepository tickerRepository,
                             PriceHistoryRepository priceHistoryRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    // A ticker must already be tracked (via /api/tickers or /api/tickers/search
    // + add) before it can be watchlisted — this keeps price polling and the
    // watchlist backed by the exact same tracked-ticker list.
    public WatchlistItemResponse addToWatchlist(String symbol, User user) {
        if (symbol == null || symbol.isBlank()) {
            throw new InvalidRequestException("Symbol is required");
        }

        Ticker ticker = tickerRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));

        if (watchlistItemRepository.existsByUserIdAndTickerId(user.getId(), ticker.getId())) {
            throw new InvalidRequestException(ticker.getSymbol() + " is already in your watchlist");
        }

        WatchlistItem item = watchlistItemRepository.save(new WatchlistItem(user, ticker));
        return toResponse(item);
    }

    public List<WatchlistItemResponse> getWatchlist(User user) {
        return watchlistItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public void removeFromWatchlist(String symbol, User user) {
        Ticker ticker = tickerRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));

        WatchlistItem item = watchlistItemRepository.findByUserIdAndTickerId(user.getId(), ticker.getId())
                .orElseThrow(() -> new WatchlistItemNotFoundException(symbol));

        watchlistItemRepository.delete(item);
    }

    private WatchlistItemResponse toResponse(WatchlistItem item) {
        Ticker ticker = item.getTicker();
        var currentPrice = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getPrice)
                .orElse(null);

        return new WatchlistItemResponse(
                ticker.getSymbol(), ticker.getName(), ticker.getExchange(), ticker.getAssetType(),
                currentPrice, item.getCreatedAt()
        );
    }
}
