#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPO_ROOT

feature_files=(
  "acceptance/openproject_boards_live_stack.feature"
  "acceptance/operator_support_safety.feature"
)

scenarios=()
while IFS= read -r scenario; do
  scenarios+=("${scenario}")
done < <(
  cd "${REPO_ROOT}"
  awk '/^[[:space:]]*Scenario: / {sub(/^[[:space:]]*Scenario: /, ""); print}' "${feature_files[@]}"
)

required_scenarios=(
  "OpenProject provider disabled fails closed through Weave"
  "OpenProject provider enabled exposes read-only provider-neutral boards"
  "Missing Context Space authorization exposes no provider data"
  "Provider writes remain refused until audit and consent promotion"
  "Support bundle redacts secrets and provider credentials"
  "Operator checks verify the local Weave product topology"
  "Destructive reset refuses persistent data deletion without typed confirmation"
)

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

contains_scenario() {
  local needle="$1"
  local item
  for item in "${scenarios[@]}"; do
    [[ "${item}" == "${needle}" ]] && return 0
  done
  return 1
}

assert_file_contains() {
  local file="$1"
  local expected="$2"
  grep -Fq -- "${expected}" "${REPO_ROOT}/${file}" || \
    fail "Acceptance scenario mapping missing executable fragment '${expected}' in ${file}"
}

for scenario in "${required_scenarios[@]}"; do
  contains_scenario "${scenario}" || fail "Missing acceptance scenario: ${scenario}"
done

if [[ "${#scenarios[@]}" -ne "${#required_scenarios[@]}" ]]; then
  printf 'Scenarios found:\n' >&2
  printf -- '- %s\n' "${scenarios[@]}" >&2
  fail "Every infra acceptance scenario must be declared in acceptance-feature-mapping-test.sh"
fi

assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_ENABLED"
assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "openproject-read-sync-backend-facade"
assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "support-safe"
assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "provider write endpoint should be refused"

assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_CONTEXT_DENIED"
assert_file_contains "weave-workspace/openproject-boards-live-e2e.sh" "boards-forbidden"
assert_file_contains "docs/openproject-boards-runtime.md" "WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_CONTEXT_DENIED=true"

assert_file_contains "weave-workspace/tests/support-bundle-redaction-test.sh" "TF_VAR_boards_openproject_api_token=openproject-super-secret"
assert_file_contains "weave-workspace/tests/support-bundle-redaction-test.sh" "support bundle leaked a test secret"
assert_file_contains "weave-workspace/tests/support-bundle-redaction-test.sh" "WEAVE_BOARDS_OPENPROJECT_BASE_URL=https://openproject.example"

assert_file_contains "weave-workspace/operator-check.sh" "Checking public product, issuer, API, files, and matrix routes"
assert_file_contains "weave-workspace/operator-check.sh" "WEAVE_BASE_URL:=\$("
assert_file_contains "weave-workspace/operator-check.sh" "WEAVE_MATRIX_HOMESERVER_URL:=\$("
assert_file_contains "weave-workspace/operator-check.sh" "assert_backend_boards_openproject_config"

assert_file_contains "weave-workspace/tests/teardown-guard-test.sh" "Persistent Docker volumes: preserved."
assert_file_contains "weave-workspace/tests/teardown-guard-test.sh" "Refusing to remove persistent Weave Docker volumes"
assert_file_contains "weave-workspace/tests/teardown-guard-test.sh" "docs/operator-runbook.md#5-backup-expectations"

printf 'acceptance feature mapping tests passed\n'
