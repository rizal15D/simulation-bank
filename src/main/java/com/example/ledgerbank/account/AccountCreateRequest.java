package com.example.ledgerbank.account;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AccountCreateRequest(@NotNull UUID customerId) {}
