#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_MODULE="${ROOT_DIR}/01-infrastructure/modules/backend/main.tf"
CADDY_TEMPLATE="${ROOT_DIR}/01-infrastructure/templates/Caddyfile.tpl"
OPERATOR_CHECK="${ROOT_DIR}/operator-check.sh"

fail() { printf '%s\n' "$*" >&2; exit 1; }

grep -Fq 'ip       = "127.0.0.1"' "${BACKEND_MODULE}" || fail "backend host port must be loopback-bound"
[[ "$(grep -Fc '@backend_actuator path /actuator /actuator/*' "${CADDY_TEMPLATE}")" == 2 ]] ||
  fail "product and API Caddy sites must both reject Actuator"
[[ "$(grep -A2 -F '@backend_actuator path /actuator /actuator/*' "${CADDY_TEMPLATE}" | grep -Fc 'respond "Not Found" 404')" == 2 ]] ||
  fail "public Actuator matchers must return 404"
grep -Fq '@product_api path /api/*' "${CADDY_TEMPLATE}" || fail "public product API routing was removed"
# shellcheck disable=SC2016
grep -Fq 'reverse_proxy ${api_upstream}' "${CADDY_TEMPLATE}" || fail "public backend API proxy was removed"
grep -Fq '/api/health/ready' "${OPERATOR_CHECK}" || fail "operator backend readiness check was removed"
# shellcheck disable=SC2016
grep -Fq 'http://${LOOPBACK_HOST}:${TF_VAR_backend_host_port:-48084}/api/health/ready' "${OPERATOR_CHECK}" ||
  fail "operator readiness must use the loopback backend port"

if grep -Eq 'reverse_proxy[^\n]*actuator|path /actuator/\*[^\n]*reverse_proxy' "${CADDY_TEMPLATE}"; then
  fail "Actuator must not be reverse proxied publicly"
fi

printf 'backend health boundary tests passed\n'
