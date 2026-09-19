package com.example.ledgerbank.transaction;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.OwnershipPolicy;
import com.example.ledgerbank.common.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionHistoryService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public TransactionHistoryService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse history(BankingPrincipal actor, UUID accountId, int page, int size) {
        validatePagination(page, size);
        Account account = accounts.findById(accountId).orElseThrow(() ->
                new BusinessException("ACCOUNT_NOT_FOUND", "Account was not found", HttpStatus.NOT_FOUND));
        OwnershipPolicy.requireReadableCustomer(actor, account.getCustomerId());
        return query(accountId, page, size);
    }

    @Transactional(readOnly = true)
    TransactionHistoryResponse history(UUID accountId, int page, int size) {
        validatePagination(page, size);
        if (!accounts.existsById(accountId)) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Account was not found", HttpStatus.NOT_FOUND);
        }
        return query(accountId, page, size);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGINATION", "Page must be non-negative and size must be between 1 and 100",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private TransactionHistoryResponse query(UUID accountId, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return TransactionHistoryResponse.from(transactions.findByAccountId(accountId, request));
    }
}
