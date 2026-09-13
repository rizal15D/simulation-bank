package com.example.ledgerbank.account;

import com.example.ledgerbank.common.Money;
import com.example.ledgerbank.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 40)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {}

    public Account(UUID customerId, String accountNumber) {
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.currency = "IDR";
        this.balance = new BigDecimal("0.00");
        this.status = AccountStatus.ACTIVE;
    }

    public void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_NOT_ACTIVE", "Account must be active", HttpStatus.CONFLICT);
        }
    }

    public void debit(BigDecimal amount) {
        requireActive();
        BigDecimal validAmount = Money.requireValid(amount);
        if (balance.compareTo(validAmount) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "Account balance is insufficient", HttpStatus.CONFLICT);
        }
        balance = balance.subtract(validAmount);
    }

    public void credit(BigDecimal amount) {
        requireActive();
        BigDecimal validAmount = Money.requireValid(amount);
        BigDecimal newBalance = balance.add(validAmount);
        Money.requireBalanceCapacity(newBalance);
        balance = newBalance;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
