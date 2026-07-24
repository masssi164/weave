#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${1:-}"
[[ "${PROFILE}" == test ]] || {
  printf 'WEAVE_TEARDOWN_ERROR only an explicit test --isolated teardown is supported\n' >&2
  exit 2
}
shift

args=("${PROFILE}" --root "${ROOT_DIR}")
[[ -z "${WEAVE_ENV_FILE:-}" ]] || args+=(--env-file "${WEAVE_ENV_FILE}")

exec python3 "${ROOT_DIR}/scripts/teardown_compose.py" "${args[@]}" "$@"
