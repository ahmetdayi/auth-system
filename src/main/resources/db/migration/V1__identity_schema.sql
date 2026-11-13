-- Identity bounded context: accounts, roles and sessions.
-- Ids come from sequences so aggregates have their identity before they are stored.

CREATE SEQUENCE role_id_seq;
CREATE SEQUENCE customer_id_seq;
CREATE SEQUENCE customer_address_id_seq;
CREATE SEQUENCE admin_id_seq;
CREATE SEQUENCE user_session_id_seq;

CREATE TABLE roles (
    id         BIGINT      PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name),
    -- Names reserved for authorities generated in code can never come from the database.
    CONSTRAINT ck_roles_name_format CHECK (name ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    CONSTRAINT ck_roles_name_not_reserved CHECK (
        name NOT IN ('ADMIN', 'SUPER_ADMIN')
        AND name NOT LIKE 'ROLE\_%' AND name NOT LIKE 'TYPE\_%' AND name NOT LIKE 'MEMBERSHIP\_%')
);

CREATE TABLE role_permissions (
    role_id         BIGINT       NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_name),
    CONSTRAINT ck_role_permissions_format CHECK (permission_name ~ '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$')
);

CREATE TABLE customers (
    id                   BIGINT       PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    loyalty_points       INTEGER      NOT NULL DEFAULT 0,
    status               VARCHAR(20)  NOT NULL,
    sessions_valid_after TIMESTAMPTZ  NOT NULL,
    registered_at        TIMESTAMPTZ  NOT NULL,
    membership_tier      VARCHAR(20),
    membership_starts_at TIMESTAMPTZ,
    membership_ends_at   TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_email_normalized CHECK (email = lower(email)),
    CONSTRAINT ck_customers_loyalty_points CHECK (loyalty_points >= 0),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_customers_membership_tier CHECK (membership_tier IN ('BRONZE', 'SILVER', 'GOLD')),
    CONSTRAINT ck_customers_membership_consistent CHECK (
        (membership_tier IS NULL AND membership_starts_at IS NULL AND membership_ends_at IS NULL)
        OR (membership_tier IS NOT NULL AND membership_starts_at IS NOT NULL
            AND (membership_ends_at IS NULL OR membership_ends_at > membership_starts_at)))
);

CREATE INDEX ix_customers_registered_at ON customers (registered_at);
CREATE INDEX ix_customers_last_name ON customers (last_name);

CREATE TABLE customer_roles (
    customer_id BIGINT NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (customer_id, role_id)
);

CREATE TABLE customer_addresses (
    id          BIGINT       PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    label       VARCHAR(40)  NOT NULL,
    city        VARCHAR(80)  NOT NULL,
    line        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL
);

CREATE INDEX ix_customer_addresses_customer ON customer_addresses (customer_id);

CREATE TABLE admins (
    id                   BIGINT       PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(200) NOT NULL,
    department           VARCHAR(100),
    level                VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    sessions_valid_after TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL,
    CONSTRAINT uk_admins_email UNIQUE (email),
    CONSTRAINT ck_admins_email_normalized CHECK (email = lower(email)),
    CONSTRAINT ck_admins_level CHECK (level IN ('STANDARD', 'SUPER')),
    CONSTRAINT ck_admins_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE admin_regions (
    admin_id BIGINT       NOT NULL REFERENCES admins (id) ON DELETE CASCADE,
    region   VARCHAR(100) NOT NULL,
    PRIMARY KEY (admin_id, region)
);

CREATE TABLE admin_permissions (
    admin_id        BIGINT       NOT NULL REFERENCES admins (id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (admin_id, permission_name),
    CONSTRAINT ck_admin_permissions_format CHECK (permission_name ~ '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$')
);

-- One row per login. owner_id points to customers or admins depending on owner_type, so there is no FK;
-- every query filters on (owner_type, owner_id) together.
CREATE TABLE user_sessions (
    id                 BIGINT       PRIMARY KEY,
    owner_type         VARCHAR(20)  NOT NULL,
    owner_id           BIGINT       NOT NULL,
    user_agent         VARCHAR(255) NOT NULL,
    ip_address         VARCHAR(45)  NOT NULL,
    started_at         TIMESTAMPTZ  NOT NULL,
    last_refreshed_at  TIMESTAMPTZ  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    current_token_hash VARCHAR(64)  NOT NULL,
    ended_at           TIMESTAMPTZ,
    end_reason         VARCHAR(40),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL,
    CONSTRAINT uk_user_sessions_current_token UNIQUE (current_token_hash),
    CONSTRAINT ck_user_sessions_owner_type CHECK (owner_type IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_user_sessions_end CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

CREATE INDEX ix_user_sessions_open_by_owner ON user_sessions (owner_type, owner_id, last_refreshed_at DESC)
    WHERE ended_at IS NULL;
CREATE INDEX ix_user_sessions_expires_at ON user_sessions (expires_at);

-- Refresh tokens replaced by rotation. Kept until the session is purged so reuse can be detected.
CREATE TABLE retired_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES user_sessions (id) ON DELETE CASCADE,
    retired_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL
);

CREATE INDEX ix_retired_refresh_tokens_session ON retired_refresh_tokens (session_id);
