#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly PROFILE="${1:-dev}"

case "${PROFILE}" in
  dev|dogfood|prod|e2e) ;;
  *)
    printf 'WEAVE_INSTALL_ERROR environment must be dev, dogfood, prod, or e2e\n' >&2
    exit 2
    ;;
esac

if [[ "${PROFILE}" != "dev" && -z "${WEAVE_ENV_FILE:-}" ]]; then
  printf 'WEAVE_INSTALL_ERROR %s requires WEAVE_ENV_FILE pointing to reviewed public deployment inputs\n' "${PROFILE}" >&2
  exit 2
fi

"${ROOT_DIR}/compose.sh" "${PROFILE}" up
"${ROOT_DIR}/compose.sh" "${PROFILE}" identity-verify

printf 'install: %s exact-candidate stack is running and verified\n' "${PROFILE}"
