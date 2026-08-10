#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd -- "${ROOT_DIR}/../.." && pwd)"
readonly ROOT_DIR REPO_ROOT

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing backend boundary '$2' in $1"; }

GATEWAY="${ROOT_DIR}/scripts/rendering/gateway.py"

require "${ROOT_DIR}/compose.yaml" '"127.0.0.1:${WEAVE_BACKEND_HOST_PORT:-48084}:8080"'
require "${ROOT_DIR}/compose.yaml" 'curl -fsS http://127.0.0.1:8080/api/health/ready'
require "${GATEWAY}" '@internal path /api/internal/* /actuator/*'
require "${GATEWAY}" 'handle @internal {'
require "${GATEWAY}" 'respond 404'
require "${GATEWAY}" 'handle_path /api/* {'
require "${ROOT_DIR}/scripts/operator_check.py" 'context.env["WEAVE_API_URL"].rstrip("/") + "/health/ready"'
require "${REPO_ROOT}/server/src/main/resources/application.yml" 'import: classpath:application-base.yml'
require "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'open-in-view: false'
require "${REPO_ROOT}/server/src/main/resources/application-base.yml" 'ddl-auto: ${WEAVE_JPA_DDL_AUTO:validate}'

if grep -Eq 'reverse_proxy[^\n]*(/actuator|actuator:)' "${GATEWAY}"; then
  fail "Actuator must never be reverse proxied by the public gateway"
fi

printf 'backend health boundary tests passed\n'
