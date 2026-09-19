package com.example.ledgerbank.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthSecretRedactionTest {
    @Test
    void requestAndResponseStringsNeverExposeCredentials() {
        String password = "sensitive-password";
        String token = "sensitive-bearer-token";

        assertThat(new LoginRequest("user@example.com", password).toString())
                .doesNotContain(password).contains("[REDACTED]");
        assertThat(new RegisterRequest("User", "user@example.com", password).toString())
                .doesNotContain(password).contains("[REDACTED]");
        assertThat(new TokenResponse(token, "Bearer", Instant.now(), null).toString())
                .doesNotContain(token).contains("[REDACTED]");
    }
}
