package com.example.ledgerbank.customer;

import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.OwnershipPolicy;
import com.example.ledgerbank.common.exception.BusinessException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {
    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public CustomerResponse createByAdmin(BankingPrincipal actor, String fullName, String email) {
        OwnershipPolicy.requireAdmin(actor);
        return create(fullName, email);
    }

    CustomerResponse create(String fullName, String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmail(normalizedEmail)) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT);
        }
        Customer customer = new Customer(fullName.trim(), normalizedEmail);
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(BankingPrincipal actor, UUID id) {
        OwnershipPolicy.requireReadableCustomer(actor, id);
        return get(id);
    }

    CustomerResponse get(UUID id) {
        Customer customer = customers.findById(id).orElseThrow(() ->
                new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found", HttpStatus.NOT_FOUND));
        return CustomerResponse.from(customer);
    }
}
