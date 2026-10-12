#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="${ROOT}/gradle/scripts/discard_unsafe_diagnostics.py"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT

output_root="${work_dir}/test-app"
diagnostics="${output_root}/isolated-run/failure-diagnostics"
mkdir -p "${diagnostics}"
printf 'subject_token_client_id="unsafe-fixture"\n' >"${diagnostics}/keycloak.log"
python3 "${SCRIPT}" --output-root "${output_root}" --diagnostics-dir "${diagnostics}"
[[ -s "${diagnostics}/diagnostics-withheld.txt" ]]
[[ ! -e "${diagnostics}/keycloak.log" ]]
! grep -R -Fq 'unsafe-fixture' "${output_root}"

outside="${work_dir}/outside"
mkdir -p "${outside}/failure-diagnostics"
printf 'preserve\n' >"${outside}/failure-diagnostics/real-data"
if python3 "${SCRIPT}" --output-root "${output_root}" --diagnostics-dir "${outside}/failure-diagnostics" >/dev/null 2>&1; then
  printf '%s\n' 'diagnostics discard accepted a path outside its output root' >&2
  exit 1
fi
[[ -s "${outside}/failure-diagnostics/real-data" ]]

printf 'preserve\n' >"${outside}/real-data"
rm -r -- "${diagnostics}"
ln -s "${outside}" "${diagnostics}"
python3 "${SCRIPT}" --output-root "${output_root}" --diagnostics-dir "${diagnostics}"
[[ -s "${diagnostics}/diagnostics-withheld.txt" ]]
[[ -s "${outside}/real-data" ]]

printf '%s\n' 'testApp diagnostics safety test passed'
