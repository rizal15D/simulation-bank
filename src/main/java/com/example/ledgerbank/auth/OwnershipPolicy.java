package com.example.ledgerbank.auth;

import com.example.ledgerbank.common.exception.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class OwnershipPolicy {
    private OwnershipPolicy() {}

    public static void requireReadableCustomer(BankingPrincipal actor, UUID customerId) {
        if (actor != null && (actor.isAdmin() || customerId.equals(actor.customerId()))) {
            return;
        }
        forbidden();
    }

    public static void requireOwnedCustomer(BankingPrincipal actor, UUID customerId) {
        if (actor != null && actor.role() == UserRole.CUSTOMER && customerId.equals(actor.customerId())) {
            return;
        }
        forbidden();
    }

    public static void requireAdmin(BankingPrincipal actor) {
        if (actor != null && actor.isAdmin()) {
            return;
        }
        forbidden();
    }

    private static void forbidden() {
        throw new BusinessException("FORBIDDEN", "You are not allowed to access this resource", HttpStatus.FORBIDDEN);
    }
}
