#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly PROFILE="${1:-dev}"

case "${PROFILE}" in
  dev|test|prod) ;;
  *)
    printf 'WEAVE_INSTALL_ERROR profile must be dev, test, or prod\n' >&2
    exit 2
    ;;
esac

if [[ "${PROFILE}" != "dev" && -z "${WEAVE_ENV_FILE:-}" ]]; then
  printf 'WEAVE_INSTALL_ERROR %s requires WEAVE_ENV_FILE pointing to reviewed public deployment inputs\n' "${PROFILE}" >&2
  exit 2
fi

"${ROOT_DIR}/compose.sh" "${PROFILE}" secrets-init
"${ROOT_DIR}/compose.sh" "${PROFILE}" render
"${ROOT_DIR}/compose.sh" "${PROFILE}" config >/dev/null
"${ROOT_DIR}/compose.sh" "${PROFILE}" prepare
"${ROOT_DIR}/compose.sh" "${PROFILE}" keycloak-apply
"${ROOT_DIR}/compose.sh" "${PROFILE}" up
"${ROOT_DIR}/compose.sh" "${PROFILE}" keycloak-verify

printf 'install: %s exact-candidate stack is running and verified\n' "${PROFILE}"
