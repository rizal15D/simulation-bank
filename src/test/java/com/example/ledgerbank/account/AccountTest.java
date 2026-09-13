package com.example.ledgerbank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ledgerbank.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class AccountTest {
    private final Account account = new Account(UUID.randomUUID(), "LBK-2026-00000001");

    @Test
    void debitCanSpendExactBalanceWithoutGoingNegative() {
        account.credit(new BigDecimal("123.45"));

        account.debit(new BigDecimal("123.45"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void insufficientFundsLeaveBalanceUnchanged() {
        account.credit(new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account balance is insufficient");

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void creditRejectsBalanceBeyondDatabaseCapacityWithoutChangingBalance() {
        account.credit(new BigDecimal("99999999999999999.99"));

        assertThatThrownBy(() -> account.credit(new BigDecimal("0.01")))
                .isInstanceOf(BusinessException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("99999999999999999.99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001"})
    void invalidAmountsCannotChangeBalance(String amount) {
        account.credit(new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.credit(new BigDecimal(amount)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal(amount)))
                .isInstanceOf(BusinessException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BLOCKED", "CLOSED"})
    void inactiveAccountsCannotBeDebitedOrCredited(AccountStatus status) {
        account.credit(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "status", status);

        assertThatThrownBy(() -> account.debit(BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account must be active");
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Account must be active");

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }
}
