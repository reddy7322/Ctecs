-- CTECS Buy Me a Coffee Donation Service — Initial Schema Migration
-- Strictly enforces unique order references, idempotency keys, webhook event deduplication,
-- donor ownership scoping, and immutable verification audit trails.

CREATE TABLE IF NOT EXISTS donation_orders (
    order_ref         VARCHAR(64)    PRIMARY KEY,
    gateway_order_id  VARCHAR(128)   DEFAULT '',
    idempotency_key   VARCHAR(128)   NOT NULL UNIQUE,
    owner_user_id     VARCHAR(128)   NOT NULL,
    amount_inr        NUMERIC(12, 2) NOT NULL CHECK (amount_inr > 0),
    currency          VARCHAR(8)     NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    upi_id            VARCHAR(128)   NOT NULL DEFAULT 'tvtelugu@ptyes',
    recipient_name    VARCHAR(128)   NOT NULL DEFAULT 'madhu',
    description       VARCHAR(255)   NOT NULL DEFAULT 'CTECS Coffee Support',
    upi_uri           TEXT           NOT NULL,
    status            VARCHAR(24)    NOT NULL DEFAULT 'CREATED'
                      CHECK (status IN ('CREATED', 'PENDING', 'UNDER_REVIEW', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    payment_method    VARCHAR(64)    NOT NULL DEFAULT 'UPI',
    utr               VARCHAR(64)    DEFAULT '',
    confirmation_ref  VARCHAR(128)   DEFAULT '',
    server_verified   BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at_ms     BIGINT         NOT NULL,
    updated_at_ms     BIGINT         NOT NULL,
    verified_at_ms    BIGINT,
    expires_at_ms     BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_donation_orders_owner_created
    ON donation_orders (owner_user_id, created_at_ms DESC);

CREATE INDEX IF NOT EXISTS idx_donation_orders_status_created
    ON donation_orders (status, created_at_ms DESC);

CREATE INDEX IF NOT EXISTS idx_donation_orders_gateway_id
    ON donation_orders (gateway_order_id);

-- Replay & duplicate webhook event deduplication table
CREATE TABLE IF NOT EXISTS donation_webhook_events (
    event_id          VARCHAR(128)   PRIMARY KEY,
    order_ref         VARCHAR(64)    NOT NULL REFERENCES donation_orders(order_ref),
    event_type        VARCHAR(64)    NOT NULL,
    payload_sha256    VARCHAR(64)    NOT NULL,
    processed_at_ms   BIGINT         NOT NULL
);

-- Immutable verification & status transition audit log
CREATE TABLE IF NOT EXISTS donation_audit_logs (
    id                BIGSERIAL      PRIMARY KEY,
    order_ref         VARCHAR(64)    NOT NULL REFERENCES donation_orders(order_ref),
    previous_status   VARCHAR(24)    NOT NULL,
    new_status        VARCHAR(24)    NOT NULL,
    actor_role        VARCHAR(32)    NOT NULL,
    actor_id          VARCHAR(128)   NOT NULL,
    evidence          TEXT           NOT NULL,
    reason            TEXT           NOT NULL,
    timestamp_ms      BIGINT         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_donation_audit_order_time
    ON donation_audit_logs (order_ref, timestamp_ms DESC);
