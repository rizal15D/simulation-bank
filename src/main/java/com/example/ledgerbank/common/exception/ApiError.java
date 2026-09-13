package com.example.ledgerbank.common.exception;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {
    public ApiError(String code, String message) {
        this(code, message, Instant.now());
    }
}
