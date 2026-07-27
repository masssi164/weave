#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"
readonly BOOTSTRAP_ENV="${WORKSPACE}/.generated/bootstrap.env"

if [[ -f "${BOOTSTRAP_ENV}" ]]; then
  # shellcheck disable=SC1090
  source "${BOOTSTRAP_ENV}"
fi

[[ "${TF_VAR_deployment_environment:-dev}" == dev ]] || {
  printf 'WEAVE_DEV_DOWN_ERROR refusing to operate on non-dev environment %s\n' \
    "${TF_VAR_deployment_environment:-unset}" >&2
  exit 1
}

bash "${WORKSPACE}/compose.sh" dev down --remove-orphans
printf '%s\n' "WEAVE_DEV_DOWN_COMPLETE persistent provider volumes were preserved."
