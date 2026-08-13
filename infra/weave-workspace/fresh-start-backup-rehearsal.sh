#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly PROFILE="${1:-dogfood}"
if [[ $# -gt 0 ]]; then
  shift
fi

# Persistent deployment images may deliberately run under emulation, but the
# backup and isolated restore helpers are local recovery tools. Let Docker use
# the runner's native platform so a locally cached arm64 manifest is not
# replaced by the workflow-wide amd64 default under the same immutable digest.
unset DOCKER_DEFAULT_PLATFORM

exec python3 "${ROOT_DIR}/scripts/adoption_rehearsal.py" \
  "${PROFILE}" \
  --root "${ROOT_DIR}" \
  --purpose fresh-start \
  "$@"
