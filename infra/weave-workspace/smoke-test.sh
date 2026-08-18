#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${WEAVE_PROFILE:-dev}"

case "${1:-}" in
  dev|dogfood|prod|e2e) PROFILE="$1"; shift ;;
esac

args=("${PROFILE}" --root "${ROOT_DIR}")
[[ -z "${WEAVE_ENV_FILE:-}" ]] || args+=(--env-file "${WEAVE_ENV_FILE}")
[[ "${PROFILE}" == dev ]] || args+=(--require-application)
if [[ -n "${WEAVE_MEMBER_ACCESS_TOKEN_FILE:-}" ]]; then
  # Checking admin API protection with a member token is a credentialed E2E
  # gate. The token is read by the Python probe from a mode-0600 file and is
  # sent through curl stdin, never argv, logs, evidence, or support output.
  printf 'Checking admin API protection with a member token at /admin/control-plane\n'
  args+=(--member-access-token-file "${WEAVE_MEMBER_ACCESS_TOKEN_FILE}")
fi

exec python3 "${ROOT_DIR}/scripts/operator_check.py" "${args[@]}" "$@"
