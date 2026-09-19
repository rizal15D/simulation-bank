package com.example.ledgerbank.transaction;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.OwnershipPolicy;
import com.example.ledgerbank.common.Money;
import com.example.ledgerbank.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class DepositService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public DepositService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public TransactionResponse deposit(BankingPrincipal actor, UUID accountId, BigDecimal requestedAmount) {
        return depositInternal(actor, accountId, requestedAmount);
    }

    @Transactional
    TransactionResponse deposit(UUID accountId, BigDecimal requestedAmount) {
        return depositInternal(null, accountId, requestedAmount);
    }

    private TransactionResponse depositInternal(BankingPrincipal actor, UUID accountId, BigDecimal requestedAmount) {
        BigDecimal amount = Money.requireValid(requestedAmount);
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account was not found", HttpStatus.NOT_FOUND));
        if (actor != null) {
            OwnershipPolicy.requireOwnedCustomer(actor, account.getCustomerId());
        }
        account.requireActive();
        BigDecimal before = account.getBalance();
        account.credit(amount);
        AccountTransaction transaction = transactions.saveAndFlush(new AccountTransaction(account, null,
                TransactionType.DEPOSIT, amount, before, account.getBalance()));
        return TransactionResponse.from(transaction);
    }
}
