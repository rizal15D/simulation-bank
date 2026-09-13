package com.example.ledgerbank.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @Schema(description = "ID rekening sumber", example = "22222222-2222-4222-8222-222222222222")
        @NotNull UUID sourceAccountId,
        @Schema(description = "ID rekening tujuan", example = "33333333-3333-4333-8333-333333333333")
        @NotNull UUID destinationAccountId,
        @Schema(description = "Nilai positif, maksimal 17 digit integer dan dua angka pecahan; tidak dibulatkan",
                type = "number", format = "decimal", minimum = "0", exclusiveMinimum = true,
                maximum = "99999999999999999.99", multipleOf = 0.01, example = "100000.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,
        @Schema(example = "Transfer internal")
        @Size(max = 255) String description) {
}
