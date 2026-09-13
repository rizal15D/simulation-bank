package com.example.ledgerbank.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<AccountTransaction, UUID> {
    Page<AccountTransaction> findByAccountId(UUID accountId, Pageable pageable);

    long countByTransferId(UUID transferId);
}
