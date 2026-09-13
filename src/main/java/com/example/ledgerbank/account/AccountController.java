package com.example.ledgerbank.account;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponse account = accounts.create(request.customerId());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse get(@PathVariable UUID accountId) {
        return accounts.get(accountId);
    }

    @GetMapping("/customers/{customerId}/accounts")
    public List<AccountResponse> listByCustomer(@PathVariable UUID customerId) {
        return accounts.listByCustomer(customerId);
    }
}
