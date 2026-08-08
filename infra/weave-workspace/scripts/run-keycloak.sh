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
elif [[ "${KC_DB:-}" != "dev-file" || "${1:-}" != "start-dev" ]]; then
  printf 'WEAVE_KEYCLOAK_START_ERROR missing mounted database secret outside dev-file startup\n' >&2
  exit 1
fi

if [[ -f /run/secrets/keycloak-bootstrap-admin-password ]]; then
  if [[ "${1:-}" == "bootstrap-admin" ]]; then
    # The offline recovery command receives only its explicit secret input.
    read_secret WEAVE_IDENTITY_OPS_BOOTSTRAP_SECRET /run/secrets/keycloak-bootstrap-admin-password
    unset KC_BOOTSTRAP_ADMIN_CLIENT_ID KC_BOOTSTRAP_ADMIN_CLIENT_SECRET
  else
    # Keycloak consumes these only while creating the first master realm.
    read_secret KC_BOOTSTRAP_ADMIN_CLIENT_SECRET /run/secrets/keycloak-bootstrap-admin-password
    export KC_BOOTSTRAP_ADMIN_CLIENT_ID=weave-identity-ops-bootstrap
  fi
fi
unset KC_BOOTSTRAP_ADMIN_USERNAME KC_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD

exec /opt/keycloak/bin/kc.sh "$@"
