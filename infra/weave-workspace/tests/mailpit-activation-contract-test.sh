#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR

grep -Fq \
  '${WEAVE_TLS_ROOT:-./.generated/dev/tls}:/run/mailpit/tls:ro' \
  "${ROOT_DIR}/compose.yaml"
if grep -Fq \
  '${WEAVE_GENERATED_ROOT:-./.generated/dev}/mailpit/tls:/run/mailpit/tls:ro' \
  "${ROOT_DIR}/compose.yaml"; then
  printf '%s\n' "Mailpit must consume the canonical TLS generation directly." >&2
  exit 1
fi
grep -Fq 'ACTIVATION_SERVICES = ("mailpit",)' \
  "${ROOT_DIR}/scripts/operator_check.py"
grep -Fq 'if context.profile in {"dev", "test"}:' \
  "${ROOT_DIR}/scripts/operator_check.py"

printf '%s\n' "Mailpit activation readiness contract test passed."
