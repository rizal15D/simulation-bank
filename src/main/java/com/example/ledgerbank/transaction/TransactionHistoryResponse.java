package com.example.ledgerbank.transaction;

import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionHistoryResponse(List<TransactionResponse> content, int page, int size,
                                         long totalElements, int totalPages) {
    public static TransactionHistoryResponse from(Page<AccountTransaction> results) {
        return new TransactionHistoryResponse(results.getContent().stream().map(TransactionResponse::from).toList(),
                results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }
}
