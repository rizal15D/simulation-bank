package com.example.ledgerbank.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ledgerbank.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TransferRequestValidatorTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DESTINATION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final TransferRequestValidator validator = new TransferRequestValidator();

    @Test
    void normalizesAmountAndMatchesCanonicalProtocolVector() {
        TransferRequestValidator.ValidatedRequest result = validator.validate(
                "transfer-key-001", new TransferRequest(SOURCE, DESTINATION, new BigDecimal("25"), "Lunch"));

        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.requestHash())
                .isEqualTo("3821713ec8db2e03072255dd7f76291a7d5a75a4ce1d5461352a39aaa14fc090");
    }

    @Test
    void equivalentAmountsAndEmptyDescriptionsHaveStableFingerprint() {
        String nullDescription = validate(new BigDecimal("25"), null).requestHash();
        String emptyDescription = validate(new BigDecimal("25.0"), "").requestHash();

        assertThat(nullDescription).isEqualTo(emptyDescription);
    }

    @Test
    void materialPayloadChangesProduceDifferentFingerprints() {
        String baseline = validate(new BigDecimal("25.00"), "Lunch").requestHash();

        assertThat(validator.validate("transfer-key-001",
                new TransferRequest(DESTINATION, SOURCE, new BigDecimal("25.00"), "Lunch")).requestHash())
                .isNotEqualTo(baseline);
        assertThat(validate(new BigDecimal("25.01"), "Lunch").requestHash()).isNotEqualTo(baseline);
        assertThat(validate(new BigDecimal("25.00"), "Dinner").requestHash()).isNotEqualTo(baseline);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "contains spaces", "contains/slash"})
    void rejectsInvalidIdempotencyKeys(String key) {
        assertThatThrownBy(() -> validator.validate(key,
                new TransferRequest(SOURCE, DESTINATION, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void rejectsInvalidTransferBeforeInfrastructureCoordination() {
        assertThatThrownBy(() -> validator.validate("transfer-key-001",
                new TransferRequest(SOURCE, SOURCE, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SAME_ACCOUNT");

        assertThatThrownBy(() -> validator.validate("transfer-key-001",
                new TransferRequest(SOURCE, DESTINATION, new BigDecimal("0.001"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_AMOUNT");
    }

    private TransferRequestValidator.ValidatedRequest validate(BigDecimal amount, String description) {
        return validator.validate("transfer-key-001",
                new TransferRequest(SOURCE, DESTINATION, amount, description));
    }
}
