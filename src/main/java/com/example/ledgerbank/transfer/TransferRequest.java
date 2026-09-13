package com.example.ledgerbank.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                              BigDecimal amount, @Size(max = 255) String description) {
}
