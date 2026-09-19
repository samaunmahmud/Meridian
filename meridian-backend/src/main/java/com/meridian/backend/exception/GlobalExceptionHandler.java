package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// @RestControllerAdvice intercepts exceptions thrown by ANY controller in the
// app, in one central place, instead of every controller handling its own
// errors. This is what actually puts the exception's message into the JSON
// body — without this, Spring Boot's default error handling silently drops
// it, which is exactly the bug this class fixes.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            TickerNotFoundException.class,
            InsufficientFundsException.class,
            InsufficientSharesException.class,
            PriceUnavailableException.class,
            EmailAlreadyExistsException.class,
            InvalidCredentialsException.class,
            InvalidRequestException.class,
            OrderNotFoundException.class,
            InvalidOrderStateException.class,
            WatchlistItemNotFoundException.class,
            AlertNotFoundException.class,
            FxRateUnavailableException.class,
            RecurringOrderNotFoundException.class,
            MarketDataUnavailableException.class
    })
    public ResponseEntity<Map<String, String>> handleKnownException(RuntimeException ex) {
        // Reads the @ResponseStatus annotation already on each exception class,
        // so the status code stays defined in ONE place (the exception itself),
        // not duplicated here.
        ResponseStatus statusAnnotation = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = statusAnnotation != null ? statusAnnotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("message", ex.getMessage()));
    }

    // Catch-all for anything unexpected — never leak internal exception
    // details (stack traces, class names) to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Something went wrong. Please try again."));
    }
}
