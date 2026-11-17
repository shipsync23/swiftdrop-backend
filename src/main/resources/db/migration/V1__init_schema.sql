-- SwiftDrop database schema (matches report section 3: ER Diagram and Data Design)
-- Flyway runs this automatically when the API starts.

-- ---------- Users and authentication ----------
CREATE TABLE roles (
    id    SMALLINT    PRIMARY KEY,
    name  VARCHAR(20) NOT NULL UNIQUE          -- CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN
);

CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100),                -- NULL for Google-only accounts
    google_id      VARCHAR(100) UNIQUE,          -- NULL for email/password accounts
    full_name      VARCHAR(150) NOT NULL,
    phone          VARCHAR(20),
    role_id        SMALLINT     NOT NULL REFERENCES roles(id),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, DISABLED
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,    -- SHA-256 of the token, never the raw token
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE TABLE saved_addresses (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(50)  NOT NULL,           -- e.g. Home, Office
    address     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Zones and pricing ----------
CREATE TABLE zones (
    id           SERIAL       PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE pricing_rules (
    id             SERIAL         PRIMARY KEY,
    zone_id        INT            NOT NULL REFERENCES zones(id),
    min_weight_kg  NUMERIC(6,2)   NOT NULL,
    max_weight_kg  NUMERIC(6,2)   NOT NULL,
    base_fee       NUMERIC(10,2)  NOT NULL,
    per_kg_fee     NUMERIC(10,2)  NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    CHECK (min_weight_kg >= 0 AND max_weight_kg > min_weight_kg),
    CHECK (base_fee >= 0 AND per_kg_fee >= 0)
);

-- ---------- Merchants ----------
CREATE TABLE merchants (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL UNIQUE REFERENCES users(id),
    business_name       VARCHAR(150) NOT NULL,
    business_reg_no     VARCHAR(50),
    approval_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    approved_by         UUID         REFERENCES users(id),
    approved_at         TIMESTAMPTZ,
    webhook_url         VARCHAR(500),
    webhook_secret_enc  VARCHAR(500),            -- encrypted (not hashed) so we can sign webhooks
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE api_credentials (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id  UUID         NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    key_prefix   VARCHAR(12)  NOT NULL,          -- first characters, shown in the dashboard
    key_hash     VARCHAR(100) NOT NULL UNIQUE,   -- SHA-256 of the full API key
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, REVOKED
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);

-- ---------- Couriers ----------
CREATE TABLE couriers (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL UNIQUE REFERENCES users(id),
    zone_id         INT         REFERENCES zones(id),
    vehicle_type    VARCHAR(30),
    vehicle_number  VARCHAR(20),
    availability    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'   -- AVAILABLE, OFF_DUTY
);

-- ---------- Parcels ----------
CREATE TABLE parcels (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_number  VARCHAR(20)   NOT NULL UNIQUE,
    customer_id      UUID          REFERENCES users(id),        -- set for customer bookings
    merchant_id      UUID          REFERENCES merchants(id),    -- set for merchant API orders
    pickup_address   VARCHAR(500)  NOT NULL,
    dropoff_address  VARCHAR(500)  NOT NULL,
    dest_zone_id     INT           NOT NULL REFERENCES zones(id),
    recipient_name   VARCHAR(150)  NOT NULL,
    recipient_phone  VARCHAR(20)   NOT NULL,
    parcel_size      VARCHAR(10)   NOT NULL,     -- SMALL, MEDIUM, LARGE
    weight_kg        NUMERIC(6,2)  NOT NULL CHECK (weight_kg > 0),
    fee              NUMERIC(10,2) NOT NULL,
    status           VARCHAR(30)   NOT NULL,     -- CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED
    version          INT           NOT NULL DEFAULT 0,          -- optimistic locking
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (customer_id IS NOT NULL OR merchant_id IS NOT NULL)
);
CREATE INDEX idx_parcels_customer ON parcels(customer_id);
CREATE INDEX idx_parcels_merchant ON parcels(merchant_id);
CREATE INDEX idx_parcels_status   ON parcels(status);

CREATE TABLE assignments (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id    UUID        NOT NULL REFERENCES parcels(id),
    courier_id   UUID        NOT NULL REFERENCES couriers(id),
    assigned_by  UUID        NOT NULL REFERENCES users(id),
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    active       BOOLEAN     NOT NULL DEFAULT TRUE              -- FALSE after a reassignment
);
CREATE INDEX idx_assignments_courier ON assignments(courier_id) WHERE active;
-- A parcel can have only one active assignment at a time
CREATE UNIQUE INDEX uq_assignments_active_parcel ON assignments(parcel_id) WHERE active;

-- Append-only history of every status change
CREATE TABLE status_history (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id   UUID         NOT NULL REFERENCES parcels(id),
    old_status  VARCHAR(30),                     -- NULL for the first CREATED entry
    new_status  VARCHAR(30)  NOT NULL,
    changed_by  UUID         REFERENCES users(id),
    note        VARCHAR(500),
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_history_parcel ON status_history(parcel_id);

CREATE TABLE delivery_proofs (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id          UUID         NOT NULL REFERENCES parcels(id),
    status_history_id  UUID         NOT NULL REFERENCES status_history(id),
    recipient_name     VARCHAR(150),
    note               VARCHAR(500),
    photo_path         VARCHAR(500),
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Notifications and webhooks ----------
CREATE TABLE notifications (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         REFERENCES users(id),
    parcel_id   UUID         REFERENCES parcels(id),
    channel     VARCHAR(10)  NOT NULL,           -- EMAIL, SMS
    recipient   VARCHAR(255) NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)  NOT NULL,           -- SENT, FAILED
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID         NOT NULL REFERENCES merchants(id),
    parcel_id      UUID         NOT NULL REFERENCES parcels(id),
    event_id       UUID         NOT NULL,
    attempt_no     INT          NOT NULL,
    http_status    INT,
    result         VARCHAR(20)  NOT NULL,        -- SUCCESS, RETRY, DEAD
    error_message  VARCHAR(500),
    attempted_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_deliveries_merchant ON webhook_deliveries(merchant_id);

-- ---------- Reliable events (report section 4.4) ----------
-- Transactional outbox: written in the same transaction as the parcel change
CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID         NOT NULL,         -- parcel id
    event_type    VARCHAR(50)  NOT NULL,         -- ParcelStatusChanged
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ                    -- NULL until the relay sends it to RabbitMQ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

-- Idempotent consumers: one row per (event, consumer)
CREATE TABLE processed_events (
    event_id       UUID        NOT NULL,
    consumer_name  VARCHAR(50) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

-- ---------- Audit ----------
CREATE TABLE audit_logs (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     UUID         REFERENCES users(id),
    action       VARCHAR(50)  NOT NULL,          -- MERCHANT_APPROVED, ROLE_CHANGED, PARCEL_REASSIGNED ...
    target_type  VARCHAR(30)  NOT NULL,
    target_id    VARCHAR(50)  NOT NULL,
    old_value    VARCHAR(255),
    new_value    VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
