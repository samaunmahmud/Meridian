package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The price provider was asked and did not give a usable answer: it timed out, could
// not be reached, returned a server error, or sent something that is not a price
// response. (MarketDataUnavailableException is different: there we did not ask at all.)
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MarketDataUnreachableException extends RuntimeException {
    public MarketDataUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
