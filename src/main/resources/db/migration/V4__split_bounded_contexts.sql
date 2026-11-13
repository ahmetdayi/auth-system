-- Splits the original single "identity" model into bounded contexts. Existing rows are carried over.
--
--   identity   : accounts (customer AND admin logins), account_roles, account_permissions, user_sessions
--   customer   : customer_profiles, customer_addresses
--   membership : memberships
--   staff      : admin_profiles, admin_profile_regions
--
-- Accounts share one id space (AccountId is a shared-kernel type). Customers keep their ids; admins get new
-- ids after them. V1..V3 are not edited: an applied migration is history, changes go into a new one.

-- ------------------------------------------------------------------------------------------- id space
CREATE SEQUENCE account_id_seq;

DO $$
DECLARE
    start_after BIGINT;
BEGIN
    SELECT GREATEST(
               COALESCE((SELECT max(id) FROM customers), 0),
               (SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM customer_id_seq))
      INTO start_after;
    IF start_after > 0 THEN
        PERFORM setval('account_id_seq', start_after, true);
    END IF;
END $$;

CREATE TEMPORARY TABLE admin_account_ids ON COMMIT DROP AS
SELECT ordered.admin_id, nextval('account_id_seq') AS account_id
FROM (SELECT id AS admin_id FROM admins ORDER BY id) AS ordered;

-- ------------------------------------------------------------------------------------------- identity
CREATE TABLE accounts (
    id                   BIGINT       PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    account_type         VARCHAR(20)  NOT NULL,
    admin_level          VARCHAR(20),
    status               VARCHAR(20)  NOT NULL,
    sessions_valid_after TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL,
    -- A person may have a customer and an admin account with the same address; logins name the type.
    CONSTRAINT uk_accounts_email_type UNIQUE (email, account_type),
    CONSTRAINT ck_accounts_email_normalized CHECK (email = lower(email)),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- Every admin has a level, no customer has one.
    CONSTRAINT ck_accounts_admin_level CHECK (
        (account_type = 'ADMIN' AND admin_level IN ('STANDARD', 'SUPER'))
        OR (account_type = 'CUSTOMER' AND admin_level IS NULL))
);

INSERT INTO accounts (id, email, password_hash, account_type, admin_level, status, sessions_valid_after,
                      created_at, updated_at, version)
SELECT id, email, password_hash, 'CUSTOMER', NULL, status, sessions_valid_after, registered_at, updated_at, version
FROM customers;

INSERT INTO accounts (id, email, password_hash, account_type, admin_level, status, sessions_valid_after,
                      created_at, updated_at, version)
SELECT m.account_id, a.email, a.password_hash, 'ADMIN', a.level, a.status, a.sessions_valid_after, a.created_at,
       a.updated_at, a.version
FROM admins a JOIN admin_account_ids m ON m.admin_id = a.id;

CREATE TABLE account_roles (
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role_id    BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (account_id, role_id)
);

INSERT INTO account_roles (account_id, role_id)
SELECT customer_id, role_id FROM customer_roles;

-- Permissions granted to one account directly (admins), in addition to those of its roles.
CREATE TABLE account_permissions (
    account_id      BIGINT       NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    permission_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (account_id, permission_name),
    CONSTRAINT ck_account_permissions_format CHECK (permission_name ~ '^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$')
);

INSERT INTO account_permissions (account_id, permission_name)
SELECT m.account_id, p.permission_name
FROM admin_permissions p JOIN admin_account_ids m ON m.admin_id = p.admin_id;

-- Sessions point at the account instead of (owner_type, owner_id).
ALTER TABLE user_sessions ADD COLUMN account_id BIGINT;
UPDATE user_sessions SET account_id = owner_id WHERE owner_type = 'CUSTOMER';
UPDATE user_sessions s SET account_id = m.account_id
FROM admin_account_ids m WHERE s.owner_type = 'ADMIN' AND s.owner_id = m.admin_id;
DELETE FROM user_sessions WHERE account_id IS NULL;   -- owner no longer exists; retired tokens cascade
ALTER TABLE user_sessions ALTER COLUMN account_id SET NOT NULL;
ALTER TABLE user_sessions
    ADD CONSTRAINT fk_user_sessions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE;
DROP INDEX ix_user_sessions_open_by_owner;
ALTER TABLE user_sessions DROP CONSTRAINT ck_user_sessions_owner_type;
ALTER TABLE user_sessions DROP COLUMN owner_type, DROP COLUMN owner_id;
CREATE INDEX ix_user_sessions_open_by_account ON user_sessions (account_id, last_refreshed_at DESC)
    WHERE ended_at IS NULL;

-- ------------------------------------------------------------------------------------------- customer
-- The foreign key to accounts is a database-level guarantee only; the code holds a scalar id (rule 5.2).
CREATE TABLE customer_profiles (
    account_id     BIGINT       PRIMARY KEY REFERENCES accounts (id),
    contact_email  VARCHAR(254) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    loyalty_points INTEGER      NOT NULL DEFAULT 0,
    registered_at  TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL,
    CONSTRAINT ck_customer_profiles_email_normalized CHECK (contact_email = lower(contact_email)),
    CONSTRAINT ck_customer_profiles_loyalty_points CHECK (loyalty_points >= 0)
);

CREATE INDEX ix_customer_profiles_registered_at ON customer_profiles (registered_at);
CREATE INDEX ix_customer_profiles_last_name ON customer_profiles (last_name);
CREATE INDEX ix_customer_profiles_contact_email ON customer_profiles (contact_email);

INSERT INTO customer_profiles (account_id, contact_email, first_name, last_name, loyalty_points, registered_at,
                               created_at, updated_at, version)
SELECT id, email, first_name, last_name, loyalty_points, registered_at, created_at, updated_at, version
FROM customers;

ALTER TABLE customer_addresses DROP CONSTRAINT customer_addresses_customer_id_fkey;
ALTER TABLE customer_addresses
    ADD CONSTRAINT fk_customer_addresses_profile FOREIGN KEY (customer_id)
        REFERENCES customer_profiles (account_id) ON DELETE CASCADE;

-- ----------------------------------------------------------------------------------------- membership
CREATE TABLE memberships (
    account_id BIGINT      PRIMARY KEY REFERENCES accounts (id),
    tier       VARCHAR(20) NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL,
    CONSTRAINT ck_memberships_tier CHECK (tier IN ('BRONZE', 'SILVER', 'GOLD')),
    CONSTRAINT ck_memberships_period CHECK (ends_at IS NULL OR ends_at > starts_at)
);

INSERT INTO memberships (account_id, tier, starts_at, ends_at, created_at, updated_at, version)
SELECT id, membership_tier, membership_starts_at, membership_ends_at, created_at, updated_at, 0
FROM customers WHERE membership_tier IS NOT NULL;

-- ---------------------------------------------------------------------------------------------- staff
CREATE TABLE admin_profiles (
    account_id BIGINT       PRIMARY KEY REFERENCES accounts (id),
    full_name  VARCHAR(200) NOT NULL,
    department VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL
);

CREATE TABLE admin_profile_regions (
    account_id BIGINT       NOT NULL REFERENCES admin_profiles (account_id) ON DELETE CASCADE,
    region     VARCHAR(100) NOT NULL,
    PRIMARY KEY (account_id, region)
);

INSERT INTO admin_profiles (account_id, full_name, department, created_at, updated_at, version)
SELECT m.account_id, a.full_name, a.department, a.created_at, a.updated_at, 0
FROM admins a JOIN admin_account_ids m ON m.admin_id = a.id;

INSERT INTO admin_profile_regions (account_id, region)
SELECT m.account_id, r.region FROM admin_regions r JOIN admin_account_ids m ON m.admin_id = r.admin_id;

-- -------------------------------------------------------------------------------- old tables removed
DROP TABLE customer_roles;
DROP TABLE admin_permissions;
DROP TABLE admin_regions;
DROP TABLE admins;
DROP TABLE customers;
DROP SEQUENCE customer_id_seq;
DROP SEQUENCE admin_id_seq;
