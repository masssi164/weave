#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"
python3 "${WORKSPACE}/scripts/prepare_dev_dependencies.py" --root "${WORKSPACE}"

printf '%s\n' \
  "WEAVE_DEV_UP_READY providers=containers applications=host" \
  "Run ./gradlew devRun to start Server and MCP as separate host processes."
