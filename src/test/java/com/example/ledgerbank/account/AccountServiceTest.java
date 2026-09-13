package com.example.ledgerbank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.customer.Customer;
import com.example.ledgerbank.customer.CustomerRepository;
import com.example.ledgerbank.customer.CustomerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock private AccountRepository accounts;
    @Mock private CustomerRepository customers;
    private AccountService service;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, customers);
    }

    @Test
    void createUsesSequenceAndDefaultsToActiveIdrWithZeroBalance() {
        when(customers.findById(customerId)).thenReturn(Optional.of(activeCustomer()));
        when(accounts.nextAccountNumber()).thenReturn(42L, 43L);
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse first = service.create(customerId);
        AccountResponse second = service.create(customerId);

        assertThat(first.customerId()).isEqualTo(customerId);
        assertThat(first.accountNumber()).matches("LBK-\\d{4}-00000042");
        assertThat(second.accountNumber()).matches("LBK-\\d{4}-00000043");
        assertThat(first.currency()).isEqualTo("IDR");
        assertThat(first.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(first.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void sequenceNumbersAreNotTruncatedAfterEightDigits() {
        when(customers.findById(customerId)).thenReturn(Optional.of(activeCustomer()));
        when(accounts.nextAccountNumber()).thenReturn(100_000_000L);
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(customerId).accountNumber()).matches("LBK-\\d{4}-100000000");
    }

    @Test
    void createRejectsMissingCustomerBeforeAllocatingAccountNumber() {
        when(customers.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Customer was not found");

        verifyNoInteractions(accounts);
    }

    @Test
    void createRejectsInactiveCustomerBeforeAllocatingAccountNumber() {
        Customer customer = activeCustomer();
        ReflectionTestUtils.setField(customer, "status", CustomerStatus.SUSPENDED);
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.create(customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Customer must be active");

        verifyNoInteractions(accounts);
    }

    @Test
    void getReturnsRequestedAccount() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(customerId, "LBK-2026-00000001");
        ReflectionTestUtils.setField(account, "id", accountId);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        AccountResponse response = service.get(accountId);

        assertThat(response.id()).isEqualTo(accountId);
        assertThat(response.customerId()).isEqualTo(customerId);
    }

    @Test
    void getRejectsMissingAccount() {
        UUID accountId = UUID.randomUUID();
        when(accounts.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account was not found");
    }

    @Test
    void listReturnsOnlyAccountsForRequestedCustomer() {
        when(customers.findById(customerId)).thenReturn(Optional.of(activeCustomer()));
        when(accounts.findByCustomerId(customerId)).thenReturn(List.of(
                new Account(customerId, "LBK-2026-00000001"),
                new Account(customerId, "LBK-2026-00000002")));

        List<AccountResponse> responses = service.listByCustomer(customerId);

        assertThat(responses).hasSize(2).allSatisfy(account ->
                assertThat(account.customerId()).isEqualTo(customerId));
        verify(accounts).findByCustomerId(customerId);
    }

    @Test
    void listDistinguishesMissingCustomerFromCustomerWithoutAccounts() {
        when(customers.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByCustomer(customerId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Customer was not found");

        verify(accounts, never()).findByCustomerId(any());
    }

    private Customer activeCustomer() {
        return new Customer("Siti Aminah", "siti@example.com");
    }
}
