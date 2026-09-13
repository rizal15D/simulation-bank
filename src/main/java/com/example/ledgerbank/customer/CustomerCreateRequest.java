package com.example.ledgerbank.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @Schema(example = "Budi Santoso") @NotBlank @Size(max = 150) String fullName,
        @Schema(example = "budi@example.com") @NotBlank @Email @Size(max = 254) String email) {
    public CustomerCreateRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }
}
