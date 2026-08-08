#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR

if [[ $# -lt 2 ]]; then
  printf 'Usage: %s <dev|dogfood|prod|e2e> <secrets-init|render|configure|config|prepare|provider-prepare|keycloak-migration-apply|bootstrap-owner|up|down|ps|logs|persistence-restart-proof|chat-provider-stop-proof|chat-provider-start-proof|collaboration-restart-proof> [args...]\n' "$0" >&2
  exit 2
fi

exec python3 "${ROOT_DIR}/scripts/compose_runtime.py" --root "${ROOT_DIR}" "$@"
