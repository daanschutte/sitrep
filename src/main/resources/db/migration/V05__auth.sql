CREATE TABLE credential
(
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL UNIQUE REFERENCES users (id),
    password_hash TEXT        NOT NULL,
    role          VARCHAR(16) NOT NULL,
    version       BIGINT,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE activation_token
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    role        VARCHAR(16) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    version     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_activation_token_live_per_user
    ON activation_token (user_id) WHERE consumed_at IS NULL AND revoked_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON credential, activation_token TO app_user;
