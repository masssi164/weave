#!/bin/sh

set -eu

fail() {
  printf 'WEAVE_POSTGRES_RECONCILE_ERROR %s\n' "$*" >&2
  exit 1
}

read_secret() {
  variable="$1"
  path="$2"
  fingerprint_variable="$3"
  [ -f "${path}" ] && [ ! -L "${path}" ] || fail "missing mounted secret ${path}"
  value="$(cat "${path}")"
  [ -n "${value}" ] || fail "empty mounted secret ${path}"
  fingerprint="$(sha256sum "${path}" | awk '{print $1}')"
  case "${fingerprint}" in
    *[!0-9a-f]*|'') fail "could not fingerprint mounted secret ${path}" ;;
  esac
  [ "${#fingerprint}" -eq 64 ] || fail "invalid secret-generation fingerprint for ${path}"
  export "${variable}=${value}"
  export "${fingerprint_variable}=sha256:${fingerprint}"
  unset value fingerprint
}

read_secret PGPASSWORD /run/secrets/postgres-admin-password WEAVE_POSTGRES_ADMIN_PASSWORD_FINGERPRINT
read_secret WEAVE_BACKEND_MIGRATOR_DB_PASSWORD /run/secrets/backend-migrator-db-password WEAVE_BACKEND_MIGRATOR_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_BACKEND_DB_PASSWORD /run/secrets/backend-db-password WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_KEYCLOAK_DB_PASSWORD /run/secrets/keycloak-db-password WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_CONTROL_DB_PASSWORD /run/secrets/control-db-password WEAVE_CONTROL_DB_PASSWORD_FINGERPRINT

[ "${WEAVE_BACKEND_MIGRATOR_DB_USERNAME}" != "${WEAVE_BACKEND_DB_USERNAME}" ] ||
  fail "backend migrator and serving database roles must be distinct"
[ "${WEAVE_BACKEND_MIGRATOR_DB_USERNAME}" != "${PGUSER}" ] ||
  fail "backend migrator must be distinct from the PostgreSQL administrator"

psql --no-psqlrc --set=ON_ERROR_STOP=1 --quiet <<'SQL'
\getenv backend_name WEAVE_BACKEND_DB_NAME
\getenv backend_migrator_user WEAVE_BACKEND_MIGRATOR_DB_USERNAME
\getenv backend_migrator_password WEAVE_BACKEND_MIGRATOR_DB_PASSWORD
\getenv backend_migrator_fingerprint WEAVE_BACKEND_MIGRATOR_DB_PASSWORD_FINGERPRINT
\getenv backend_user WEAVE_BACKEND_DB_USERNAME
\getenv backend_password WEAVE_BACKEND_DB_PASSWORD
\getenv backend_fingerprint WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT
\getenv keycloak_name WEAVE_KEYCLOAK_DB_NAME
\getenv keycloak_user WEAVE_KEYCLOAK_DB_USERNAME
\getenv keycloak_password WEAVE_KEYCLOAK_DB_PASSWORD
\getenv keycloak_fingerprint WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT
\getenv control_user WEAVE_CONTROL_DB_USERNAME
\getenv control_password WEAVE_CONTROL_DB_PASSWORD
\getenv control_fingerprint WEAVE_CONTROL_DB_PASSWORD_FINGERPRINT

CREATE SCHEMA IF NOT EXISTS weave_control;
REVOKE ALL ON SCHEMA weave_control FROM PUBLIC;
CREATE TABLE IF NOT EXISTS weave_control.database_role_secret_generations (
  role_name text PRIMARY KEY,
  secret_fingerprint char(71) NOT NULL CHECK (secret_fingerprint ~ '^sha256:[0-9a-f]{64}$'),
  applied_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE ALL ON TABLE weave_control.database_role_secret_generations FROM PUBLIC;

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'backend_migrator_user', :'backend_migrator_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'backend_migrator_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
  :'backend_migrator_user', :'backend_migrator_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'backend_migrator_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
              :'backend_migrator_user', :'backend_migrator_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'backend_migrator_user'
    AND secret_fingerprint = :'backend_migrator_fingerprint'
) \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
              :'backend_migrator_user') \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'backend_migrator_user', :'backend_migrator_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'backend_user', :'backend_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'backend_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'backend_user', :'backend_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'backend_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'backend_user', :'backend_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'backend_user' AND secret_fingerprint = :'backend_fingerprint'
) \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
              :'backend_user') \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'backend_user', :'backend_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I', :'backend_name', :'backend_migrator_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'backend_name') \gexec

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'keycloak_user', :'keycloak_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'keycloak_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'keycloak_user', :'keycloak_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'keycloak_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'keycloak_user', :'keycloak_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'keycloak_user' AND secret_fingerprint = :'keycloak_fingerprint'
) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'keycloak_user', :'keycloak_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I', :'keycloak_name', :'keycloak_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'keycloak_name') \gexec

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'control_user', :'control_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'control_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'control_user', :'control_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'control_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'control_user', :'control_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'control_user' AND secret_fingerprint = :'control_fingerprint'
) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'control_user', :'control_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;

SELECT format('ALTER DATABASE %I OWNER TO %I', :'backend_name', :'backend_migrator_user') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'keycloak_name', :'keycloak_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'backend_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'keycloak_name') \gexec

ALTER SCHEMA weave_control OWNER TO :"control_user";
REVOKE ALL ON SCHEMA weave_control FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA weave_control TO :"control_user";

CREATE TABLE IF NOT EXISTS weave_control.keycloak_reconciliation_leases (
  lock_key text PRIMARY KEY,
  deployment_scope text NOT NULL,
  deployment_instance text NOT NULL,
  compose_project text NOT NULL,
  database_fingerprint text NOT NULL,
  realm text NOT NULL,
  lease_id text,
  reconciliation_id text,
  fencing_token bigint NOT NULL DEFAULT 0 CHECK (fencing_token >= 0),
  acquired_at timestamptz,
  expires_at timestamptz,
  released_at timestamptz,
  quarantined_at timestamptz,
  status text NOT NULL DEFAULT 'released' CHECK (status IN ('active', 'released', 'quarantined')),
  validation_count bigint NOT NULL DEFAULT 0 CHECK (validation_count >= 0),
  stale_fence_rejections bigint NOT NULL DEFAULT 0 CHECK (stale_fence_rejections >= 0),
  CHECK ((status = 'active' AND lease_id IS NOT NULL AND released_at IS NULL AND quarantined_at IS NULL)
      OR (status = 'released' AND quarantined_at IS NULL)
      OR (status = 'quarantined' AND released_at IS NULL AND quarantined_at IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS keycloak_reconciliation_active_lease
  ON weave_control.keycloak_reconciliation_leases (deployment_scope, deployment_instance, database_fingerprint, realm)
  WHERE status = 'active';

CREATE TABLE IF NOT EXISTS weave_control.keycloak_reconciliation_consumptions (
  reconciliation_id text PRIMARY KEY,
  request_nonce text NOT NULL UNIQUE,
  specification_commit char(40) NOT NULL,
  candidate_commit char(40) NOT NULL,
  receipt_payload_digest char(71) NOT NULL,
  accepted_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS weave_control.keycloak_tombstone_consumptions (
  authorization_evidence_ref text PRIMARY KEY,
  backup_receipt_ref text NOT NULL,
  candidate_commit char(40) NOT NULL,
  resource_key text NOT NULL,
  observed_fingerprint char(71) NOT NULL,
  consumed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (backup_receipt_ref, candidate_commit, resource_key, observed_fingerprint)
);

ALTER TABLE weave_control.keycloak_reconciliation_leases OWNER TO :"control_user";
ALTER TABLE weave_control.keycloak_reconciliation_consumptions OWNER TO :"control_user";
ALTER TABLE weave_control.keycloak_tombstone_consumptions OWNER TO :"control_user";
ALTER TABLE weave_control.database_role_secret_generations OWNER TO :"control_user";
REVOKE ALL ON ALL TABLES IN SCHEMA weave_control FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA weave_control TO :"control_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA weave_control REVOKE ALL ON TABLES FROM PUBLIC;
SQL

PGDATABASE="${WEAVE_BACKEND_DB_NAME}" psql --no-psqlrc --set=ON_ERROR_STOP=1 --quiet <<'SQL'
\getenv backend_migrator_user WEAVE_BACKEND_MIGRATOR_DB_USERNAME
\getenv backend_user WEAVE_BACKEND_DB_USERNAME

SELECT format('REASSIGN OWNED BY %I TO %I', :'backend_user', :'backend_migrator_user') \gexec
ALTER SCHEMA public OWNER TO :"backend_migrator_user";
SELECT format('REVOKE ALL PRIVILEGES ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I', current_database(), :'backend_user') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'backend_user') \gexec
REVOKE ALL PRIVILEGES ON SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM :"backend_user";
GRANT USAGE ON SCHEMA public TO :"backend_user";
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM :"backend_user";
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM :"backend_user";
SQL

unset PGPASSWORD WEAVE_BACKEND_MIGRATOR_DB_PASSWORD WEAVE_BACKEND_DB_PASSWORD WEAVE_KEYCLOAK_DB_PASSWORD \
  WEAVE_CONTROL_DB_PASSWORD WEAVE_POSTGRES_ADMIN_PASSWORD_FINGERPRINT \
  WEAVE_BACKEND_MIGRATOR_DB_PASSWORD_FINGERPRINT WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT \
  WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT \
  WEAVE_CONTROL_DB_PASSWORD_FINGERPRINT

printf '%s\n' 'postgres-reconcile: converged'
