#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR

grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}/mailpit-cert.pem:/run/mailpit/tls/cert.pem:ro' \
  "${ROOT_DIR}/compose.yaml"
grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}/mailpit-key.pem:/run/mailpit/tls/key.pem:ro' \
  "${ROOT_DIR}/compose.yaml"
caddy_service="$(sed -n '/^  caddy:/,/^  keycloak:/p' "${ROOT_DIR}/compose.yaml")"
grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}/mailpit-cert.pem:/certs/mailpit-cert.pem:ro' \
  <<<"${caddy_service}"
grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}/mailpit-key.pem:/certs/mailpit-key.pem:ro' \
  <<<"${caddy_service}"
grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}/ca.pem:/opt/weave/trust/ca.pem:ro' \
  "${ROOT_DIR}/compose.yaml"
grep -Fq 'KC_TRUSTSTORE_PATHS: /opt/weave/trust/ca.pem' "${ROOT_DIR}/compose.yaml"
if grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}:/opt/weave/trust' \
  "${ROOT_DIR}/compose.yaml"; then
  printf '%s\n' "Keycloak trust roots must not mount the TLS private-key directory." >&2
  exit 1
fi
if grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}:/run/mailpit/tls:ro' \
  "${ROOT_DIR}/compose.yaml"; then
  printf '%s\n' "Mailpit must not receive the CA or gateway private-key directory." >&2
  exit 1
fi
grep -Fq '"mailpit-cert.pem"' "${ROOT_DIR}/scripts/init_secrets.py"
grep -Fq '"mailpit-key.pem"' "${ROOT_DIR}/scripts/init_secrets.py"
grep -Fq '["mailpit"]' "${ROOT_DIR}/scripts/init_secrets.py"
grep -Fq 'ACTIVATION_SERVICES = ("mailpit",)' \
  "${ROOT_DIR}/scripts/operator_check.py"
grep -A8 '^  mailpit:$' "${ROOT_DIR}/compose.yaml" | \
  grep -Fq 'com.massimotter.weave.data-class: activation-sensitive'
grep -Fq 'if context.environment in {"dogfood", "e2e"} or "dev-tools" in context.active_profiles:' \
  "${ROOT_DIR}/scripts/operator_check.py"
grep -A4 '^x-mail-profiles: &mail-profiles$' "${ROOT_DIR}/compose.yaml" | \
  grep -Fq -- '- dev-tools'
grep -A4 '^x-mail-profiles: &mail-profiles$' "${ROOT_DIR}/compose.yaml" | \
  grep -Fq -- '- dogfood'
grep -A4 '^x-mail-profiles: &mail-profiles$' "${ROOT_DIR}/compose.yaml" | \
  grep -Fq -- '- e2e'
if grep -A4 '^x-mail-profiles: &mail-profiles$' "${ROOT_DIR}/compose.yaml" | \
  grep -Eq -- '- (dev|prod)$'; then
  printf '%s\n' "Mailpit must remain outside default dev/prod profiles." >&2
  exit 1
fi

printf '%s\n' "Mailpit activation readiness contract test passed."
