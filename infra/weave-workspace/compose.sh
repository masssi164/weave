#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR

if [[ $# -lt 2 ]]; then
  printf 'Usage: %s <dev|dogfood|main> <secrets-init|render|config|prepare|provider-prepare|up|down|ps|logs|keycloak-plan|keycloak-apply|keycloak-verify> [args...]\n' "$0" >&2
  exit 2
fi

exec python3 "${ROOT_DIR}/scripts/compose_runtime.py" --root "${ROOT_DIR}" "$@"
