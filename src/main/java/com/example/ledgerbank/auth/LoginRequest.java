package com.example.ledgerbank.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "budi@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "correct horse battery staple") @NotBlank @Size(max = 200) String password) {
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
