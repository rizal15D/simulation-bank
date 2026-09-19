CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    reference_number VARCHAR(40) NOT NULL UNIQUE,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status = 'SUCCESS'),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_transfers_different_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_transfers_completion CHECK (completed_at >= created_at)
);

CREATE INDEX idx_transfers_source ON transfers(source_account_id);
CREATE INDEX idx_transfers_destination ON transfers(destination_account_id);
