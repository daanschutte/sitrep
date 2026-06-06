CREATE TABLE squadron_assignment
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    squadron_id UUID        NOT NULL REFERENCES squadron (id),
    role        VARCHAR(32) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    version     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_user_current_squadron_assignment
    ON squadron_assignment (user_id) WHERE ended_at IS NULL;

CREATE TABLE squadron_guest_access
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    squadron_id UUID        NOT NULL REFERENCES squadron (id),
    role        VARCHAR(32) NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,
    version     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
