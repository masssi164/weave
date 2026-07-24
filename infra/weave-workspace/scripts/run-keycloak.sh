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

read_secret KC_DB_PASSWORD /run/secrets/keycloak-db-password
read_secret WEAVE_IDENTITY_EVENTS_HMAC_SECRET /run/secrets/identity-events-hmac-secret

if [[ -f /run/secrets/keycloak-bootstrap-admin-password ]]; then
  read_secret WEAVE_IDENTITY_OPS_BOOTSTRAP_SECRET /run/secrets/keycloak-bootstrap-admin-password
fi
unset KC_BOOTSTRAP_ADMIN_USERNAME KC_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD

exec /opt/keycloak/bin/kc.sh "$@"
