package com.meridian.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecurringOrderNotFoundException extends RuntimeException {
    public RecurringOrderNotFoundException(Long id) {
        super("No recurring order found with id: " + id);
    }
}
