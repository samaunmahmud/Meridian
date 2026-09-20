package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Duration;

// A trade needs a price, but the newest one we have is too old to trade on: the price
// feed has been down or delayed. Nothing is wrong with the request itself, so 503.
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class StalePriceException extends RuntimeException {
    public StalePriceException(String what, Duration age) {
        super("The latest " + what + " is " + describe(age) + " old because the price feed is delayed, "
                + "so trading on it is paused until prices refresh.");
    }

    static String describe(Duration age) {
        long minutes = age.toMinutes();
        if (minutes < 90) return minutes + " minutes";
        long hours = Math.round(minutes / 60.0);
        return hours < 48 ? hours + " hours" : (hours / 24) + " days";
    }
}
