CREATE TABLE squadron
(
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) UNIQUE NOT NULL,
    short_name VARCHAR(16) UNIQUE,
    is_active  BOOLEAN             NOT NULL DEFAULT TRUE,
    version    BIGINT,
    created_at TIMESTAMPTZ         NOT NULL,
    updated_at TIMESTAMPTZ         NOT NULL
);