package com.meridian.backend.controller;

import com.meridian.backend.dto.AddWatchlistRequest;
import com.meridian.backend.dto.WatchlistItemResponse;
import com.meridian.backend.model.User;
import com.meridian.backend.service.WatchlistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemResponse> getWatchlist(@AuthenticationPrincipal User user) {
        return watchlistService.getWatchlist(user);
    }

    @PostMapping
    public WatchlistItemResponse addToWatchlist(@RequestBody AddWatchlistRequest request, @AuthenticationPrincipal User user) {
        return watchlistService.addToWatchlist(request.symbol(), user);
    }

    @DeleteMapping("/{symbol}")
    public void removeFromWatchlist(@PathVariable String symbol, @AuthenticationPrincipal User user) {
        watchlistService.removeFromWatchlist(symbol, user);
    }
}
