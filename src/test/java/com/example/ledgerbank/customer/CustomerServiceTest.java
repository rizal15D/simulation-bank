package com.example.ledgerbank.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.common.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock private CustomerRepository customers;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customers);
    }

    @Test
    void createNormalizesEmailAndNameBeforeCheckingUniquenessAndSaving() {
        UUID id = UUID.randomUUID();
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            ReflectionTestUtils.setField(customer, "id", id);
            return customer;
        });

        CustomerResponse response = service.create("  Siti Aminah  ", "  SITI@EXAMPLE.COM  ");

        verify(customers).existsByEmail("siti@example.com");
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getEmail()).isEqualTo("siti@example.com");
        assertThat(customerCaptor.getValue().getFullName()).isEqualTo("Siti Aminah");
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void duplicateEmailDoesNotCreateAnotherCustomer() {
        when(customers.existsByEmail("siti@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Siti Aminah", " SITI@example.com "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email is already registered");

        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void getReturnsExistingCustomer() {
        UUID id = UUID.randomUUID();
        Customer customer = new Customer("Siti Aminah", "siti@example.com");
        ReflectionTestUtils.setField(customer, "id", id);
        when(customers.findById(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = service.get(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("siti@example.com");
    }

    @Test
    void getRejectsMissingCustomer() {
        UUID id = UUID.randomUUID();
        when(customers.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Customer was not found");
    }
}
