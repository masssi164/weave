#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR

if [[ $# -lt 2 ]]; then
  printf 'Usage: %s <dev|dogfood|prod|e2e> <secrets-init|render|configure|config|prepare|provider-prepare|keycloak-migration-apply|bootstrap-owner|up|down|reset|ps|logs|persistence-restart-proof|chat-provider-stop-proof|chat-provider-start-proof|collaboration-restart-proof> [args...]\n' "$0" >&2
  exit 2
fi

if [[ "$1" == "e2e" && "$2" == "keycloak-migration-apply" ]]; then
  [[ $# -eq 2 ]] || {
    printf 'WEAVE_COMPOSE_ERROR e2e keycloak-migration-apply does not accept command arguments\n' >&2
    exit 2
  }
  exec python3 "${ROOT_DIR}/scripts/keycloak_e2e_migration.py" \
    --root "${ROOT_DIR}" \
    --env-file "${WEAVE_ENV_FILE:?WEAVE_ENV_FILE is required for isolated E2E migration}"
fi

exec python3 "${ROOT_DIR}/scripts/compose_runtime.py" --root "${ROOT_DIR}" "$@"
