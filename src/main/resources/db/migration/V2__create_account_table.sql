CREATE SEQUENCE ledgerbank_account_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    account_number VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0),
    CONSTRAINT ck_accounts_currency CHECK (currency = 'IDR'),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);
