#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/dogfood-pending-identity-recovery.yml"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Expected $1 to contain: $2"; }
reject() { ! grep -Fq -- "$2" "$1" || fail "Retired recovery authority remains in $1: $2"; }

require "${WORKFLOW}" 'Dogfood Pending Identity Recovery (Guarded)'
require "${WORKFLOW}" 'acknowledge-recovery-is-guarded'
require "${WORKFLOW}" 'Pending-identity retirement is guarded.'
require "${WORKFLOW}" 'Use normal invitation resend and authenticated session reconciliation'
reject "${WORKFLOW}" 'WEAVE_KEYCLOAK_SUPERVISOR'
reject "${WORKFLOW}" 'keycloak-sanitizer'
reject "${WORKFLOW}" 'keycloak-event-listener'
reject "${WORKFLOW}" './compose.sh dogfood'

printf 'DOGFOOD_PENDING_IDENTITY_RECOVERY_CONTRACT status=guarded authority=identity-ops supportSafe=true\n'
