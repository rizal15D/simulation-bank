package com.example.ledgerbank.account;

import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.OwnershipPolicy;
import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.customer.Customer;
import com.example.ledgerbank.customer.CustomerRepository;
import com.example.ledgerbank.customer.CustomerStatus;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {
    private final AccountRepository accounts;
    private final CustomerRepository customers;

    public AccountService(AccountRepository accounts, CustomerRepository customers) {
        this.accounts = accounts;
        this.customers = customers;
    }

    public AccountResponse create(BankingPrincipal actor, UUID customerId) {
        OwnershipPolicy.requireOwnedCustomer(actor, customerId);
        return create(customerId);
    }

    AccountResponse create(UUID customerId) {
        Customer customer = requireCustomer(customerId);
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("CUSTOMER_NOT_ACTIVE", "Customer must be active", HttpStatus.CONFLICT);
        }
        String accountNumber = String.format(Locale.ROOT, "LBK-%d-%08d",
                Year.now(ZoneOffset.UTC).getValue(), accounts.nextAccountNumber());
        return AccountResponse.from(accounts.saveAndFlush(new Account(customerId, accountNumber)));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(BankingPrincipal actor, UUID id) {
        Account account = requireAccount(id);
        OwnershipPolicy.requireReadableCustomer(actor, account.getCustomerId());
        return AccountResponse.from(account);
    }

    AccountResponse get(UUID id) {
        return AccountResponse.from(requireAccount(id));
    }

    private Account requireAccount(UUID id) {
        Account account = accounts.findById(id).orElseThrow(() ->
                new BusinessException("ACCOUNT_NOT_FOUND", "Account was not found", HttpStatus.NOT_FOUND));
        return account;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomer(BankingPrincipal actor, UUID customerId) {
        OwnershipPolicy.requireReadableCustomer(actor, customerId);
        return listByCustomer(customerId);
    }

    List<AccountResponse> listByCustomer(UUID customerId) {
        requireCustomer(customerId);
        return accounts.findByCustomerId(customerId).stream().map(AccountResponse::from).toList();
    }

    private Customer requireCustomer(UUID customerId) {
        return customers.findById(customerId).orElseThrow(() ->
                new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found", HttpStatus.NOT_FOUND));
    }
}
