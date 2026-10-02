package com.example.ledgerbank.transaction;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.account.AccountStatus;
import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.UserRole;
import com.example.ledgerbank.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WithdrawalServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final WithdrawalService service = new WithdrawalService(accounts, transactions);
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final BankingPrincipal actor = new BankingPrincipal(
            UUID.randomUUID(), customerId, "owner@example.com", UserRole.CUSTOMER);

    @Test
    void withdrawsEntireBalanceAndRecordsHistory() {
        Account account = account("10.25");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = service.withdraw(actor, accountId, new BigDecimal("10.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.transactionType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.transferId()).isNull();
        assertThat(result.balanceBefore()).isEqualByComparingTo("10.25");
        assertThat(result.balanceAfter()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.001"})
    void rejectsInvalidAmounts(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        assertThatThrownBy(() -> service.withdraw(actor, accountId, value))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_AMOUNT");
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void rejectsInsufficientBalanceWithoutChangingIt() {
        Account account = account("10.00");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.withdraw(actor, accountId, new BigDecimal("10.01")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verifyNoInteractions(transactions);
    }

    @Test
    void rejectsMissingAccount() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdraw(actor, accountId, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(transactions);
    }

    @Test
    void rejectsClosedAccount() {
        Account account = account("10.00");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.withdraw(actor, accountId, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verifyNoInteractions(transactions);
    }

    private Account account(String balance) {
        Account account = new Account(customerId, "LBK-2026-00000001");
        ReflectionTestUtils.setField(account, "id", accountId);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }
}
