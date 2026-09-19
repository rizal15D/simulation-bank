CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    customer_id UUID UNIQUE REFERENCES customers(id),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_normalized CHECK (email = lower(trim(email))),
    CONSTRAINT ck_app_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_app_users_customer_link CHECK (
        (role = 'CUSTOMER' AND customer_id IS NOT NULL) OR (role = 'ADMIN' AND customer_id IS NULL)
    )
);

CREATE TABLE transfer_idempotency (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    transfer_id UUID NOT NULL UNIQUE REFERENCES transfers(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_transfer_idempotency_actor_key UNIQUE (actor_id, idempotency_key)
);

CREATE INDEX idx_transfer_idempotency_expiry ON transfer_idempotency(expires_at);
