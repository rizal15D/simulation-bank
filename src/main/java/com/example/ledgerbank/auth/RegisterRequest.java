package com.example.ledgerbank.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Budi Santoso") @NotBlank @Size(max = 150) String fullName,
        @Schema(example = "budi@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "correct horse battery staple", minLength = 12, maxLength = 72)
        @NotBlank
        @Pattern(regexp = "[\\x20-\\x7E]{12,72}",
                message = "must contain 12 to 72 printable ASCII characters") String password) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
    }
}
