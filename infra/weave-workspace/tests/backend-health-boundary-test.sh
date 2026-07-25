#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing backend boundary '$2' in $1"; }

require "${ROOT_DIR}/compose.yaml" '"127.0.0.1:${WEAVE_BACKEND_HOST_PORT:-48084}:8080"'
require "${ROOT_DIR}/compose.yaml" 'curl -fsS http://127.0.0.1:8080/api/health/ready'
require "${ROOT_DIR}/scripts/render_config.py" '@internal path /api/internal/* /actuator/*'
require "${ROOT_DIR}/scripts/render_config.py" 'respond @internal'
require "${ROOT_DIR}/scripts/render_config.py" 'Not Found'
require "${ROOT_DIR}/scripts/render_config.py" '@product_api path /api/*'
require "${ROOT_DIR}/scripts/operator_check.py" 'context.env["WEAVE_API_URL"].rstrip("/") + "/health/ready"'
require "${REPO_ROOT}/server/src/main/resources/application.yml" 'open-in-view: false'
require "${REPO_ROOT}/server/src/main/resources/application.yml" 'ddl-auto: validate'

if grep -Eq 'reverse_proxy[^\n]*(/actuator|actuator:)' "${ROOT_DIR}/scripts/render_config.py"; then
  fail "Actuator must never be reverse proxied by the public gateway"
fi

printf 'backend health boundary tests passed\n'
