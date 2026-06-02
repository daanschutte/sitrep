#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
CREATE ROLE audit_writer NOLOGIN;
CREATE USER app_user WITH PASSWORD '$APP_USER_PASSWORD';
GRANT audit_writer TO app_user;
GRANT SET ON ROLE audit_writer TO app_user;
EOSQL