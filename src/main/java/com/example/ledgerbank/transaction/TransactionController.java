package com.example.ledgerbank.transaction;

import com.example.ledgerbank.auth.BankingPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Transactions", description = "Deposit, withdrawal, dan histori rekening")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class TransactionController {
    private final DepositService deposits;
    private final WithdrawalService withdrawals;
    private final TransactionHistoryService history;

    public TransactionController(DepositService deposits, WithdrawalService withdrawals, TransactionHistoryService history) {
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.history = history;
    }

    @Operation(summary = "Deposit simulasi", description = "Menambah saldo rekening ACTIVE dan mencatat histori dalam satu database transaction.", responses = {
            @ApiResponse(responseCode = "201", description = "Transaksi berhasil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")})
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@AuthenticationPrincipal BankingPrincipal actor,
                                       @PathVariable UUID accountId, @RequestBody AmountRequest request) {
        return deposits.deposit(actor, accountId, request.amount());
    }

    @Operation(summary = "Withdrawal simulasi", description = "Mengurangi saldo rekening ACTIVE. Saldo harus mencukupi; perubahan saldo dan histori bersifat atomik.", responses = {
            @ApiResponse(responseCode = "201", description = "Transaksi berhasil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")})
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@AuthenticationPrincipal BankingPrincipal actor,
                                        @PathVariable UUID accountId, @RequestBody AmountRequest request) {
        return withdrawals.withdraw(actor, accountId, request.amount());
    }

    @Operation(summary = "Histori transaksi rekening", description = "Urutan createdAt DESC, lalu id DESC. Page dimulai dari nol; size 1 sampai 100.", responses = {
            @ApiResponse(responseCode = "200", description = "Halaman histori transaksi", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")})
    @GetMapping("/transactions")
    public TransactionHistoryResponse history(@AuthenticationPrincipal BankingPrincipal actor,
                                              @PathVariable UUID accountId,
                                              @Parameter(description = "Nomor halaman", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
                                              @Parameter(description = "Jumlah transaksi per halaman", schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return history.history(actor, accountId, page, size);
    }
}
