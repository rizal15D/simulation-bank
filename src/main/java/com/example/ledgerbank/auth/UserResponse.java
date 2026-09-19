package com.example.ledgerbank.auth;

import java.util.UUID;

public record UserResponse(UUID userId, UUID customerId, String email, UserRole role) {
    static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getCustomerId(), user.getEmail(), user.getRole());
    }
}
