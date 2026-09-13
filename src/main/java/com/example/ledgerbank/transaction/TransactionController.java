package com.example.ledgerbank.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class TransactionController {
    private final DepositService deposits;
    private final WithdrawalService withdrawals;
    private final TransactionHistoryService history;

    public TransactionController(DepositService deposits, WithdrawalService withdrawals, TransactionHistoryService history) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.history = history;
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@PathVariable UUID accountId, @RequestBody AmountRequest request) {
        return deposits.deposit(accountId, request.amount());
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@PathVariable UUID accountId, @RequestBody AmountRequest request) {
        return withdrawals.withdraw(accountId, request.amount());
    }

    @GetMapping("/transactions")
    public TransactionHistoryResponse history(@PathVariable UUID accountId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return history.history(accountId, page, size);
    }
}
