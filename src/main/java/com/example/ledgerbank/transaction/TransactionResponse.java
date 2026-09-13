package com.example.ledgerbank.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, UUID accountId, UUID transferId, TransactionType transactionType,
                                  BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                  Instant createdAt) {
    public static TransactionResponse from(AccountTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getAccountId(), transaction.getTransferId(),
                transaction.getTransactionType(), transaction.getAmount(), transaction.getBalanceBefore(),
                transaction.getBalanceAfter(), transaction.getCreatedAt());
    }
}
