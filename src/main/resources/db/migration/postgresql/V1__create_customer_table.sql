CREATE TABLE customers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_full_name CHECK (length(trim(full_name)) > 0),
    CONSTRAINT ck_customers_email_normalized CHECK (email = lower(trim(email))),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);
