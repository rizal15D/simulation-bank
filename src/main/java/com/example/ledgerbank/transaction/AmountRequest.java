package com.example.ledgerbank.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record AmountRequest(
        @Schema(description = "Nilai positif, maksimal 17 digit integer dan dua angka pecahan; tidak dibulatkan",
                type = "number", format = "decimal", minimum = "0", exclusiveMinimum = true,
                maximum = "99999999999999999.99", multipleOf = 0.01, example = "100000.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount) {
}
