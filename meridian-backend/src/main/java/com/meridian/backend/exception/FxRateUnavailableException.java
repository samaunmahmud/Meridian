package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FxRateUnavailableException extends RuntimeException {
    public FxRateUnavailableException(String currency) {
        super("No exchange rate available yet for: " + currency);
    }
}
