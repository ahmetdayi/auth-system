-- Extensions are persisted by a JPA entity like every other table (audit timestamps + optimistic version).
-- V7 is not edited: an applied migration is history, changes go into a new one.

ALTER TABLE support_grant_extensions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

ALTER TABLE support_grant_extensions
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN version DROP DEFAULT;
