package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class WatchlistItemNotFoundException extends RuntimeException {
    public WatchlistItemNotFoundException(String symbol) {
        super(symbol + " is not in your watchlist");
    }
}
