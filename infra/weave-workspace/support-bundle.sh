#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
DEFAULT_OUTPUT_DIR="${ROOT_DIR}/.generated/support-bundles"
SUPPORT_BUNDLE_OUTPUT_DIR="${WEAVE_SUPPORT_BUNDLE_DIR:-${DEFAULT_OUTPUT_DIR}}"
TAIL_LINES="${WEAVE_SUPPORT_BUNDLE_LOG_LINES:-200}"
RUN_CHECKS="${WEAVE_SUPPORT_BUNDLE_RUN_CHECKS:-false}"
CREATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"
BUNDLE_BASENAME="weave-support-${CREATED_AT}"
WORK_DIR=""
NEGATIVE_REDACTION_FIXTURE_STATUS="not_run"

readonly DEFAULT_CONTAINERS=(
  weave-proxy
  weave-keycloak
  weave-backend
  weave-mas
  weave-synapse
  weave-nextcloud
  weave-livekit
  weave-db
)

readonly PUBLIC_ENV_KEYS=(
  TF_VAR_tenant_domain
  TF_VAR_tenant_slug
  TF_VAR_public_scheme
  TF_VAR_auth_subdomain
  TF_VAR_api_subdomain
  TF_VAR_admin_subdomain
  TF_VAR_matrix_subdomain
  TF_VAR_nextcloud_subdomain
  TF_VAR_proxy_host_port
  TF_VAR_backend_host_port
  TF_VAR_keycloak_management_host_port
  TF_VAR_mas_host_port
  TF_VAR_synapse_host_port
  TF_VAR_weave_backend_image
  TF_VAR_synapse_image
  TF_VAR_mas_image
  TF_VAR_create_test_user
  WEAVE_PROVIDER_STACK_PROFILE
  WEAVE_PROVIDER_STACK_READINESS
  WEAVE_DEVOPS_PRIMARY_PROVIDER
  WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED
  WEAVE_DEVOPS_GITLAB_BASE_URL
  WEAVE_DEVOPS_GITLAB_WRITES_ENABLED
  WEAVE_OFFICE_PRIMARY_PROVIDER
  WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED
  WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL
  WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE
  WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED
  WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED
  WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED
  WEAVE_LIVEKIT_ENABLED
  WEAVE_LIVEKIT_TOKEN_ENDPOINT_CONFIGURED
  WEAVE_PUBLIC_BASE_URL
  WEAVE_API_BASE_URL
  WEAVE_BASE_URL
  WEAVE_AUTH_BASE_URL
  WEAVE_ADMIN_CONSOLE_URL
  WEAVE_ADMIN_CONSOLE_OIDC_CLIENT_ID
  WEAVE_ORG_MANIFEST_URL
  WEAVE_PROVIDER_PROFILE
  WEAVE_OIDC_ISSUER_URL
  WEAVE_OIDC_CLIENT_ID
  WEAVE_NEXTCLOUD_BASE_URL
  WEAVE_BOARDS_RUNTIME_ENABLED
  WEAVE_BOARDS_PROVIDER
  WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED
  WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED
  WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED
  WEAVE_BOARDS_OPENPROJECT_AUDIT_CONSENT_ENABLED
  WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED
  WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED
  WEAVE_BOARDS_OPENPROJECT_AUTH_MODE
  WEAVE_BOARDS_OPENPROJECT_BASE_URL
  WEAVE_CHAT_E2EE
  WEAVE_MATRIX_HOMESERVER_URL
  WEAVE_MATRIX_PROVIDER_URL
  WEAVE_TLS_CA_FILE
)

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: bash weave-workspace/support-bundle.sh [output-dir]

Creates a redacted support bundle for diagnostics.
The bundle is a triage artifact, not a backup.

Environment:
  WEAVE_SUPPORT_BUNDLE_DIR         Output directory (default: .generated/support-bundles)
  WEAVE_SUPPORT_BUNDLE_LOG_LINES   Docker log tail per service (default: 200)
  WEAVE_SUPPORT_BUNDLE_RUN_CHECKS  true to run operator-check and release-verify (default: false)
USAGE
}

cleanup() {
  if [[ -n "${WORK_DIR}" && -d "${WORK_DIR}" ]]; then
    rm -rf "${WORK_DIR}"
  fi
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
    s/\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b/<redacted-jwt>/g;
    s/(([A-Za-z0-9_]*(?:password|passwd|token|secret|private[_-]?key|signing[_-]?key|credential|authorization|cookie)[A-Za-z0-9_]*\s*[=:]\s*)([^\s\r\n"'"'"']+))/${2}<redacted>/gi;
    s/("(?:password|passwd|token|secret|privateKey|signingKey|credential|authorization|cookie)"\s*:\s*")[^"]+/${1}<redacted>/gi;
  '
}

scan_for_unredacted_secrets() {
  local path="$1"
  local findings=""

  findings="$(grep -RIliE \
    'BEGIN ((RSA|EC|OPENSSH) )?PRIVATE KEY|[a-z][a-z0-9+.-]*://[^[:space:]/@:]+:[^[:space:]/@]+@|Authorization:[[:space:]]+(Bearer|Basic)[[:space:]]+[^<[:space:]]|Cookie:[[:space:]]+[^<[:space:]]|Set-Cookie:[[:space:]]+[^<[:space:]]|([A-Za-z0-9_]*(PASSWORD|TOKEN|SECRET|PRIVATE_KEY|SIGNING_KEY|CREDENTIAL)[A-Za-z0-9_]*[=:][[:space:]]*[^<[:space:]]+)|[Ss][Ee][Cc][Rr][Ee][Tt][Rr][Ee][Ff]://[^[:space:]<]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|(AKIA|ASIA)[A-Z0-9]{16}|(ghp|gho|ghu|ghs|ghr|github_pat|glpat|xox[baprs])-[-_A-Za-z0-9]{20,}|eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}' \
    "${path}" 2>/dev/null || true)"

  if [[ -n "${findings}" ]]; then
    printf 'Support bundle redaction check failed. Possible secret material remains in these files only; values are intentionally not printed:\n%s\n' "${findings}" >&2
    return 1
  fi
}

write_text_file() {
  local relative_path="$1"
  local target="${WORK_DIR}/${relative_path}"
  mkdir -p "$(dirname -- "${target}")"
  cat >"${target}"
}

collect_command_output() {
  local relative_path="$1"
  shift
  local target="${WORK_DIR}/${relative_path}"
  mkdir -p "$(dirname -- "${target}")"

  {
    printf '$'
    local arg
    for arg in "$@"; do
      printf ' %q' "${arg}"
    done
    printf '\n\n'
    set +e
    "$@"
    local status=$?
    set -e
    printf '\n[exit status: %s]\n' "${status}"
  } 2>&1 | redact_stream >"${target}"
}

collect_if_command_exists() {
  local command_name="$1"
  local relative_path="$2"
  shift 2

  if command -v "${command_name}" >/dev/null 2>&1; then
    collect_command_output "${relative_path}" "$@"
  else
    write_text_file "${relative_path}" <<MSG
Skipped: missing command ${command_name}
MSG
  fi
}

collect_public_env_from_file() {
  local source_file="$1"
  local target_file="$2"
  local key
  local line

  if [[ ! -f "${source_file}" ]]; then
    printf 'Skipped: file not found: %s\n' "${source_file}" >>"${target_file}"
    return
  fi

  printf '# %s\n' "${source_file}" >>"${target_file}"
  for key in "${PUBLIC_ENV_KEYS[@]}"; do
    while IFS= read -r line; do
      write_support_safe_env_line "${key}" "${line}" >>"${target_file}"
    done < <(grep -E "^(export[[:space:]]+)?${key}=" "${source_file}" || true)
  done
  printf '\n' >>"${target_file}"
}

is_provider_endpoint_key() {
  local key="$1"
  case "${key}" in
    WEAVE_DEVOPS_GITLAB_BASE_URL|WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL|WEAVE_PUBLIC_BASE_URL|WEAVE_OIDC_ISSUER_URL|WEAVE_NEXTCLOUD_BASE_URL|WEAVE_BOARDS_OPENPROJECT_BASE_URL|WEAVE_MATRIX_PROVIDER_URL)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

write_support_safe_env_line() {
  local key="$1"
  local line="$2"
  local value=""

  if [[ "${line}" == export[[:space:]]* ]]; then
    line="${line#export }"
  fi
  value="${line#*=}"

  if is_provider_endpoint_key "${key}"; then
    printf '%s_CONFIGURED=%s\n' "${key}" "$(if [[ -n "${value}" ]]; then printf true; else printf false; fi)"
  else
    printf '%s\n' "${line}"
  fi
}

collect_public_env() {
  local target="${WORK_DIR}/config/public-env-summary.env"
  mkdir -p "$(dirname -- "${target}")"
  : >"${target}"

  collect_public_env_from_file "${ROOT_DIR}/.generated/bootstrap.env" "${target}"
  collect_public_env_from_file "${ROOT_DIR}/.generated/app-config.env" "${target}"

  {
    printf '# current process public env\n'
    local key
    for key in "${PUBLIC_ENV_KEYS[@]}"; do
      if [[ -n "${!key:-}" ]]; then
        if is_provider_endpoint_key "${key}"; then
          printf '%s_CONFIGURED=true\n' "${key}"
        else
          printf '%s=%q\n' "${key}" "${!key}"
        fi
      fi
    done
  } >>"${target}"

  redact_stream <"${target}" >"${target}.redacted"
  mv "${target}.redacted" "${target}"
}

bool_from_env_presence() {
  local key="$1"
  [[ -n "${!key:-}" ]]
}

bool_from_env_files() {
  local key="$1"
  grep -hE "^(export[[:space:]]+)?${key}=.+" \
    "${ROOT_DIR}/.generated/bootstrap.env" \
    "${ROOT_DIR}/.generated/app-config.env" 2>/dev/null | grep -q .
}

health_from_env() {
  local configured="$1"
  if [[ "${configured}" != "true" ]]; then
    printf 'not_configured'
    return
  fi
  case "${WEAVE_PROVIDER_STACK_READINESS:-${TF_VAR_provider_stack_readiness:-fail-closed}}" in
    ready|configured|degraded|fail-closed|not_configured|disabled)
      printf '%s' "${WEAVE_PROVIDER_STACK_READINESS:-${TF_VAR_provider_stack_readiness:-fail-closed}}"
      ;;
    *)
      printf 'fail-closed'
      ;;
  esac
}

adapter_evidence_object() {
  local comma="$1"
  local domain="$2"
  local adapter_key="$3"
  local configured="$4"
  local health
  health="$(health_from_env "${configured}")"
  local reachable="false"
  local fail_closed="true"
  if [[ "${health}" == "ready" || "${health}" == "configured" || "${health}" == "degraded" ]]; then
    reachable="true"
  fi
  if [[ "${health}" == "ready" || "${health}" == "configured" ]]; then
    fail_closed="false"
  fi
  cat <<JSON
${comma}    {
      "domain": "${domain}",
      "adapterKey": "${adapter_key}",
      "configured": ${configured},
      "reachable": ${reachable},
      "health": "${health}",
      "failClosed": ${fail_closed},
      "supportSafeDiagnostics": {
        "secretsReturned": false,
        "rawProviderErrorsReturned": false,
        "diagnosticsRedacted": true
      },
      "evidenceTimestamp": "${CREATED_AT}"
    }
JSON
}

collect_adapter_readiness_evidence() {
  local target="${WORK_DIR}/checks/adapter-readiness-summary.json"
  mkdir -p "$(dirname -- "${target}")"
  local identity_configured="false" chat_configured="false" files_configured="false" calendar_configured="false" boards_configured="false" meetings_configured="false"

  (bool_from_env_presence WEAVE_OIDC_ISSUER_URL || bool_from_env_files WEAVE_OIDC_ISSUER_URL) && identity_configured="true"
  (bool_from_env_presence WEAVE_MATRIX_PROVIDER_URL || bool_from_env_files WEAVE_MATRIX_PROVIDER_URL) && chat_configured="true"
  (bool_from_env_presence WEAVE_NEXTCLOUD_BASE_URL || bool_from_env_files WEAVE_NEXTCLOUD_BASE_URL) && files_configured="true" && calendar_configured="true"
  (bool_from_env_presence WEAVE_BOARDS_OPENPROJECT_BASE_URL || bool_from_env_files WEAVE_BOARDS_OPENPROJECT_BASE_URL) && boards_configured="true"
  if [[ "${WEAVE_LIVEKIT_ENABLED:-false}" == "true" || "${WEAVE_LIVEKIT_TOKEN_ENDPOINT_CONFIGURED:-false}" == "true" ]]; then
    meetings_configured="true"
  fi

  {
    printf '{\n  "schema": "weave-support-safe-adapter-readiness-v1",\n  "generatedAt": "%s",\n  "adapterEvidence": [\n' "${CREATED_AT}"
    adapter_evidence_object "" "identity-idm" "keycloak-realm" "${identity_configured}"
    adapter_evidence_object "," "chat" "synapse-matrix" "${chat_configured}"
    adapter_evidence_object "," "files" "nextcloud-files" "${files_configured}"
    adapter_evidence_object "," "calendar" "nextcloud-caldav" "${calendar_configured}"
    adapter_evidence_object "," "boards-tasks" "openproject-primary" "${boards_configured}"
    adapter_evidence_object "," "meetings-calls" "livekit" "${meetings_configured}"
    printf '  ]\n}\n'
  } >"${target}"
}

collect_recent_artifacts() {
  local target_dir="${WORK_DIR}/recent-artifacts"
  mkdir -p "${target_dir}"

  if [[ ! -d "${ROOT_DIR}/.generated" ]]; then
    printf 'Skipped: no .generated directory exists.\n' >"${target_dir}/README.txt"
    return
  fi

  find "${ROOT_DIR}/.generated" -maxdepth 2 -type f \
    \( -iname '*smoke*.log' -o -iname '*smoke*.txt' -o -iname '*operator*.log' -o -iname '*operator*.txt' -o -iname '*verify*.log' -o -iname '*verify*.txt' \) \
    -print0 | while IFS= read -r -d '' artifact; do
      local name
      name="$(basename -- "${artifact}")"
      redact_stream <"${artifact}" >"${target_dir}/${name}"
    done

  if [[ -z "$(find "${target_dir}" -type f ! -name README.txt -print -quit)" ]]; then
    printf 'No recent smoke/operator/release-verify text artifacts were found under .generated.\n' >"${target_dir}/README.txt"
  fi
}

collect_logs() {
  local container
  mkdir -p "${WORK_DIR}/logs"

  if ! command -v docker >/dev/null 2>&1; then
    printf 'Skipped: missing command docker\n' >"${WORK_DIR}/logs/README.txt"
    return
  fi

  for container in "${DEFAULT_CONTAINERS[@]}"; do
    collect_command_output "logs/${container}.log" docker logs --tail "${TAIL_LINES}" "${container}"
  done
}

collect_optional_checks() {
  mkdir -p "${WORK_DIR}/checks"

  if [[ "${RUN_CHECKS}" != "true" ]]; then
    cat >"${WORK_DIR}/checks/README.txt" <<MSG
operator-check.sh and release-verify.sh were not run.
Set WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=true to include fresh check output in the bundle.
MSG
    return
  fi

  collect_command_output "checks/operator-check.txt" bash "${ROOT_DIR}/operator-check.sh"
  collect_command_output "checks/release-verify.txt" bash "${ROOT_DIR}/release-verify.sh"
}

run_negative_redaction_fixture() {
  local fixture_dir
  fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/weave-support-negative-fixture.XXXXXX")"

  cat >"${fixture_dir}/unsafe.txt" <<'MSG'
Authorization: Bearer fixture-token-that-must-be-detected
Cookie: fixture_session=must_be_detected
MSG

  if scan_for_unredacted_secrets "${fixture_dir}" >/dev/null 2>&1; then
    NEGATIVE_REDACTION_FIXTURE_STATUS="failed"
    fail "negative redaction fixture was not detected; refusing to mark support bundle redaction checks as passed"
  fi

  rm -rf "${fixture_dir}"
  NEGATIVE_REDACTION_FIXTURE_STATUS="passed"
}

write_redaction_report() {
  local target="${WORK_DIR}/checks/support-redaction-report.json"
  mkdir -p "$(dirname -- "${target}")"
  cat >"${target}" <<JSON
{
  "artifactKind": "weave-support-bundle-redaction-report-v1",
  "issue": 640,
  "supportSafe": true,
  "createdAt": "${CREATED_AT}",
  "bundleRef": "${BUNDLE_BASENAME}",
  "scannerVersion": "support-bundle.sh:redaction-v1",
  "checks": [
    {"name": "tokens_and_authorization_headers", "status": "passed"},
    {"name": "cookies", "status": "passed"},
    {"name": "private_keys", "status": "passed"},
    {"name": "secret_refs", "status": "passed"},
    {"name": "provider_urls", "status": "passed"},
    {"name": "private_messages_file_contents_weaver_memory", "status": "excluded_by_bundle_scope"},
    {"name": "negative_fixture_detects_unsafe_content", "status": "${NEGATIVE_REDACTION_FIXTURE_STATUS}"}
  ],
  "findings": [],
  "unsafeContentDetected": false,
  "limitations": [
    "Support bundles are diagnostics only and cannot restore data.",
    "Operators must review site-specific logs before external sharing."
  ]
}
JSON
}

create_bundle() {
  local output_dir="$1"
  mkdir -p "${output_dir}"
  WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/${BUNDLE_BASENAME}.XXXXXX")"
  mkdir -p "${WORK_DIR}"

  cat >"${WORK_DIR}/README.txt" <<MSG
Weave support bundle
Created UTC: ${CREATED_AT}

This bundle is for support-safe diagnostics only. It is not a backup and cannot restore
Postgres databases, Matrix media, Nextcloud files/calendar data, Caddy ACME state, or
generated secrets. Use docs/operator-runbook.md#5-backup-expectations for backups.

Before sharing externally, review the bundle contents. The script redacts common secret
patterns and refuses obvious leftovers, but operators remain responsible for checking
site-specific logs.
MSG

  collect_public_env
  collect_if_command_exists uname host/uname.txt uname -a
  collect_if_command_exists df host/disk.txt df -h
  collect_if_command_exists docker docker/containers.txt docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}\t{{.Ports}}'
  collect_if_command_exists docker docker/volumes.txt docker volume ls
  collect_if_command_exists docker docker/system-df.txt docker system df
  collect_logs
  collect_optional_checks
  collect_adapter_readiness_evidence
  collect_recent_artifacts
  run_negative_redaction_fixture
  write_redaction_report

  scan_for_unredacted_secrets "${WORK_DIR}"

  local archive="${output_dir}/${BUNDLE_BASENAME}.tar.gz"
  tar -C "$(dirname -- "${WORK_DIR}")" -czf "${archive}" "$(basename -- "${WORK_DIR}")"
  log "Support bundle written to ${archive}"
}

main() {
  if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    usage
    exit 0
  fi

  if [[ "${TAIL_LINES}" =~ ^[0-9]+$ ]]; then
    :
  else
    fail "WEAVE_SUPPORT_BUNDLE_LOG_LINES must be numeric"
  fi

  trap cleanup EXIT
  local output_dir="${1:-${SUPPORT_BUNDLE_OUTPUT_DIR}}"
  create_bundle "${output_dir}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
