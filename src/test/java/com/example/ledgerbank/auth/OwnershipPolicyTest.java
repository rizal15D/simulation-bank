package com.example.ledgerbank.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ledgerbank.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnershipPolicyTest {
    private final UUID ownerId = UUID.randomUUID();
    private final BankingPrincipal owner = new BankingPrincipal(UUID.randomUUID(), ownerId,
            "owner@example.com", UserRole.CUSTOMER);
    private final BankingPrincipal other = new BankingPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            "other@example.com", UserRole.CUSTOMER);
    private final BankingPrincipal admin = new BankingPrincipal(UUID.randomUUID(), null,
            "admin@example.com", UserRole.ADMIN);

    @Test
    void customerCanReadAndMutateOnlyOwnedResources() {
        assertThatCode(() -> OwnershipPolicy.requireReadableCustomer(owner, ownerId)).doesNotThrowAnyException();
        assertThatCode(() -> OwnershipPolicy.requireOwnedCustomer(owner, ownerId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> OwnershipPolicy.requireReadableCustomer(other, ownerId))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> OwnershipPolicy.requireOwnedCustomer(other, ownerId))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
    }

    @Test
    void adminCanReadButCannotPerformCustomerOwnedMutation() {
        assertThatCode(() -> OwnershipPolicy.requireReadableCustomer(admin, ownerId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> OwnershipPolicy.requireOwnedCustomer(admin, ownerId))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
    }
}
