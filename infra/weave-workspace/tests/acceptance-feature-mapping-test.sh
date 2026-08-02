#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

INFRA_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly INFRA_ROOT
readonly FEATURE="${INFRA_ROOT}/acceptance/operator_support_safety.feature"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require() { grep -Fq -- "$2" "$1" || fail "Missing current Compose evidence fragment '$2' in $1"; }

scenarios=()
while IFS= read -r scenario; do
  scenarios+=("${scenario}")
done < <(awk '/^[[:space:]]*Scenario: / {sub(/^[[:space:]]*Scenario: /, ""); print}' "${FEATURE}")
expected=(
  "Support bundle excludes secrets and provider credentials"
  "Operator checks verify the declared Compose product topology"
  "Runner checks verify provider readiness fail-closed"
  "Destructive teardown rejects persistent projects"
)
[[ "${#scenarios[@]}" == "${#expected[@]}" ]] || fail "Infrastructure acceptance scenarios and executable mappings differ"
for index in "${!expected[@]}"; do
  [[ "${scenarios[index]}" == "${expected[index]}" ]] || fail "Unexpected infrastructure scenario ordering"
done

require "${INFRA_ROOT}/weave-workspace/scripts/support_bundle.py" '"raw logs"'
require "${INFRA_ROOT}/weave-workspace/scripts/support_bundle.py" '"signed receipt payloads"'
require "${INFRA_ROOT}/weave-workspace/scripts/support_bundle.py" '"containsSecretValues": False'
require "${INFRA_ROOT}/weave-workspace/scripts/operator_check.py" 'Nextcloud authenticated DAV readiness evidence is missing'
require "${INFRA_ROOT}/weave-workspace/scripts/operator_check.py" 'adminControlPlaneMemberDenial'
require "${INFRA_ROOT}/weave-workspace/scripts/teardown_compose.py" 'destructive teardown is restricted to a run-scoped isolated test project'
require "${INFRA_ROOT}/weave-workspace/scripts/teardown_compose.py" 'refusing to remove unowned Docker'

if find "${INFRA_ROOT}/acceptance" -maxdepth 1 -type f -name '*openproject*' -print -quit | grep -q .; then
  fail "Expansion OpenProject acceptance must not be part of the normative Core infrastructure lane"
fi

printf 'acceptance feature mapping tests passed\n'
