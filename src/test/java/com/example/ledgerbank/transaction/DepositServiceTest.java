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

class DepositServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final DepositService service = new DepositService(accounts, transactions);
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final BankingPrincipal actor = new BankingPrincipal(
            UUID.randomUUID(), customerId, "owner@example.com", UserRole.CUSTOMER);

    @Test
    void depositUpdatesBalanceAndRecordsExactDelta() {
        Account account = account("10.25");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = service.deposit(actor, accountId, new BigDecimal("0.10"));

        assertThat(account.getBalance()).isEqualByComparingTo("10.35");
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.transferId()).isNull();
        assertThat(result.transactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.amount()).isEqualByComparingTo("0.10");
        assertThat(result.balanceBefore()).isEqualByComparingTo("10.25");
        assertThat(result.balanceAfter()).isEqualByComparingTo("10.35");
        assertThat(result.id()).isNotNull();
        assertThat(result.createdAt()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000"})
    void rejectsInvalidAmountBeforeLoadingAccount(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        assertThatThrownBy(() -> service.deposit(actor, accountId, value))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_AMOUNT");
        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void rejectsMissingAccount() {
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deposit(actor, accountId, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(transactions);
    }

    @Test
    void rejectsInactiveAccountWithoutChangingBalance() {
        Account account = account("10.00");
        ReflectionTestUtils.setField(account, "status", AccountStatus.BLOCKED);
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.deposit(actor, accountId, BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verifyNoInteractions(transactions);
    }

    @Test
    void rejectsBalanceOverflowWithoutChangingBalance() {
        Account account = account("99999999999999999.99");
        when(accounts.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.deposit(actor, accountId, new BigDecimal("0.01")))
                .isInstanceOf(BusinessException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("99999999999999999.99");
        verifyNoInteractions(transactions);
    }

    private Account account(String balance) {
        Account account = new Account(customerId, "LBK-2026-00000001");
        ReflectionTestUtils.setField(account, "id", accountId);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }
}
