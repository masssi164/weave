#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${WEAVE_PROFILE:-dev}"

case "${1:-}" in
  dev|test|prod) PROFILE="$1"; shift ;;
esac

args=("${PROFILE}" --root "${ROOT_DIR}")
[[ -z "${WEAVE_ENV_FILE:-}" ]] || args+=(--env-file "${WEAVE_ENV_FILE}")
[[ "${PROFILE}" == dev ]] || args+=(--require-application)

exec python3 "${ROOT_DIR}/scripts/operator_check.py" "${args[@]}" "$@"
