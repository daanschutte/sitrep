CREATE TABLE squadron_assignment
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    squadron_id UUID        NOT NULL REFERENCES squadron (id),
    role        VARCHAR(32) NOT NULL,
    is_current  BOOLEAN     NOT NULL DEFAULT TRUE,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    version     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_user_id_is_current ON squadron_assignment (user_id) WHERE is_current;

CREATE TABLE cross_squadron_grant
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    squadron_id UUID        NOT NULL REFERENCES squadron (id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,
    version     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
