CREATE TABLE account_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    transfer_id UUID REFERENCES transfers(id),
    transaction_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_before NUMERIC(19, 2) NOT NULL CHECK (balance_before >= 0),
    balance_after NUMERIC(19, 2) NOT NULL CHECK (balance_after >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT')),
    CONSTRAINT ck_transactions_transfer CHECK (
        (transaction_type IN ('TRANSFER_DEBIT', 'TRANSFER_CREDIT') AND transfer_id IS NOT NULL)
        OR (transaction_type IN ('DEPOSIT', 'WITHDRAWAL') AND transfer_id IS NULL)
    ),
    CONSTRAINT ck_transactions_balance_delta CHECK (
        (transaction_type IN ('DEPOSIT', 'TRANSFER_CREDIT') AND balance_after = balance_before + amount)
        OR (transaction_type IN ('WITHDRAWAL', 'TRANSFER_DEBIT') AND balance_after = balance_before - amount)
    )
);

CREATE INDEX idx_transactions_account_history ON account_transactions(account_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX idx_transactions_transfer_type ON account_transactions(transfer_id, transaction_type) WHERE transfer_id IS NOT NULL;
