-- Content bounded context.

CREATE SEQUENCE article_id_seq;

CREATE TABLE articles (
    id            BIGINT       PRIMARY KEY,
    title         VARCHAR(150) NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    body          TEXT         NOT NULL,
    required_tier VARCHAR(20)  NOT NULL,
    published_at  TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT ck_articles_required_tier CHECK (required_tier IN ('BRONZE', 'SILVER', 'GOLD'))
);

CREATE INDEX ix_articles_published_at ON articles (published_at DESC);
CREATE INDEX ix_articles_tier_published ON articles (required_tier, published_at DESC);
