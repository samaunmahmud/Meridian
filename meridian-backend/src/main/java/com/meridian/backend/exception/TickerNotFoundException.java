package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TickerNotFoundException extends RuntimeException {
    public TickerNotFoundException(String symbol) {
        super("Unknown ticker: " + symbol);
    }
}
