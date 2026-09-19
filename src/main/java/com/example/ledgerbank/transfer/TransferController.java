package com.example.ledgerbank.transfer;

import com.example.ledgerbank.auth.BankingPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Transfer internal atomik")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final IdempotentTransferService transfers;

    public TransferController(IdempotentTransferService transfers) {
        this.transfers = transfers;
    }

    @Operation(summary = "Transfer internal", description = "Memindahkan dana dari rekening milik caller. Header transfer, debit, credit, dan record idempotency disimpan atomik.", responses = {
            @ApiResponse(responseCode = "201", description = "Transfer berhasil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@AuthenticationPrincipal BankingPrincipal actor,
                                     @Parameter(description = "Kunci unik 8-128 karakter; retry request yang sama mengembalikan transfer sebelumnya",
                                             required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody TransferRequest request) {
        return transfers.transfer(actor, idempotencyKey, request);
    }
}
