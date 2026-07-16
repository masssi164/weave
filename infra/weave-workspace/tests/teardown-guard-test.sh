#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly TEARDOWN_SCRIPT="${ROOT_DIR}/teardown.sh"

run_case() {
  local name="$1"
  local expected_status="$2"
  shift 2

  local output_file
  output_file="$(mktemp)"

  set +e
  (
    cd "${ROOT_DIR}"
    env -i \
      PATH="${PATH}" \
      HOME="${HOME:-/tmp}" \
      WEAVE_TEARDOWN_DRY_RUN=true \
      TF_VAR_tenant_slug=weave \
      "$@" \
      bash "${TEARDOWN_SCRIPT}"
  ) >"${output_file}" 2>&1
  local status=$?
  set -e

  if [[ "${status}" != "${expected_status}" ]]; then
    printf 'FAIL %s: expected exit %s, got %s\n' "${name}" "${expected_status}" "${status}" >&2
    cat "${output_file}" >&2
    rm -f "${output_file}"
    exit 1
  fi

  printf '%s\n' "${output_file}"
}

assert_contains() {
  local file="$1"
  local expected="$2"

  if ! grep -Fq -- "${expected}" "${file}"; then
    printf 'Expected output to contain: %s\n' "${expected}" >&2
    cat "${file}" >&2
    rm -f "${file}"
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local unexpected="$2"

  if grep -Fq -- "${unexpected}" "${file}"; then
    printf 'Expected output not to contain: %s\n' "${unexpected}" >&2
    cat "${file}" >&2
    rm -f "${file}"
    exit 1
  fi
}

write_isolated_ownership() {
  local output_root="$1"
  local namespace="$2"
  local run_id="$3"
  local candidate="$4"
  local evidence_ref="$5"
  local target="${output_root}/${namespace}/teardown-ownership.json"

  mkdir -p "$(dirname -- "${target}")"
  jq -n \
    --arg namespace "${namespace}" \
    --arg runId "${run_id}" \
    --arg candidateCommit "${candidate}" \
    --arg candidateEvidenceRef "${evidence_ref}" \
    '{schemaVersion:"weave.isolated-e2e-teardown-ownership.v1",scope:"isolated",namespace:$namespace,runId:$runId,candidateCommit:$candidateCommit,candidateEvidenceRef:$candidateEvidenceRef,resourcePrefix:$namespace}' \
    >"${target}"
  chmod 600 "${target}"
  printf '%s\n' "${target}"
}

main() {
  local output
  local isolated_root isolated_namespace isolated_run candidate evidence_ref ownership

  output="$(run_case "partial isolation intent fails before persistent cleanup" 1 \
    WEAVE_E2E_RUN_ID=partial-isolated-run)"
  assert_contains "${output}" "isolated teardown intent requires WEAVE_E2E_STACK_SCOPE=isolated"
  assert_not_contains "${output}" "DRY RUN: would remove container"
  rm -f "${output}"

  output="$(run_case "preserves volumes by default" 0)"
  assert_contains "${output}" "Persistent Docker volumes: preserved."
  assert_contains "${output}" "DRY RUN: would remove container weave-mcp-server"
  assert_contains "${output}" "DRY RUN: would remove container weave-mailpit"
  assert_not_contains "${output}" "DRY RUN: would remove volume weave_synapse_data"
  assert_not_contains "${output}" "DRY RUN: would remove volume weave_mailpit_data"
  rm -f "${output}"

  output="$(run_case "refuses missing confirmation" 2 WEAVE_REMOVE_VOLUMES=true)"
  assert_contains "${output}" "Refusing to remove persistent Weave Docker volumes without the typed tenant/workspace confirmation."
  assert_contains "${output}" "docs/operator-runbook.md#5-backup-expectations"
  assert_contains "${output}" "Keycloak identity/session data"
  assert_contains "${output}" "Matrix/Synapse database and media state"
  assert_contains "${output}" "Nextcloud database, files, and calendar data"
  assert_contains "${output}" "Mailpit messages containing dogfood activation links"
  assert_contains "${output}" "WEAVE_CONFIRM_DESTRUCTIVE_RESET=weave"
  assert_not_contains "${output}" "DRY RUN: would remove volume weave_synapse_data"
  rm -f "${output}"

  output="$(run_case "refuses legacy confirmation" 2 WEAVE_REMOVE_VOLUMES=true WEAVE_CONFIRM_REMOVE_VOLUMES=weave-delete-local-data)"
  assert_contains "${output}" "old"
  assert_contains "${output}" "WEAVE_CONFIRM_REMOVE_VOLUMES=weave-delete-local-data"
  assert_contains "${output}" "Type the tenant/workspace slug instead."
  rm -f "${output}"

  output="$(run_case "removes volumes with typed confirmation in dry-run mode" 0 WEAVE_REMOVE_VOLUMES=true WEAVE_CONFIRM_DESTRUCTIVE_RESET=weave)"
  assert_contains "${output}" "Destructive reset confirmed for tenant/workspace slug 'weave'."
  assert_contains "${output}" "DRY RUN: would remove volume weave_synapse_data"
  assert_contains "${output}" "DRY RUN: would remove volume weave_nextcloud_data"
  assert_contains "${output}" "DRY RUN: would remove volume weave_mailpit_data"
  assert_contains "${output}" "DRY RUN: would remove volume weave_matrix_chat_appservice_runtime"
  rm -f "${output}"

  isolated_root="$(mktemp -d)"
  isolated_namespace="weave-e2e-0123456789abcdef"
  isolated_run="fixture-run-1"
  candidate="cccccccccccccccccccccccccccccccccccccccc"
  evidence_ref="https://github.example.invalid/weave/actions/runs/101"
  ownership="$(write_isolated_ownership "${isolated_root}" "${isolated_namespace}" "${isolated_run}" "${candidate}" "${evidence_ref}")"

  local -a isolated_env=(
    WEAVE_E2E_STACK_SCOPE=isolated
    WEAVE_E2E_RUN_ID="${isolated_run}"
    WEAVE_E2E_OUTPUT_ROOT="${isolated_root}"
    WEAVE_CANDIDATE_COMMIT="${candidate}"
    WEAVE_CANDIDATE_EVIDENCE_REF="${evidence_ref}"
    WEAVE_TEARDOWN_OWNERSHIP_FILE="${ownership}"
    WEAVE_REMOVE_VOLUMES=true
    TF_VAR_isolated_e2e_enabled=true
    TF_VAR_isolated_e2e_namespace="${isolated_namespace}"
    TF_VAR_docker_network_name="${isolated_namespace}_network"
  )

  output="$(run_case "isolated teardown rejects HTTP evidence" 1 \
    "${isolated_env[@]}" WEAVE_CANDIDATE_EVIDENCE_REF=http://github.example.invalid/actions/runs/101)"
  assert_contains "${output}" "requires a support-safe HTTPS WEAVE_CANDIDATE_EVIDENCE_REF"
  assert_not_contains "${output}" "DRY RUN: would remove container"
  rm -f "${output}"

  output="$(run_case "isolated teardown rejects credentialed evidence" 1 \
    "${isolated_env[@]}" WEAVE_CANDIDATE_EVIDENCE_REF=https://token@github.example.invalid/actions/runs/101)"
  assert_contains "${output}" "requires a support-safe HTTPS WEAVE_CANDIDATE_EVIDENCE_REF"
  assert_not_contains "${output}" "DRY RUN: would remove container"
  rm -f "${output}"

  output="$(run_case "isolated teardown requires candidate commit" 1 \
    "${isolated_env[@]}" WEAVE_CANDIDATE_COMMIT=)"
  assert_contains "${output}" "requires a lowercase 40-character WEAVE_CANDIDATE_COMMIT"
  assert_not_contains "${output}" "DRY RUN: would remove container"
  rm -f "${output}"

  output="$(run_case "isolated teardown requires ownership evidence path" 1 \
    "${isolated_env[@]}" WEAVE_TEARDOWN_OWNERSHIP_FILE=)"
  assert_contains "${output}" "requires WEAVE_TEARDOWN_OWNERSHIP_FILE"
  assert_not_contains "${output}" "DRY RUN: would remove container"
  rm -f "${output}"

  output="$(run_case "isolated teardown is namespace-bound" 0 "${isolated_env[@]}")"
  assert_contains "${output}" "DRY RUN: would remove container ${isolated_namespace}-backend"
  assert_contains "${output}" "DRY RUN: would remove volume ${isolated_namespace//-/_}_nextcloud_data"
  assert_contains "${output}" "DRY RUN: would remove network ${isolated_namespace}_network"
  assert_not_contains "${output}" "DRY RUN: would remove container weave-backend"
  assert_not_contains "${output}" "DRY RUN: would remove volume weave_nextcloud_data"
  assert_not_contains "${output}" "DRY RUN: would remove network weave_network"
  rm -f "${output}"
  rm -rf "${isolated_root}"

  printf 'teardown guard tests passed\n'
}

main "$@"
