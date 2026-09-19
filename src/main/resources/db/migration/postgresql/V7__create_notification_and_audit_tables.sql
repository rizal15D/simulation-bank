CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id),
    event_type VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notifications_event_customer UNIQUE (event_id, customer_id)
);

CREATE INDEX idx_notifications_customer_created
    ON notifications(customer_id, created_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    actor_id UUID REFERENCES app_users(id),
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id UUID NOT NULL,
    metadata TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_audit_logs_event UNIQUE (event_id)
);

CREATE INDEX idx_audit_logs_actor_created
    ON audit_logs(actor_id, created_at DESC);
CREATE INDEX idx_audit_logs_resource
    ON audit_logs(resource_type, resource_id, created_at DESC);
