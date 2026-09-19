package com.example.ledgerbank.event;

public enum BankingEventType {
    USER_LOGIN("user.login"),
    ACCOUNT_CREATED("account.created"),
    TRANSFER_COMPLETED("transfer.completed"),
    TRANSFER_FAILED("transfer.failed");

    private final String routingKey;

    BankingEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
