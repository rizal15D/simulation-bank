package com.example.ledgerbank.transfer;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.common.Money;
import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.transaction.AccountTransaction;
import com.example.ledgerbank.transaction.TransactionRepository;
import com.example.ledgerbank.transaction.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final TransactionRepository transactions;

    public TransferService(AccountRepository accounts, TransferRepository transfers, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.transactions = transactions;
    }

    @Transactional
    public TransferResponse transfer(UUID sourceAccountId, UUID destinationAccountId,
                                     BigDecimal requestedAmount, String description) {
        BigDecimal amount = Money.requireValid(requestedAmount);
        if (sourceAccountId == null || destinationAccountId == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Both account IDs are required", HttpStatus.NOT_FOUND);
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new BusinessException("SAME_ACCOUNT", "Source and destination accounts must be different", HttpStatus.BAD_REQUEST);
        }
        if (description != null && description.length() > 255) {
            throw new BusinessException("INVALID_REQUEST", "Description must not exceed 255 characters", HttpStatus.BAD_REQUEST);
        }

        // Every transfer locks in the same order, including transfers in opposite directions.
        boolean sourceFirst = sourceAccountId.compareTo(destinationAccountId) < 0;
        Account first = lockAccount(sourceFirst ? sourceAccountId : destinationAccountId);
        Account second = lockAccount(sourceFirst ? destinationAccountId : sourceAccountId);
        Account source = sourceFirst ? first : second;
        Account destination = sourceFirst ? second : first;
        source.requireActive();
        destination.requireActive();
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new BusinessException("CURRENCY_MISMATCH", "Accounts must use the same currency", HttpStatus.CONFLICT);
        }
        Money.requireBalanceCapacity(destination.getBalance().add(amount));
        BigDecimal sourceBefore = source.getBalance();
        BigDecimal destinationBefore = destination.getBalance();
        source.debit(amount);
        destination.credit(amount);

        Transfer transfer = transfers.saveAndFlush(new Transfer(sourceAccountId, destinationAccountId, amount, description));
        transactions.saveAndFlush(new AccountTransaction(source, transfer.getId(), TransactionType.TRANSFER_DEBIT,
                amount, sourceBefore, source.getBalance()));
        transactions.saveAndFlush(new AccountTransaction(destination, transfer.getId(), TransactionType.TRANSFER_CREDIT,
                amount, destinationBefore, destination.getBalance()));
        return TransferResponse.from(transfer);
    }

    private Account lockAccount(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account was not found", HttpStatus.NOT_FOUND));
    }
}
