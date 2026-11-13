-- Transactional outbox (rule 6.2). An integration event is written in the same transaction as the change
-- that caused it; a relay delivers it afterwards. A crash between commit and delivery cannot lose it.

CREATE SEQUENCE outbox_event_id_seq;

CREATE TABLE outbox_events (
    id              BIGINT        PRIMARY KEY,
    event_type      VARCHAR(200)  NOT NULL,
    payload         TEXT          NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL,
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

-- The relay only ever looks at pending rows that are due.
CREATE INDEX ix_outbox_events_pending ON outbox_events (next_attempt_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
