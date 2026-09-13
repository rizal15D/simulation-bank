package com.example.ledgerbank.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.example.ledgerbank.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class Money {
    public static final BigDecimal MAX_VALUE = new BigDecimal("99999999999999999.99");

    private Money() {}

    public static BigDecimal requireValid(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_VALUE) > 0) {
            throw invalidAmount();
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    public static void requireBalanceCapacity(BigDecimal balance) {
        if (balance.compareTo(MAX_VALUE) > 0) {
            throw new BusinessException("BALANCE_LIMIT_EXCEEDED",
                    "Account balance exceeds the supported maximum", HttpStatus.CONFLICT);
        }
    }

    private static BusinessException invalidAmount() {
        return new BusinessException("INVALID_AMOUNT",
                "Amount must be positive, fit 17 integer digits and have at most 2 decimal places",
                HttpStatus.BAD_REQUEST);
    }
}
