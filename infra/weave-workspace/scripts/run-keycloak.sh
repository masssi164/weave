#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

read_secret() {
  local variable="$1"
  local path="$2"
  [[ -f "${path}" && ! -L "${path}" ]] || {
    printf 'WEAVE_KEYCLOAK_START_ERROR missing mounted secret: %s\n' "${path}" >&2
    exit 1
  }
  declare -gx "${variable}=$(<"${path}")"
}

if [[ -f /run/secrets/keycloak-db-password ]]; then
  read_secret KC_DB_PASSWORD /run/secrets/keycloak-db-password
elif [[ "${KC_DB:-}" != "dev-file" ]]; then
  printf 'WEAVE_KEYCLOAK_START_ERROR missing mounted database secret outside dev-file mode\n' >&2
  exit 1
fi

readonly MIGRATION_SECRET=/run/secrets/keycloak-realm-migration-bootstrap-secret
if [[ "${1:-}" == "bootstrap-admin" ]]; then
  read_secret KC_BOOTSTRAP_ADMIN_CLIENT_SECRET "${MIGRATION_SECRET}"
elif [[ -e "${MIGRATION_SECRET}" || -L "${MIGRATION_SECRET}" ]]; then
  printf 'WEAVE_KEYCLOAK_START_ERROR temporary migration authority reached normal Keycloak startup\n' >&2
  exit 1
fi
unset KC_BOOTSTRAP_ADMIN_USERNAME KC_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD

exec /opt/keycloak/bin/kc.sh "$@"
