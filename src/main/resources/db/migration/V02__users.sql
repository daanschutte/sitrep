CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    rank       VARCHAR(16)  NOT NULL,
    version    BIGINT,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_users_email_normalised CHECK (email = lower(btrim(email)))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON users TO app_user;