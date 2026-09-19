package com.example.ledgerbank.auth;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=[REDACTED], tokenType=" + tokenType
                + ", expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}
