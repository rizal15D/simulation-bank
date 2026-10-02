package com.example.ledgerbank.transfer;

import com.example.ledgerbank.common.Money;
import com.example.ledgerbank.common.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TransferRequestValidator {
    private static final String KEY_PATTERN = "[A-Za-z0-9._:-]{8,128}";

    ValidatedRequest validate(String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches(KEY_PATTERN)) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 128 letters, digits, dot, underscore, colon, or hyphen",
                    HttpStatus.BAD_REQUEST);
        }
        if (request == null) {
            throw new BusinessException("INVALID_REQUEST", "Transfer request is required", HttpStatus.BAD_REQUEST);
        }

        UUID source = request.sourceAccountId();
        UUID destination = request.destinationAccountId();
        if (source == null || destination == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Both account IDs are required", HttpStatus.NOT_FOUND);
        }
        if (source.equals(destination)) {
            throw new BusinessException("SAME_ACCOUNT", "Source and destination accounts must be different",
                    HttpStatus.BAD_REQUEST);
        }
        if (request.description() != null && request.description().length() > 255) {
            throw new BusinessException("INVALID_REQUEST", "Description must not exceed 255 characters",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal amount = Money.requireValid(request.amount());
        String requestHash = fingerprint(source, destination, amount, request.description());
        return new ValidatedRequest(source, destination, amount, request.description(), requestHash);
    }

    private String fingerprint(UUID source, UUID destination, BigDecimal amount, String description) {
        String encodedDescription = Base64.getEncoder().encodeToString(
                (description == null ? "" : description).getBytes(StandardCharsets.UTF_8));
        String canonical = source + "\n" + destination + "\n" + amount.toPlainString() + "\n" + encodedDescription;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    record ValidatedRequest(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String description,
            String requestHash) {
    }
}
