-- SUPPORT account type + consent-based support grants (acting-as sessions).

ALTER TABLE accounts DROP CONSTRAINT ck_accounts_type;
ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_type CHECK (account_type IN ('CUSTOMER', 'ADMIN', 'SUPPORT'));

ALTER TABLE accounts DROP CONSTRAINT ck_accounts_admin_level;
ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_admin_level CHECK (
        (account_type = 'ADMIN' AND admin_level IN ('STANDARD', 'SUPER'))
        OR (account_type IN ('CUSTOMER', 'SUPPORT') AND admin_level IS NULL)
    );

CREATE SEQUENCE support_grant_id_seq;
CREATE SEQUENCE support_grant_extension_id_seq;

CREATE TABLE support_grants (
    id               BIGINT       PRIMARY KEY,
    agent_id         BIGINT       NOT NULL REFERENCES accounts (id),
    customer_id      BIGINT       NOT NULL REFERENCES accounts (id),
    status           VARCHAR(32)  NOT NULL,
    scopes           TEXT         NOT NULL,
    initial_minutes  INT          NOT NULL,
    extension_count  INT          NOT NULL,
    expires_at       TIMESTAMPTZ,
    requested_at     TIMESTAMPTZ  NOT NULL,
    started_at       TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    end_reason       VARCHAR(32),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL,
    CONSTRAINT ck_support_grants_status CHECK (status IN (
        'PENDING', 'ACTIVE', 'PENDING_EXTENSION', 'ENDED', 'EXPIRED', 'DENIED')),
    CONSTRAINT ck_support_grants_end_reason CHECK (
        end_reason IS NULL OR end_reason IN ('CUSTOMER_ENDED', 'AGENT_ENDED', 'EXPIRED', 'DENIED')),
    CONSTRAINT ck_support_grants_minutes CHECK (initial_minutes > 0 AND extension_count >= 0)
);

CREATE INDEX ix_support_grants_agent ON support_grants (agent_id, requested_at DESC);
CREATE INDEX ix_support_grants_customer ON support_grants (customer_id, requested_at DESC);
CREATE UNIQUE INDEX uk_support_grants_customer_live
    ON support_grants (customer_id)
    WHERE status IN ('PENDING', 'ACTIVE', 'PENDING_EXTENSION');

CREATE TABLE support_grant_extensions (
    id                 BIGINT       PRIMARY KEY,
    grant_id           BIGINT       NOT NULL REFERENCES support_grants (id) ON DELETE CASCADE,
    requested_minutes  INT          NOT NULL,
    requested_at       TIMESTAMPTZ  NOT NULL,
    decision           VARCHAR(20)  NOT NULL,
    decided_at         TIMESTAMPTZ,
    CONSTRAINT ck_support_ext_decision CHECK (decision IN ('PENDING', 'APPROVED', 'DENIED')),
    CONSTRAINT ck_support_ext_minutes CHECK (requested_minutes > 0)
);

CREATE INDEX ix_support_grant_extensions_grant ON support_grant_extensions (grant_id, requested_at);
