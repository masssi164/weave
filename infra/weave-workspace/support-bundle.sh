#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROFILE="${WEAVE_PROFILE:-dev}"
OUTPUT_DIR=""

case "${1:-}" in
  dev|dogfood|prod|e2e) PROFILE="$1"; shift ;;
esac
if [[ $# -gt 0 ]]; then OUTPUT_DIR="$1"; shift; fi
[[ $# -eq 0 ]] || { printf 'WEAVE_SUPPORT_BUNDLE_ERROR unexpected arguments\n' >&2; exit 2; }

args=("${PROFILE}" --root "${ROOT_DIR}")
# The support manifest includes the public WEAVE_ADMIN_CONSOLE_URL and
# WEAVE_PROVIDER_PROFILE coordinates derived by the closed Compose context.
# Neither coordinate is a provider endpoint credential or SecretRef value.
[[ -z "${WEAVE_ENV_FILE:-}" ]] || args+=(--env-file "${WEAVE_ENV_FILE}")
[[ -z "${OUTPUT_DIR}" ]] || args+=(--output-dir "${OUTPUT_DIR}")

exec python3 "${ROOT_DIR}/scripts/support_bundle.py" "${args[@]}"
