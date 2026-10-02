package com.meridian.backend.service;

import com.meridian.backend.dto.WatchlistItemResponse;
import com.meridian.backend.dto.WatchlistNoteRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    // Reads each item's ticker, so it needs a session outside a web request too.
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlist(User user) {
        return watchlistItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public static final int MAX_NOTE_LENGTH = 500;
    private static final BigDecimal MAX_TARGET_PRICE = new BigDecimal("99999999");

    /**
     * Saves your note and target price on a stock, adding it to your watchlist if it isn't there yet.
     * Blank text or a null price clears that part.
     */
    @Transactional
    public WatchlistItemResponse saveNote(String symbol, WatchlistNoteRequest request, User user) {
        Ticker ticker = tickerRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new TickerNotFoundException(symbol));
        String note = request == null || request.note() == null || request.note().isBlank() ? null : request.note().strip();
        BigDecimal target = request == null ? null : request.targetPrice();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidRequestException("Notes can be at most " + MAX_NOTE_LENGTH + " characters");
        }
        if (target != null && (target.signum() <= 0 || target.compareTo(MAX_TARGET_PRICE) > 0
                || target.stripTrailingZeros().scale() > 4)) {
            throw new InvalidRequestException("The target price must be above 0, with at most 4 decimals");
        }
        WatchlistItem item = watchlistItemRepository.findByUserIdAndTickerId(user.getId(), ticker.getId())
                .orElseGet(() -> new WatchlistItem(user, ticker));
        item.setNote(note);
        item.setTargetPrice(target);
        return toResponse(watchlistItemRepository.save(item));
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
                currentPrice, item.getCreatedAt(), item.getNote(), item.getTargetPrice()
        );
    }
}
