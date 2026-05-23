#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC1091

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# Source only the helper functions; openproject-boards-live-e2e.sh is guarded so the
# live E2E flow does not run during this static fixture test.
source "${ROOT_DIR}/openproject-boards-live-e2e.sh"

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT

write_fixture() {
  local name="$1"
  local body="$2"
  local file="${work_dir}/${name}.json"
  printf '%s\n' "${body}" >"${file}"
  printf '%s\n' "${file}"
}

expect_support_safe() {
  local file="$1"
  assert_support_safe_file "${file}"
}

expect_leak() {
  local file="$1"
  if (assert_support_safe_file "${file}") >/dev/null 2>&1; then
    printf 'Expected support-safe scanner to reject fixture: %s\n' "${file}" >&2
    exit 1
  fi
}

safe_registry="$(write_fixture safe-registry '{
  "backendOwnedFacades": true,
  "flutterDirectProviderCallsAllowed": false,
  "supportSafe": true,
  "redactionPolicy": "provider secrets, passwords, and raw upstream errors are redacted before support use",
  "providers": [
    {
      "module": "boards",
      "provider": "openproject",
      "configured": false,
      "supportSafe": true,
      "unsupportedOperations": ["set-password", "rotate-secret", "raw-secret-export"],
      "secretMetadata": "redacted",
      "passwordMode": "omitted"
    }
  ]
}')"
expect_support_safe "${safe_registry}"

export WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_SECRET='openproject-super-secret-token'
expect_leak "$(write_fixture token-leak '{"diagnostic":"openproject-super-secret-token"}')"
unset WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_SECRET

export WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_BASE_URL='https://openproject.internal.example'
expect_leak "$(write_fixture base-url-leak '{"diagnostic":"https://openproject.internal.example"}')"
unset WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_BASE_URL

expect_leak "$(write_fixture auth-header-leak '{"debug":"Authorization: Bearer abcdefghijklmnopqrstuvwxyz"}')"
expect_leak "$(write_fixture upstream-path-leak '{"debug":"GET /api/v3/projects/1/work_packages/2"}')"
expect_leak "$(write_fixture secret-assignment-leak '{"debug":"client_secret=raw-provider-secret"}')"
expect_leak "$(write_fixture sensitive-field-leak '{"providers":[{"module":"boards","apiKey":"raw-provider-key"}]}')"
expect_leak "$(write_fixture upstream-error-leak '{"upstreamError":"OpenProject 500 raw error"}')"

printf '%s\n' 'OpenProject support-safe scanner fixture test passed'
