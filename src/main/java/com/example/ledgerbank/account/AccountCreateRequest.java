package com.example.ledgerbank.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AccountCreateRequest(
        @Schema(description = "ID customer yang sudah dibuat", example = "11111111-1111-4111-8111-111111111111")
        @NotNull UUID customerId) {
}
