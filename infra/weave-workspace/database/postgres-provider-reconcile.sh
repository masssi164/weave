#!/bin/sh

set -eu

fail() {
  printf 'WEAVE_POSTGRES_PROVIDER_RECONCILE_ERROR %s\n' "$*" >&2
  exit 1
}

read_secret() {
  path="$1"
  [ -f "${path}" ] && [ ! -L "${path}" ] || fail "missing mounted provider database SecretRef"
  value="$(cat "${path}")"
  [ -n "${value}" ] || fail "empty mounted provider database SecretRef"
  fingerprint="$(sha256sum "${path}" | awk '{print $1}')"
  case "${fingerprint}" in
    *[!0-9a-f]*|'') fail "provider database SecretRef fingerprint is invalid" ;;
  esac
  [ "${#fingerprint}" -eq 64 ] || fail "provider database SecretRef fingerprint is invalid"
  PROVIDER_DB_PASSWORD="${value}"
  PROVIDER_DB_FINGERPRINT="sha256:${fingerprint}"
  export PROVIDER_DB_PASSWORD PROVIDER_DB_FINGERPRINT
  unset value fingerprint
}

reconcile_database() {
  PROVIDER_DB_NAME="$1"
  PROVIDER_DB_USERNAME="$2"
  password_path="$3"
  collation="$4"
  export PROVIDER_DB_NAME PROVIDER_DB_USERNAME
  read_secret "${password_path}"
  case "${collation}" in
    default) database_suffix="" ;;
    c) database_suffix=" TEMPLATE template0 LC_COLLATE 'C' LC_CTYPE 'C'" ;;
    *) fail "unsupported provider database collation" ;;
  esac
  export PROVIDER_DB_SUFFIX="${database_suffix}"
  psql --no-psqlrc --set=ON_ERROR_STOP=1 --quiet <<'SQL'
\getenv provider_name PROVIDER_DB_NAME
\getenv provider_user PROVIDER_DB_USERNAME
\getenv provider_password PROVIDER_DB_PASSWORD
\getenv provider_fingerprint PROVIDER_DB_FINGERPRINT
\getenv provider_suffix PROVIDER_DB_SUFFIX

SELECT 1 FROM weave_control.database_role_secret_generations LIMIT 1;
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'provider_user', :'provider_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'provider_user') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'provider_user', :'provider_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'provider_user')
  AND NOT EXISTS (
    SELECT 1 FROM weave_control.database_role_secret_generations
    WHERE role_name = :'provider_user' AND secret_fingerprint = :'provider_fingerprint'
  ) \gexec
INSERT INTO weave_control.database_role_secret_generations (role_name, secret_fingerprint)
VALUES (:'provider_user', :'provider_fingerprint')
ON CONFLICT (role_name) DO UPDATE
SET secret_fingerprint = EXCLUDED.secret_fingerprint, applied_at = clock_timestamp()
WHERE weave_control.database_role_secret_generations.secret_fingerprint IS DISTINCT FROM EXCLUDED.secret_fingerprint;
SELECT format('CREATE DATABASE %I OWNER %I%s', :'provider_name', :'provider_user', :'provider_suffix')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'provider_name') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'provider_name', :'provider_user') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'provider_name') \gexec
SQL
  unset PROVIDER_DB_NAME PROVIDER_DB_USERNAME PROVIDER_DB_PASSWORD \
    PROVIDER_DB_FINGERPRINT PROVIDER_DB_SUFFIX database_suffix password_path
}

read_secret /run/secrets/postgres-admin-password
PGPASSWORD="${PROVIDER_DB_PASSWORD}"
export PGPASSWORD
unset PROVIDER_DB_PASSWORD PROVIDER_DB_FINGERPRINT

case "${WEAVE_PROVIDER_DATABASE_SET:-}" in
  matrix)
    reconcile_database "${WEAVE_MAS_DB_NAME}" "${WEAVE_MAS_DB_USERNAME}" /run/secrets/mas-db-password default
    reconcile_database "${WEAVE_SYNAPSE_DB_NAME}" "${WEAVE_SYNAPSE_DB_USERNAME}" /run/secrets/synapse-db-password c
    ;;
  nextcloud)
    reconcile_database "${WEAVE_NEXTCLOUD_DB_NAME}" "${WEAVE_NEXTCLOUD_DB_USERNAME}" /run/secrets/nextcloud-db-password default
    ;;
  *) fail "provider database set must be matrix or nextcloud" ;;
esac

unset PGPASSWORD
printf '%s\n' 'postgres-provider-reconcile: converged'
