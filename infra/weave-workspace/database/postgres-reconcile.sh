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
read_secret WEAVE_BACKEND_DB_PASSWORD /run/secrets/backend-db-password WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_KEYCLOAK_DB_PASSWORD /run/secrets/keycloak-db-password WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_MAS_DB_PASSWORD /run/secrets/mas-db-password WEAVE_MAS_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_SYNAPSE_DB_PASSWORD /run/secrets/synapse-db-password WEAVE_SYNAPSE_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_NEXTCLOUD_DB_PASSWORD /run/secrets/nextcloud-db-password WEAVE_NEXTCLOUD_DB_PASSWORD_FINGERPRINT
read_secret WEAVE_CONTROL_DB_PASSWORD /run/secrets/control-db-password WEAVE_CONTROL_DB_PASSWORD_FINGERPRINT

psql --no-psqlrc --set=ON_ERROR_STOP=1 --quiet <<'SQL'
\getenv backend_name WEAVE_BACKEND_DB_NAME
\getenv backend_user WEAVE_BACKEND_DB_USERNAME
\getenv backend_password WEAVE_BACKEND_DB_PASSWORD
\getenv backend_fingerprint WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT
\getenv keycloak_name WEAVE_KEYCLOAK_DB_NAME
\getenv keycloak_user WEAVE_KEYCLOAK_DB_USERNAME
\getenv keycloak_password WEAVE_KEYCLOAK_DB_PASSWORD
\getenv keycloak_fingerprint WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT
\getenv mas_name WEAVE_MAS_DB_NAME
\getenv mas_user WEAVE_MAS_DB_USERNAME
\getenv mas_password WEAVE_MAS_DB_PASSWORD
\getenv mas_fingerprint WEAVE_MAS_DB_PASSWORD_FINGERPRINT
\getenv synapse_name WEAVE_SYNAPSE_DB_NAME
\getenv synapse_user WEAVE_SYNAPSE_DB_USERNAME
\getenv synapse_password WEAVE_SYNAPSE_DB_PASSWORD
\getenv synapse_fingerprint WEAVE_SYNAPSE_DB_PASSWORD_FINGERPRINT
\getenv nextcloud_name WEAVE_NEXTCLOUD_DB_NAME
\getenv nextcloud_user WEAVE_NEXTCLOUD_DB_USERNAME
\getenv nextcloud_password WEAVE_NEXTCLOUD_DB_PASSWORD
\getenv nextcloud_fingerprint WEAVE_NEXTCLOUD_DB_PASSWORD_FINGERPRINT
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
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'backend_user', :'backend_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I', :'backend_name', :'backend_user')
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
SELECT :'mas_user', :'mas_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'mas_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'mas_user', :'mas_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'mas_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'mas_user', :'mas_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'mas_user' AND secret_fingerprint = :'mas_fingerprint'
) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'mas_user', :'mas_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I', :'mas_name', :'mas_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'mas_name') \gexec

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'synapse_user', :'synapse_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'synapse_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'synapse_user', :'synapse_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'synapse_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'synapse_user', :'synapse_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'synapse_user' AND secret_fingerprint = :'synapse_fingerprint'
) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'synapse_user', :'synapse_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I TEMPLATE template0 LC_COLLATE ''C'' LC_CTYPE ''C''', :'synapse_name', :'synapse_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'synapse_name') \gexec

INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
SELECT :'nextcloud_user', :'nextcloud_fingerprint'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'nextcloud_user')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp();
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'nextcloud_user', :'nextcloud_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'nextcloud_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'nextcloud_user', :'nextcloud_password')
WHERE NOT EXISTS (
  SELECT 1 FROM weave_control.database_role_secret_generations
  WHERE role_name = :'nextcloud_user' AND secret_fingerprint = :'nextcloud_fingerprint'
) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'nextcloud_user', :'nextcloud_fingerprint')
ON CONFLICT (role_name) DO UPDATE SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I', :'nextcloud_name', :'nextcloud_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'nextcloud_name') \gexec

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

SELECT format('ALTER DATABASE %I OWNER TO %I', :'backend_name', :'backend_user') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'keycloak_name', :'keycloak_user') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'mas_name', :'mas_user') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'synapse_name', :'synapse_user') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'nextcloud_name', :'nextcloud_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'backend_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'keycloak_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'mas_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'synapse_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'nextcloud_name') \gexec

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

unset PGPASSWORD WEAVE_BACKEND_DB_PASSWORD WEAVE_KEYCLOAK_DB_PASSWORD \
  WEAVE_MAS_DB_PASSWORD WEAVE_SYNAPSE_DB_PASSWORD WEAVE_NEXTCLOUD_DB_PASSWORD \
  WEAVE_CONTROL_DB_PASSWORD WEAVE_POSTGRES_ADMIN_PASSWORD_FINGERPRINT \
  WEAVE_BACKEND_DB_PASSWORD_FINGERPRINT WEAVE_KEYCLOAK_DB_PASSWORD_FINGERPRINT \
  WEAVE_MAS_DB_PASSWORD_FINGERPRINT WEAVE_SYNAPSE_DB_PASSWORD_FINGERPRINT \
  WEAVE_NEXTCLOUD_DB_PASSWORD_FINGERPRINT WEAVE_CONTROL_DB_PASSWORD_FINGERPRINT

printf '%s\n' 'postgres-reconcile: converged'
