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

if [[ "${PROFILE}" == "dev" ]]; then
  printf 'WEAVE_INSTALL_ERROR the protected post-import Keycloak migration is not a dev lifecycle operation\n' >&2
  exit 1
fi

if [[ -z "${WEAVE_ENV_FILE:-}" ]]; then
  printf 'WEAVE_INSTALL_ERROR %s requires WEAVE_ENV_FILE pointing to its reviewed or generated deployment inputs\n' "${PROFILE}" >&2
  exit 2
fi

if [[ "${PROFILE}" == "e2e" && -z "${WEAVE_E2E_EMPTY_NAMESPACE_PROOF:-}" ]]; then
  printf 'WEAVE_INSTALL_ERROR e2e requires WEAVE_E2E_EMPTY_NAMESPACE_PROOF from the pre-resource namespace check\n' >&2
  exit 1
fi

"${ROOT_DIR}/compose.sh" "${PROFILE}" keycloak-migration-apply
docker compose \
  --env-file "${ROOT_DIR}/.env.${PROFILE}" \
  up -d --remove-orphans --wait --wait-timeout 600

printf 'install: %s exact-candidate stack is running with a verified Keycloak migration receipt\n' "${PROFILE}"
