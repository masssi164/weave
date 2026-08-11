#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${SCRIPT_DIR}"
readonly SCRIPT_DIR ROOT_DIR
DIAGNOSTICS_TIMEOUT_SECONDS="${WEAVE_LIVE_STACK_DIAGNOSTICS_TIMEOUT_SECONDS:-30}"
[[ "${DIAGNOSTICS_TIMEOUT_SECONDS}" =~ ^[0-9]+$ ]] &&
  ((DIAGNOSTICS_TIMEOUT_SECONDS >= 1 && DIAGNOSTICS_TIMEOUT_SECONDS <= 180)) || {
  printf '%s\n' 'Failure diagnostics timeout must be an integer from 1 through 180 seconds.' >&2
  exit 2
}
readonly DIAGNOSTICS_TIMEOUT_SECONDS
if [[ "${WEAVE_LIVE_STACK_DIAGNOSTICS_BOUNDED:-false}" != "true" ]]; then
  export WEAVE_LIVE_STACK_DIAGNOSTICS_BOUNDED=true
  exec python3 "${ROOT_DIR}/scripts/bounded_process.py" \
    --timeout-seconds "${DIAGNOSTICS_TIMEOUT_SECONDS}" \
    -- bash "${BASH_SOURCE[0]}" "$@"
fi
RESOURCE_PREFIX="${WEAVE_RESOURCE_PREFIX:-weave}"
readonly RESOURCE_PREFIX
readonly DEFAULT_CONTAINERS=(
  "${RESOURCE_PREFIX}-proxy"
  "${RESOURCE_PREFIX}-keycloak"
  "${RESOURCE_PREFIX}-backend"
  "${RESOURCE_PREFIX}-mcp-server"
  "${RESOURCE_PREFIX}-mas"
  "${RESOURCE_PREFIX}-synapse"
  "${RESOURCE_PREFIX}-nextcloud"
  "${RESOURCE_PREFIX}-mailpit"
  "${RESOURCE_PREFIX}-db"
  "${RESOURCE_PREFIX}-schema-init"
  "${RESOURCE_PREFIX}-runtime-state"
  "${RESOURCE_PREFIX}-runtime-state-volume-init"
  "${RESOURCE_PREFIX}-runtime-state-init"
)

OUTPUT_DIR="${1:-${WEAVE_LIVE_STACK_FAILURE_DIAGNOSTICS_DIR:-${ROOT_DIR}/.generated/live-stack-failure-diagnostics}}"
CREATED_AT_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PRIVATE_RAW_LOGS_ENABLED="${WEAVE_LIVE_STACK_PRIVATE_RAW_LOGS:-false}"
PRIVATE_RAW_LOGS_DIR="${WEAVE_PRIVATE_RAW_LOGS_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/weave-private-raw-live-logs}"

log() {
  printf '%s\n' "$*"
}

redact_stream() {
  perl -0pe '
    s/-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----/<redacted-private-key>/gs;
    s#([a-z][a-z0-9+.-]*://)([^\s/@:]+):([^\s/@]+)@#${1}<redacted>@#gi;
    s/(Authorization:\s*)(Bearer|Basic)\s+[^\r\n]+/${1}<redacted>/gi;
    s/((?:Set-)?Cookie:\s*)[^\r\n]+/${1}<redacted>/gi;
    s/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/<redacted-email>/gi;
    s/\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/<redacted-cloud-token>/g;
    s/\b(?:ghp|gho|ghu|ghs|ghr|github_pat|glpat|xox[baprs])-[-_A-Za-z0-9]{20,}\b/<redacted-cloud-token>/g;
    s#\bsecretref://[^\s\r\n"'"'"']+#<redacted-secret-ref>#gi;
    s#\bcredentialref://[^\s\r\n"'"'"']+#<redacted-credential-ref>#gi;
    s/\b(?:rpk|rsk)_[A-Za-z0-9_-]{20,64}\b/<redacted-key-ref>/g;
    s/\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/<redacted-jwt>/g;
    s/(flag provided but not defined:\s*)\S+/${1}<redacted>/gi;
    s/(([A-Za-z0-9_]*(?:password|passwd|token|secret|private[_-]?key|signing[_-]?key|credential|authorization|cookie)[A-Za-z0-9_]*\s*[=:]\s*)([^\s\r\n"'"'"']+))/${2}<redacted>/gi;
  '
}

redact_evidence_paths() {
  WEAVE_REDACT_EVIDENCE_ROOT="${OUTPUT_DIR}" perl -pe '
    my $root = $ENV{"WEAVE_REDACT_EVIDENCE_ROOT"};
    s/\Q$root\E/<evidence-root>/g if defined $root && length $root;
  '
}

scan_for_unredacted_secrets() {
  local path="$1" findings
  findings="$(grep -RIliE \
    'BEGIN ((RSA|EC|OPENSSH) )?PRIVATE KEY|[a-z][a-z0-9+.-]*://[^[:space:]/@:]+:[^[:space:]/@]+@|Authorization:[[:space:]]+(Bearer|Basic)[[:space:]]+[^<[:space:]]|flag provided but not defined:[[:space:]]+[^<[:space:]]|([A-Za-z0-9_]*(PASSWORD|TOKEN|SECRET|PRIVATE_KEY|SIGNING_KEY|CREDENTIAL)[A-Za-z0-9_]*[=:][[:space:]]*[^<[:space:]]+)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}' \
    "${path}" 2>/dev/null || true)"
  if [[ -n "${findings}" ]]; then
    printf 'Failure diagnostics redaction check failed; possible secret material remains in:\n%s\n' "${findings}" >&2
    return 1
  fi
}

json_escape() {
  jq -Rs .
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
    printf 'container\tstate\thealth\texitCode\toomKilled\trestartCount\tengineError\n'
    if ! command -v docker >/dev/null 2>&1; then
      printf 'docker\tunavailable\tn/a\tn/a\tn/a\tn/a\tn/a\n'
      return
    fi
    local container line
    for container in "${DEFAULT_CONTAINERS[@]}"; do
      line="$(docker inspect --format '{{.Name}}{{printf "\t"}}{{.State.Status}}{{printf "\t"}}{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}{{printf "\t"}}{{.State.ExitCode}}{{printf "\t"}}{{.State.OOMKilled}}{{printf "\t"}}{{.RestartCount}}{{printf "\t"}}{{if .State.Error}}present{{else}}none{{end}}' "${container}" 2>/dev/null || true)"
      if [[ -z "${line}" ]]; then
        printf '%s\tnot-found\tn/a\tn/a\tn/a\tn/a\tn/a\n' "${container}"
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
  bash "${ROOT_DIR}/operator-check.sh" "${WEAVE_PROFILE:-e2e}" 2>&1 | redact_stream >"${target}"
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

write_redacted_container_diagnostic() {
  local container="$1"
  local target="$2"
  mkdir -p "$(dirname -- "${target}")"
  if ! command -v docker >/dev/null 2>&1; then
    printf 'container diagnostic unavailable: docker is unavailable\n' >"${target}"
    return
  fi
  if ! docker inspect "${container}" >/dev/null 2>&1; then
    printf 'container diagnostic unavailable: container not found\n' >"${target}"
    return
  fi
  docker logs --tail 200 "${container}" 2>&1 |
    redact_stream |
    redact_evidence_paths >"${target}"
}

write_backend_readiness_diagnostic() {
  local container="$1"
  local target="$2"
  mkdir -p "$(dirname -- "${target}")"
  if ! command -v docker >/dev/null 2>&1 ||
      ! docker inspect "${container}" >/dev/null 2>&1; then
    printf '{"schema":"weave-backend-readiness-diagnostic-v1","status":"unavailable"}\n' >"${target}"
    return
  fi
  set +e
  docker exec "${container}" \
      curl --silent --show-error --max-time 5 http://127.0.0.1:8080/api/health/ready 2>/dev/null |
    jq '{
      schema: "weave-backend-readiness-diagnostic-v1",
      status: (.status // "unavailable"),
      checks: [(.checks // [])[] | {
        key: (.key // "unknown"),
        status: (.status // "unknown"),
        readiness: (.readiness // "unknown"),
        actionRequired: ((.action // "") != "")
      }]
    }' >"${target}"
  local status=${PIPESTATUS[0]}
  set -e
  if ((status != 0)) || [[ ! -s "${target}" ]]; then
    printf '{"schema":"weave-backend-readiness-diagnostic-v1","status":"unavailable"}\n' >"${target}"
  fi
}

write_support_bundle() {
  local target_dir="$1"
  mkdir -p "${target_dir}"
  set +e
  WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=true \
    WEAVE_SUPPORT_BUNDLE_LOG_LINES="${WEAVE_LIVE_STACK_SUPPORT_BUNDLE_LOG_LINES:-80}" \
    bash "${ROOT_DIR}/support-bundle.sh" "${WEAVE_PROFILE:-e2e}" "${target_dir}" >"${target_dir}/support-bundle-command.txt" 2>&1
  local status=$?
  set -e
  redact_stream <"${target_dir}/support-bundle-command.txt" |
    redact_evidence_paths >"${target_dir}/support-bundle-command.redacted.txt"
  mv "${target_dir}/support-bundle-command.redacted.txt" "${target_dir}/support-bundle-command.txt"
  printf '%s\n' "${status}" >"${target_dir}/support-bundle-exit-status.txt"
  find "${target_dir}" -maxdepth 1 -name 'weave-compose-support-*.tar.gz' -print -quit
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
  local schema_init_diagnostic="${OUTPUT_DIR}/one-shot/schema-init.log"
  local native_files_volume_init_diagnostic="${OUTPUT_DIR}/one-shot/native-files-volume-init.log"
  local keycloak_migration_receipt_diagnostic="${OUTPUT_DIR}/one-shot/keycloak-realm-migration-receipt-check.log"
  local runtime_state_diagnostic="${OUTPUT_DIR}/runtime/runtime-state.log"
  local runtime_state_volume_init_diagnostic="${OUTPUT_DIR}/one-shot/runtime-state-volume-init.log"
  local runtime_state_init_diagnostic="${OUTPUT_DIR}/one-shot/runtime-state-init.log"
  local keycloak_runtime_diagnostic="${OUTPUT_DIR}/runtime/keycloak.log"
  local synapse_runtime_diagnostic="${OUTPUT_DIR}/runtime/synapse.log"

  write_container_status "${container_status}"
  write_operator_check "${operator_check}" "${operator_status}"
  write_failed_markers "${failed_markers}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-schema-init" "${schema_init_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-native-files-volume-init" "${native_files_volume_init_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-keycloak-realm-migration-receipt-check" "${keycloak_migration_receipt_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-runtime-state" "${runtime_state_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-runtime-state-volume-init" "${runtime_state_volume_init_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-runtime-state-init" "${runtime_state_init_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-keycloak" "${keycloak_runtime_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-synapse" "${synapse_runtime_diagnostic}"
  write_redacted_container_diagnostic \
    "${RESOURCE_PREFIX}-backend" "${OUTPUT_DIR}/runtime/backend-startup.log"
  write_backend_readiness_diagnostic \
    "${RESOURCE_PREFIX}-backend" "${OUTPUT_DIR}/health-checks/backend-readiness.json"
  local support_bundle=""
  support_bundle="$(write_support_bundle "${OUTPUT_DIR}/support-bundle" || true)"
  write_private_raw_logs_if_requested "${OUTPUT_DIR}" "${private_status}"

  local operator_exit support_exit private_status_text bundle_reference
  operator_exit="$(cat "${operator_status}")"
  support_exit="$(cat "${OUTPUT_DIR}/support-bundle/support-bundle-exit-status.txt")"
  private_status_text="$(cat "${private_status}" | redact_stream)"
  if [[ -n "${support_bundle}" ]]; then
    bundle_reference="support-bundle/$(basename -- "${support_bundle}")"
  else
    bundle_reference="support bundle not written; see support-bundle/support-bundle-command.txt"
  fi

  cat >"${OUTPUT_DIR}/failure-summary.md" <<MD
## Live Stack failure diagnostics

Generated UTC: ${CREATED_AT_ISO}

This directory is support-safe by default. It intentionally does not dump raw container logs.

- Container status: \`container-status.tsv\`
- Readiness check output: \`health-checks/operator-check.txt\` (exit ${operator_exit})
- Support-safe backend readiness state: \`health-checks/backend-readiness.json\`
- Failed or missing acceptance markers: \`failed-markers.json\`
- Redacted schema initializer diagnostic: \`one-shot/schema-init.log\`
- Redacted native Files volume initializer diagnostic: \`one-shot/native-files-volume-init.log\`
- Redacted Keycloak migration receipt diagnostic: \`one-shot/keycloak-realm-migration-receipt-check.log\`
- Redacted RuntimeState process diagnostic: \`runtime/runtime-state.log\`
- Redacted RuntimeState volume initializer diagnostic: \`one-shot/runtime-state-volume-init.log\`
- Redacted RuntimeState initializer diagnostic: \`one-shot/runtime-state-init.log\`
- Redacted Keycloak runtime diagnostic: \`runtime/keycloak.log\`
- Redacted Synapse runtime diagnostic: \`runtime/synapse.log\`
- Redacted backend startup diagnostic: \`runtime/backend-startup.log\`
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
    printf '  "backendReadiness": "health-checks/backend-readiness.json",\n'
    printf '  "failedMarkers": "failed-markers.json",\n'
    printf '  "schemaInitDiagnostic": "one-shot/schema-init.log",\n'
    printf '  "nativeFilesVolumeInitDiagnostic": "one-shot/native-files-volume-init.log",\n'
    printf '  "keycloakMigrationReceiptDiagnostic": "one-shot/keycloak-realm-migration-receipt-check.log",\n'
    printf '  "runtimeStateDiagnostic": "runtime/runtime-state.log",\n'
    printf '  "runtimeStateVolumeInitDiagnostic": "one-shot/runtime-state-volume-init.log",\n'
    printf '  "runtimeStateInitDiagnostic": "one-shot/runtime-state-init.log",\n'
    printf '  "keycloakRuntimeDiagnostic": "runtime/keycloak.log",\n'
    printf '  "synapseRuntimeDiagnostic": "runtime/synapse.log",\n'
    printf '  "backendStartupDiagnostic": "runtime/backend-startup.log",\n'
    printf '  "supportBundleReference": %s,\n' "$(printf '%s' "${bundle_reference}" | json_escape)"
    printf '  "privateRawLogs": %s\n' "$(printf '%s' "${private_status_text}" | json_escape)"
    printf '}\n'
  } >"${OUTPUT_DIR}/failure-summary.json"

  scan_for_unredacted_secrets "${OUTPUT_DIR}"
  log "Support-safe live stack failure diagnostics written to ${OUTPUT_DIR}"
}

main "$@"
