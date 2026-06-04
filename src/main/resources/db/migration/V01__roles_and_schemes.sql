CREATE SCHEMA IF NOT EXISTS audit;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_writer') THEN
            CREATE ROLE audit_writer NOLOGIN;
        END IF;
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
            CREATE ROLE app_user LOGIN;
        END IF;
    END
$$;

GRANT audit_writer TO app_user;
GRANT USAGE ON SCHEMA audit TO app_user;
