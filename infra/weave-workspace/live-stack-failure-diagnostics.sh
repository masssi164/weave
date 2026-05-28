#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
# shellcheck source=infra/weave-workspace/support-bundle.sh
source "${SCRIPT_DIR}/support-bundle.sh"

OUTPUT_DIR="${1:-${WEAVE_LIVE_STACK_FAILURE_DIAGNOSTICS_DIR:-${ROOT_DIR}/.generated/live-stack-failure-diagnostics}}"
CREATED_AT_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PRIVATE_RAW_LOGS_ENABLED="${WEAVE_LIVE_STACK_PRIVATE_RAW_LOGS:-false}"
PRIVATE_RAW_LOGS_DIR="${WEAVE_PRIVATE_RAW_LOGS_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/weave-private-raw-live-logs}"

log() {
  printf '%s\n' "$*"
}

json_escape() {
  jq -Rs .
}

relative_or_absolute() {
  local path="$1"
  case "${path}" in
    "${ROOT_DIR}"/*) printf '%s' "${path#"${ROOT_DIR}/"}" ;;
    *) printf '%s' "${path}" ;;
  esac
}

is_path_under() {
  local child="$1"
  local parent="$2"
  local child_real parent_real
  child_real="$(mkdir -p "${child}" && cd "${child}" && pwd -P)"
  parent_real="$(mkdir -p "${parent}" && cd "${parent}" && pwd -P)"
  [[ "${child_real}" == "${parent_real}" || "${child_real}" == "${parent_real}"/* ]]
}

write_container_status() {
  local target="$1"
  mkdir -p "$(dirname -- "${target}")"
  {
    printf 'container\tstate\thealth\texitCode\n'
    if ! command -v docker >/dev/null 2>&1; then
      printf 'docker\tunavailable\tn/a\tn/a\n'
      return
    fi
    local container line
    for container in "${DEFAULT_CONTAINERS[@]}"; do
      line="$(docker inspect --format '{{.Name}}\t{{.State.Status}}\t{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}\t{{.State.ExitCode}}' "${container}" 2>/dev/null || true)"
      if [[ -z "${line}" ]]; then
        printf '%s\tnot-found\tn/a\tn/a\n' "${container}"
      else
        printf '%s\n' "${line#/}" | redact_stream
      fi
    done
  } >"${target}"
}

write_operator_check() {
  local target="$1"
  local status_target="$2"
  mkdir -p "$(dirname -- "${target}")"
  set +e
  bash "${ROOT_DIR}/operator-check.sh" 2>&1 | redact_stream >"${target}"
  local status=${PIPESTATUS[0]}
  set -e
  printf '%s\n' "${status}" >"${status_target}"
}

write_failed_markers() {
  local target="$1"
  local evidence_markers="${WEAVE_LIVE_STACK_EVIDENCE_MARKERS:-${WEAVE_ACCEPTANCE_EVIDENCE_DIR:-}/evidence-markers.json}"
  local scenario_results="${WEAVE_LIVE_STACK_SCENARIO_RESULTS:-${WEAVE_ACCEPTANCE_EVIDENCE_DIR:-}/scenario-mapping-results.json}"
  mkdir -p "$(dirname -- "${target}")"

  if [[ -s "${scenario_results}" ]]; then
    jq '{schema:"weave-live-stack-failed-markers-v1", failedOrIncompleteScenarios: [.scenarioResults[]? | select(.runtimeStatus == "failedOrIncomplete") | {scenario: .scenario.name, markers: [.markers[]?.marker]}]}' "${scenario_results}" >"${target}" 2>/dev/null || printf '{"schema":"weave-live-stack-failed-markers-v1","error":"scenario-results-unreadable"}\n' >"${target}"
    return
  fi

  if [[ -s "${evidence_markers}" ]]; then
    jq '{schema:"weave-live-stack-failed-markers-v1", observedMarkers: (.observedMarkers // []), markerCount: ((.observedMarkers // []) | length)}' "${evidence_markers}" >"${target}" 2>/dev/null || printf '{"schema":"weave-live-stack-failed-markers-v1","error":"evidence-markers-unreadable"}\n' >"${target}"
    return
  fi

  printf '{"schema":"weave-live-stack-failed-markers-v1","status":"acceptance-evidence-not-generated"}\n' >"${target}"
}

write_support_bundle() {
  local target_dir="$1"
  mkdir -p "${target_dir}"
  set +e
  WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=true \
    WEAVE_SUPPORT_BUNDLE_LOG_LINES="${WEAVE_LIVE_STACK_SUPPORT_BUNDLE_LOG_LINES:-80}" \
    bash "${ROOT_DIR}/support-bundle.sh" "${target_dir}" >"${target_dir}/support-bundle-command.txt" 2>&1
  local status=$?
  set -e
  redact_stream <"${target_dir}/support-bundle-command.txt" >"${target_dir}/support-bundle-command.redacted.txt"
  mv "${target_dir}/support-bundle-command.redacted.txt" "${target_dir}/support-bundle-command.txt"
  printf '%s\n' "${status}" >"${target_dir}/support-bundle-exit-status.txt"
  find "${target_dir}" -maxdepth 1 -name 'weave-support-*.tar.gz' -print -quit
}

write_private_raw_logs_if_requested() {
  local output_dir="$1"
  local target="$2"
  if [[ "${PRIVATE_RAW_LOGS_ENABLED}" != "true" ]]; then
    printf 'disabled\n' >"${target}"
    return
  fi

  if is_path_under "${PRIVATE_RAW_LOGS_DIR}" "${output_dir}"; then
    printf 'refused: private raw log directory must not be inside uploaded evidence output\n' >"${target}"
    return
  fi

  mkdir -p "${PRIVATE_RAW_LOGS_DIR}"
  chmod 700 "${PRIVATE_RAW_LOGS_DIR}" || true
  if ! command -v docker >/dev/null 2>&1; then
    printf 'skipped: docker unavailable\n' >"${target}"
    return
  fi

  local container
  for container in "${DEFAULT_CONTAINERS[@]}"; do
    docker logs "${container}" >"${PRIVATE_RAW_LOGS_DIR}/${container}.log" 2>&1 || true
  done
  chmod -R go-rwx "${PRIVATE_RAW_LOGS_DIR}" || true
  printf 'written outside uploaded evidence: %s\n' "${PRIVATE_RAW_LOGS_DIR}" >"${target}"
}

main() {
  mkdir -p "${OUTPUT_DIR}/health-checks" "${OUTPUT_DIR}/support-bundle"

  local container_status="${OUTPUT_DIR}/container-status.tsv"
  local operator_check="${OUTPUT_DIR}/health-checks/operator-check.txt"
  local operator_status="${OUTPUT_DIR}/health-checks/operator-check-exit-status.txt"
  local failed_markers="${OUTPUT_DIR}/failed-markers.json"
  local private_status="${OUTPUT_DIR}/private-raw-logs-status.txt"

  write_container_status "${container_status}"
  write_operator_check "${operator_check}" "${operator_status}"
  write_failed_markers "${failed_markers}"
  local support_bundle=""
  support_bundle="$(write_support_bundle "${OUTPUT_DIR}/support-bundle" || true)"
  write_private_raw_logs_if_requested "${OUTPUT_DIR}" "${private_status}"

  local operator_exit support_exit private_status_text bundle_reference
  operator_exit="$(cat "${operator_status}")"
  support_exit="$(cat "${OUTPUT_DIR}/support-bundle/support-bundle-exit-status.txt")"
  private_status_text="$(cat "${private_status}" | redact_stream)"
  if [[ -n "${support_bundle}" ]]; then
    bundle_reference="$(relative_or_absolute "${support_bundle}")"
  else
    bundle_reference="support bundle not written; see support-bundle/support-bundle-command.txt"
  fi

  cat >"${OUTPUT_DIR}/failure-summary.md" <<MD
## Live Stack failure diagnostics

Generated UTC: ${CREATED_AT_ISO}

This directory is support-safe by default. It intentionally does not dump raw container logs.

- Container status: \`container-status.tsv\`
- Readiness check output: \`health-checks/operator-check.txt\` (exit ${operator_exit})
- Failed or missing acceptance markers: \`failed-markers.json\`
- Redacted support bundle: \`${bundle_reference}\` (exit ${support_exit})
- Private raw logs: ${private_status_text}

Raw container logs are operator-private diagnostics only. Enable them deliberately with
\`WEAVE_LIVE_STACK_PRIVATE_RAW_LOGS=true\` and keep \`WEAVE_PRIVATE_RAW_LOGS_DIR\` outside the uploaded evidence directory.
MD

  {
    printf '{\n'
    printf '  "schema": "weave-live-stack-failure-diagnostics-v1",\n'
    printf '  "generatedAtUtc": %s,\n' "$(printf '%s' "${CREATED_AT_ISO}" | json_escape)"
    printf '  "supportSafe": true,\n'
    printf '  "rawContainerLogsIncluded": false,\n'
    printf '  "containerStatus": "container-status.tsv",\n'
    printf '  "operatorCheck": {"path": "health-checks/operator-check.txt", "exitStatus": %s},\n' "${operator_exit}"
    printf '  "failedMarkers": "failed-markers.json",\n'
    printf '  "supportBundleReference": %s,\n' "$(printf '%s' "${bundle_reference}" | json_escape)"
    printf '  "privateRawLogs": %s\n' "$(printf '%s' "${private_status_text}" | json_escape)"
    printf '}\n'
  } >"${OUTPUT_DIR}/failure-summary.json"

  scan_for_unredacted_secrets "${OUTPUT_DIR}"
  log "Support-safe live stack failure diagnostics written to ${OUTPUT_DIR}"
}

main "$@"
