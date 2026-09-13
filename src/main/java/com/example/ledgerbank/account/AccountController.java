package com.example.ledgerbank.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Accounts", description = "Pembukaan dan pembacaan rekening IDR")
@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @Operation(summary = "Membuka rekening", description = "Customer harus ACTIVE. Rekening baru memiliki saldo 0.00 dan currency IDR.", responses = {
            @ApiResponse(responseCode = "201", description = "Rekening dibuat", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")})
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponse account = accounts.create(request.customerId());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }

    @Operation(summary = "Membaca rekening", responses = {
            @ApiResponse(responseCode = "200", description = "Detail rekening", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    @GetMapping("/accounts/{accountId}")
    public AccountResponse get(@PathVariable UUID accountId) {
        return accounts.get(accountId);
    }

    @Operation(summary = "Daftar rekening customer", responses = {
            @ApiResponse(responseCode = "200", description = "Daftar rekening; dapat kosong", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    @GetMapping("/customers/{customerId}/accounts")
    public List<AccountResponse> listByCustomer(@PathVariable UUID customerId) {
        return accounts.listByCustomer(customerId);
    }
}
