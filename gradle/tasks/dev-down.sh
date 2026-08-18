#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"

bash "${WORKSPACE}/compose.sh" dev down --remove-orphans
printf '%s\n' "WEAVE_DEV_DOWN_COMPLETE local provider containers were removed."
