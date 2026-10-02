package com.example.ledgerbank.transfer;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.account.AccountStatus;
import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.UserRole;
import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventPublisher;
import com.example.ledgerbank.transaction.AccountTransaction;
import com.example.ledgerbank.transaction.TransactionRepository;
import com.example.ledgerbank.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransferServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransferRepository transfers = mock(TransferRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final BankingEventPublisher events = mock(BankingEventPublisher.class);
    private final TransferService service = new TransferService(accounts, transfers, transactions, events);
    private final UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID destinationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID sourceCustomerId = UUID.randomUUID();
    private final UUID destinationCustomerId = UUID.randomUUID();
    private final BankingPrincipal actor = principal(sourceCustomerId);
    private final BankingPrincipal destinationActor = principal(destinationCustomerId);
    private final Account source = account(sourceId, sourceCustomerId, "100.00");
    private final Account destination = account(destinationId, destinationCustomerId, "20.00");

    @Test
    void transfersExactAmountAndRecordsBothSidesWithSharedTransferId() {
        availableAccounts();
        when(transfers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransferResponse result = service.transfer(actor, sourceId, destinationId, new BigDecimal("25.50"), "Lunch");

        assertThat(source.getBalance()).isEqualByComparingTo("74.50");
        assertThat(destination.getBalance()).isEqualByComparingTo("45.50");
        assertThat(source.getBalance().add(destination.getBalance())).isEqualByComparingTo("120.00");
        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(result.referenceNumber()).startsWith("TRF-");
        assertThat(result.sourceAccountId()).isEqualTo(sourceId);
        assertThat(result.destinationAccountId()).isEqualTo(destinationId);
        assertThat(result.amount()).isEqualByComparingTo("25.50");
        assertThat(result.description()).isEqualTo("Lunch");
        assertThat(result.completedAt()).isNotNull();
        ArgumentCaptor<AccountTransaction> rows = ArgumentCaptor.forClass(AccountTransaction.class);
        verify(transactions, times(2)).saveAndFlush(rows.capture());
        assertThat(rows.getAllValues()).extracting(AccountTransaction::getTransferId).containsOnly(result.id());
        AccountTransaction debit = rows.getAllValues().getFirst();
        AccountTransaction credit = rows.getAllValues().getLast();
        assertThat(debit.getTransactionType()).isEqualTo(TransactionType.TRANSFER_DEBIT);
        assertThat(debit.getAccountId()).isEqualTo(sourceId);
        assertThat(debit.getBalanceBefore()).isEqualByComparingTo("100.00");
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("74.50");
        assertThat(credit.getTransactionType()).isEqualTo(TransactionType.TRANSFER_CREDIT);
        assertThat(credit.getAccountId()).isEqualTo(destinationId);
        assertThat(credit.getBalanceBefore()).isEqualByComparingTo("20.00");
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("45.50");
        verify(events).publish(any(BankingEvent.class));
        InOrder locks = inOrder(accounts);
        locks.verify(accounts).findByIdForUpdate(destinationId);
        locks.verify(accounts).findByIdForUpdate(sourceId);
    }

    @Test
    void oppositeTransferUsesSameAccountLockOrder() {
        availableAccounts();
        when(transfers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.transfer(destinationActor, destinationId, sourceId, BigDecimal.ONE, null);
        InOrder locks = inOrder(accounts);
        locks.verify(accounts).findByIdForUpdate(destinationId);
        locks.verify(accounts).findByIdForUpdate(sourceId);
        assertThat(source.getBalance()).isEqualByComparingTo("101.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("19.00");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-10", "0.001", "100000000000000000"})
    void rejectsInvalidAmount(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, value, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_AMOUNT");
        verifyNoInteractions(accounts, transfers, transactions);
    }

    @Test
    void rejectsSelfTransfer() {
        assertThatThrownBy(() -> service.transfer(actor, sourceId, sourceId, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("SAME_ACCOUNT");
        verifyNoInteractions(accounts, transfers, transactions);
    }

    @Test
    void rejectsMissingSourceAccount() {
        when(accounts.findByIdForUpdate(destinationId)).thenReturn(Optional.of(destination));
        when(accounts.findByIdForUpdate(sourceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(transfers, transactions);
    }

    @Test
    void rejectsMissingDestinationAccount() {
        when(accounts.findByIdForUpdate(destinationId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(transfers, transactions);
    }

    @Test
    void rejectsInsufficientBalanceAndKeepsBothBalances() {
        availableAccounts();
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, new BigDecimal("100.01"), null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INSUFFICIENT_BALANCE");
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEitherInactiveAccount(boolean sourceInactive) {
        availableAccounts();
        ReflectionTestUtils.setField(sourceInactive ? source : destination, "status", AccountStatus.BLOCKED);
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertUnchanged();
    }

    @Test
    void rejectsCurrencyMismatch() {
        availableAccounts();
        ReflectionTestUtils.setField(destination, "currency", "USD");
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        assertUnchanged();
    }

    @Test
    void checksDestinationCapacityBeforeDebitingSource() {
        availableAccounts();
        ReflectionTestUtils.setField(destination, "balance", new BigDecimal("99999999999999999.99"));
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, new BigDecimal("0.01"), null))
                .isInstanceOf(BusinessException.class);
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("99999999999999999.99");
        verifyNoInteractions(transfers, transactions);
    }

    @Test
    void propagatesHistoryFailureToTransactionBoundary() {
        availableAccounts();
        when(transfers.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("History write failed"));
        assertThatThrownBy(() -> service.transfer(actor, sourceId, destinationId, BigDecimal.ONE, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessage("History write failed");
        // Actual persisted rollback is verified separately against PostgreSQL; a unit test has no DB transaction.
    }

    private void availableAccounts() {
        when(accounts.findByIdForUpdate(sourceId)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(destinationId)).thenReturn(Optional.of(destination));
    }

    private void assertUnchanged() {
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("20.00");
        verifyNoInteractions(transfers, transactions);
    }

    private static BankingPrincipal principal(UUID customerId) {
        return new BankingPrincipal(UUID.randomUUID(), customerId, "owner@example.com", UserRole.CUSTOMER);
    }

    private static Account account(UUID id, UUID customerId, String balance) {
        Account account = new Account(customerId, "LBK-" + id);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }
}
