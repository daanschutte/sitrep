CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    firstName  VARCHAR(100) NOT NULL,
    lastName   VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    version    INTEGER,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);