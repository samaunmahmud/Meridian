package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The price provider can't be asked right now: our own daily request budget
// is used up, or the provider told us we hit its rate limit.
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message) {
        super(message);
    }
}
