package com.example.ledgerbank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id, UUID customerId, String accountNumber, String currency, BigDecimal balance,
        AccountStatus status, Instant createdAt, Instant updatedAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getCustomerId(), account.getAccountNumber(),
                account.getCurrency(), account.getBalance(), account.getStatus(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
