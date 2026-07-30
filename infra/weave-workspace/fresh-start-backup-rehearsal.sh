#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly PROFILE="${1:-test}"
if [[ $# -gt 0 ]]; then
  shift
fi

exec python3 "${ROOT_DIR}/scripts/adoption_rehearsal.py" \
  "${PROFILE}" \
  --root "${ROOT_DIR}" \
  --purpose fresh-start \
  "$@"
