#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
CREATE ROLE IF NOT EXISTS audit_writer NOLOGIN;
CREATE USER IF NOT EXISTS app_user WITH PASSWORD '$APP_USER_PASSWORD';
GRANT audit_writer TO app_user;
EOSQL