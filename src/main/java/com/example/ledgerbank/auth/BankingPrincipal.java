package com.example.ledgerbank.auth;

import java.util.UUID;

public record BankingPrincipal(UUID userId, UUID customerId, String email, UserRole role) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
