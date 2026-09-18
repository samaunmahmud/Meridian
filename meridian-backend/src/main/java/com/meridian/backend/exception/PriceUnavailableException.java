package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PriceUnavailableException extends RuntimeException {
    public PriceUnavailableException(String symbol) {
        super("No price data available for: " + symbol);
    }
}
