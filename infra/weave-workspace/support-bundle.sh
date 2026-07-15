#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${ROOT_DIR}/lib/runtime-namespace.sh"
WORKSPACE_GENERATED_DIR="$(weave_workspace_generated_dir "${ROOT_DIR}")"
DEFAULT_OUTPUT_DIR="${WORKSPACE_GENERATED_DIR}/support-bundles"
SUPPORT_BUNDLE_OUTPUT_DIR="${WEAVE_SUPPORT_BUNDLE_DIR:-${DEFAULT_OUTPUT_DIR}}"
TAIL_LINES="${WEAVE_SUPPORT_BUNDLE_LOG_LINES:-200}"
RUN_CHECKS="${WEAVE_SUPPORT_BUNDLE_RUN_CHECKS:-false}"
CREATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"
BUNDLE_BASENAME="weave-support-${CREATED_AT}"
WORK_DIR=""
NEGATIVE_REDACTION_FIXTURE_STATUS="not_run"

# Also consumed by live-stack-failure-diagnostics.sh when this file is sourced.
# shellcheck disable=SC2034
readonly DEFAULT_CONTAINERS=(
  "$(weave_container_name proxy)"
  "$(weave_container_name keycloak)"
  "$(weave_container_name backend)"
  "$(weave_container_name mcp-server)"
  "$(weave_container_name mas)"
  "$(weave_container_name synapse)"
  "$(weave_container_name nextcloud)"
  "$(weave_container_name mailpit)"
  "$(weave_container_name livekit)"
  "$(weave_container_name db)"
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
  WEAVE_CHAT_PROVIDER
  WEAVE_CHAT_STORAGE_MODE
  WEAVE_CHAT_MATRIX_APPSERVICE_CONFIGURED
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
  WEAVE_PROVIDER_HEALTH_BEARER_TOKEN
                                   Short-lived owner/admin/operator token used only
                                   for the authenticated cached-health route.
  WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE
                                   Previously captured provider-capability-health-v1
                                   response or support-safe
                                   weave.provider-health-metrics-summary.v1;
                                   used when the workflow stages evidence.
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

  collect_public_env_from_file "${WORKSPACE_GENERATED_DIR}/bootstrap.env" "${target}"
  collect_public_env_from_file "${WORKSPACE_GENERATED_DIR}/app-config.env" "${target}"

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
    "${WORKSPACE_GENERATED_DIR}/bootstrap.env" \
    "${WORKSPACE_GENERATED_DIR}/app-config.env" 2>/dev/null | grep -q .
}

env_or_file_equals() {
  local key="$1"
  local expected="$2"

  if [[ "${!key:-}" == "${expected}" ]]; then
    return 0
  fi

  grep -hE "^(export[[:space:]]+)?${key}=${expected}$" \
    "${WORKSPACE_GENERATED_DIR}/bootstrap.env" \
    "${WORKSPACE_GENERATED_DIR}/app-config.env" 2>/dev/null | grep -q .
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
  if (bool_from_env_presence WEAVE_MATRIX_PROVIDER_URL || bool_from_env_files WEAVE_MATRIX_PROVIDER_URL) &&
    env_or_file_equals WEAVE_CHAT_PROVIDER matrix-synapse &&
    env_or_file_equals WEAVE_CHAT_STORAGE_MODE jdbc &&
    env_or_file_equals WEAVE_CHAT_MATRIX_APPSERVICE_CONFIGURED true; then
    chat_configured="true"
  fi
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

write_provider_health_collection_state() {
  local target="$1"
  local status="$2"
  cat >"${target}" <<JSON
{
  "schemaVersion": "weave-support-provider-capability-health-evidence-v1",
  "collectionStatus": "${status}",
  "authenticatedRouteRequired": true,
  "sourceSchemaRequired": "provider-capability-health-v1",
  "stagedEvidenceSourceSchemasAccepted": [
    "provider-capability-health-v1",
    "weave.provider-health-metrics-summary.v1"
  ],
  "rawProviderPayloadIncluded": false,
  "supportSafe": true
}
JSON
}

sanitize_provider_capability_health() {
  local source_file="$1"
  local target_file="$2"

  python3 - "${source_file}" "${target_file}" <<'PY'
import datetime as dt
import json
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
top_keys = {"schemaVersion", "generatedAt", "supportSafe", "capabilities"}
capability_keys = {
    "capability", "state", "supportSafeCode", "correlationRef", "observedAt",
    "nextProbeAt", "backoffUntil", "cachedAgeSeconds", "stale",
    "consecutiveFailures", "probeLatencyMillis", "readinessTransitions",
}
safe_token = re.compile(r"^[A-Za-z0-9._:-]{1,160}$")

def timestamp(value, nullable=False):
    if value is None and nullable:
        return None
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError("invalid timestamp")
    dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value

def nonnegative(value, nullable=False):
    if value is None and nullable:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError("invalid nonnegative integer")
    return value

data = json.loads(source.read_text(encoding="utf-8"))
if not isinstance(data, dict) or set(data) != top_keys:
    raise ValueError("unexpected top-level fields")
if data["schemaVersion"] != "provider-capability-health-v1" or data["supportSafe"] is not True:
    raise ValueError("unsupported or unsafe source schema")
if not isinstance(data["capabilities"], list):
    raise ValueError("capabilities must be an array")

capabilities = []
seen = set()
for item in data["capabilities"]:
    if not isinstance(item, dict) or set(item) != capability_keys:
        raise ValueError("unexpected capability fields")
    capability = item["capability"]
    if capability not in {"chat", "files", "calendar"} or capability in seen:
        raise ValueError("unexpected or duplicate capability")
    seen.add(capability)
    if item["state"] not in {"available", "degraded", "unavailable"}:
        raise ValueError("invalid canonical state")
    for key in ("supportSafeCode", "correlationRef"):
        if not isinstance(item[key], str) or not safe_token.fullmatch(item[key]):
            raise ValueError("unsafe diagnostic reference")
    if not isinstance(item["stale"], bool):
        raise ValueError("stale must be boolean")
    capabilities.append({
        "capability": capability,
        "state": item["state"],
        "supportSafeCode": item["supportSafeCode"],
        "correlationRef": item["correlationRef"],
        "observedAt": timestamp(item["observedAt"], nullable=True),
        "nextProbeAt": timestamp(item["nextProbeAt"]),
        "backoffUntil": timestamp(item["backoffUntil"], nullable=True),
        "cachedAgeSeconds": nonnegative(item["cachedAgeSeconds"], nullable=True),
        "stale": item["stale"],
        "consecutiveFailures": nonnegative(item["consecutiveFailures"]),
        "probeLatencyMillis": nonnegative(item["probeLatencyMillis"]),
        "readinessTransitions": nonnegative(item["readinessTransitions"]),
    })

if seen != {"chat", "files", "calendar"}:
    raise ValueError("cached health evidence must cover every release-blocking provider capability")

output = {
    "schemaVersion": "weave-support-provider-capability-health-evidence-v1",
    "collectionStatus": "collected",
    "authenticatedRouteRequired": True,
    "sourceSchema": data["schemaVersion"],
    "generatedAt": timestamp(data["generatedAt"]),
    "capabilities": capabilities,
    "rawProviderPayloadIncluded": False,
    "supportSafe": True,
}
target.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

sanitize_provider_health_metrics_summary() {
  local source_file="$1"
  local target_file="$2"

  python3 - "${source_file}" "${target_file}" <<'PY'
import datetime as dt
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
top_keys = {
    "schemaVersion", "supportSafe", "source", "providerProbeTriggered",
    "overall", "observedAtUtc", "cachedResultAgeSeconds", "capabilities",
    "details", "rawMetricPayloadIncluded",
}
capability_keys = {"chat", "files", "calendar"}
detail_keys = {"files", "calendar"}
detail_value_keys = {
    "cachedResultAgeSeconds", "consecutiveFailures",
    "backoffUntilEpochSeconds", "readinessTransitions",
}
canonical_states = {"available", "degraded", "unavailable"}

def timestamp(value):
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError("invalid timestamp")
    dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    return value

def nonnegative(value):
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError("invalid nonnegative integer")
    return value

data = json.loads(source.read_text(encoding="utf-8"))
if not isinstance(data, dict) or set(data) != top_keys:
    raise ValueError("unexpected top-level fields")
if data["schemaVersion"] != "weave.provider-health-metrics-summary.v1":
    raise ValueError("unsupported source schema")
if data["supportSafe"] is not True:
    raise ValueError("source is not support-safe")
if data["source"] != "loopback-actuator-cached-metrics":
    raise ValueError("unsupported metrics source")
if data["providerProbeTriggered"] is not False:
    raise ValueError("provider probe execution is not support-safe")
if data["rawMetricPayloadIncluded"] is not False:
    raise ValueError("raw metric payload is not support-safe")
if data["overall"] not in canonical_states:
    raise ValueError("invalid overall state")
if not isinstance(data["capabilities"], dict) or set(data["capabilities"]) != capability_keys:
    raise ValueError("unexpected capability fields")
for state in data["capabilities"].values():
    if state not in canonical_states:
        raise ValueError("invalid capability state")
if not isinstance(data["details"], dict) or set(data["details"]) != detail_keys:
    raise ValueError("unexpected detail fields")

details = {}
for capability in sorted(detail_keys):
    item = data["details"][capability]
    if not isinstance(item, dict) or set(item) != detail_value_keys:
        raise ValueError("unexpected metric detail fields")
    details[capability] = {
        "cachedResultAgeSeconds": nonnegative(item["cachedResultAgeSeconds"]),
        "consecutiveFailures": nonnegative(item["consecutiveFailures"]),
        "backoffUntilEpochSeconds": nonnegative(item["backoffUntilEpochSeconds"]),
        "readinessTransitions": nonnegative(item["readinessTransitions"]),
    }

cached_age = nonnegative(data["cachedResultAgeSeconds"])
if cached_age != max(item["cachedResultAgeSeconds"] for item in details.values()):
    raise ValueError("summary cached age does not match capability details")
states = set(data["capabilities"].values())
expected_overall = (
    "unavailable" if "unavailable" in states
    else "degraded" if "degraded" in states
    else "available"
)
if data["overall"] != expected_overall:
    raise ValueError("overall state does not match capabilities")

output = {
    "schemaVersion": "weave-support-provider-health-metrics-summary-evidence-v1",
    "collectionStatus": "collected",
    "authenticatedRouteRequired": False,
    "sourceSchema": data["schemaVersion"],
    "source": data["source"],
    "providerProbeTriggered": False,
    "overall": data["overall"],
    "observedAtUtc": timestamp(data["observedAtUtc"]),
    "cachedResultAgeSeconds": cached_age,
    "capabilities": data["capabilities"],
    "details": details,
    "rawMetricPayloadIncluded": False,
    "supportSafe": True,
}
target.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

collect_provider_capability_health() {
  local target="${WORK_DIR}/checks/provider-capability-health.json"
  local source_file=""
  local fetched_file=""
  local staged_evidence="false"
  mkdir -p "$(dirname -- "${target}")"

  if [[ -n "${WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE:-}" && -f "${WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE}" ]]; then
    source_file="${WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE}"
    staged_evidence="true"
  elif [[ -n "${WEAVE_PROVIDER_HEALTH_BEARER_TOKEN:-}" && -n "${WEAVE_BASE_URL:-}" ]]; then
    fetched_file="$(mktemp)"
    local -a curl_args=(
      --silent --show-error --fail
      --connect-timeout 5 --max-time 15
      -H "Authorization: Bearer ${WEAVE_PROVIDER_HEALTH_BEARER_TOKEN}"
    )
    if [[ -n "${WEAVE_TLS_CA_FILE:-}" && -f "${WEAVE_TLS_CA_FILE}" ]]; then
      curl_args+=(--cacert "${WEAVE_TLS_CA_FILE}")
    fi
    if curl "${curl_args[@]}" "${WEAVE_BASE_URL%/}/v1/admin/provider-capability-health" >"${fetched_file}"; then
      source_file="${fetched_file}"
    else
      rm -f "${fetched_file}"
      write_provider_health_collection_state "${target}" "authenticated_fetch_failed"
      return
    fi
  else
    write_provider_health_collection_state "${target}" "not_collected"
    return
  fi

  if ! sanitize_provider_capability_health "${source_file}" "${target}" 2>/dev/null; then
    if [[ "${staged_evidence}" != "true" ]] || \
      ! sanitize_provider_health_metrics_summary "${source_file}" "${target}" 2>/dev/null; then
      write_provider_health_collection_state "${target}" "rejected_unsafe_or_invalid_source"
    fi
  fi
  [[ -z "${fetched_file}" ]] || rm -f "${fetched_file}"
}

collect_nextcloud_auth_security_audit() {
  local target="${WORK_DIR}/checks/nextcloud-auth-security-audit.json"
  local audit_script="${ROOT_DIR}/nextcloud-auth-security-audit.sh"
  if [[ ! -f "${audit_script}" || ! -x "${audit_script}" || ! -x "$(command -v docker 2>/dev/null || true)" ]]; then
    cat >"${target}" <<'JSON'
{"schemaVersion":"weave-nextcloud-auth-security-audit-v1","collectionStatus":"not_available","requestClassifications":[],"backendActorAttribution":{"configured":false,"failureObserved":false,"failureEvents":0},"rawAddressesIncluded":false,"actorIdentifiersIncluded":false,"rawProviderPayloadIncluded":false,"supportSafe":true}
JSON
    return
  fi
  if ! bash "${audit_script}" --output "${target}" >/dev/null 2>&1; then
    cat >"${target}" <<'JSON'
{"schemaVersion":"weave-nextcloud-auth-security-audit-v1","collectionStatus":"collection_failed","requestClassifications":[],"backendActorAttribution":{"configured":false,"failureObserved":false,"failureEvents":0},"rawAddressesIncluded":false,"actorIdentifiersIncluded":false,"rawProviderPayloadIncluded":false,"supportSafe":true}
JSON
  fi
}

collect_recent_artifacts() {
  local target_dir="${WORK_DIR}/recent-artifacts"
  mkdir -p "${target_dir}"

  if [[ ! -d "${WORKSPACE_GENERATED_DIR}" ]]; then
    printf '{"schemaVersion":"weave-recent-diagnostic-artifact-summary-v1","artifactCount":0,"contentSetSha256":null,"rawContentsIncluded":false,"supportSafe":true}\n' >"${target_dir}/summary.json"
    return
  fi

  local hashes_file count aggregate
  hashes_file="$(mktemp)"
  find "${WORKSPACE_GENERATED_DIR}" -maxdepth 2 -type f \
    \( -iname '*smoke*.log' -o -iname '*smoke*.txt' -o -iname '*operator*.log' -o -iname '*operator*.txt' -o -iname '*verify*.log' -o -iname '*verify*.txt' \) \
    -print0 | while IFS= read -r -d '' artifact; do
      shasum -a 256 "${artifact}" | awk '{print $1}'
    done | sort >"${hashes_file}"
  count="$(wc -l <"${hashes_file}" | tr -d '[:space:]')"
  aggregate=""
  if ((count > 0)); then
    aggregate="$(shasum -a 256 "${hashes_file}" | awk '{print $1}')"
  fi
  rm -f "${hashes_file}"

  python3 - "${target_dir}/summary.json" "${count}" "${aggregate}" <<'PY'
import json
import sys
from pathlib import Path
target, count, aggregate = sys.argv[1:]
Path(target).write_text(json.dumps({
    "schemaVersion": "weave-recent-diagnostic-artifact-summary-v1",
    "artifactCount": int(count),
    "contentSetSha256": aggregate or None,
    "rawContentsIncluded": False,
    "supportSafe": True,
}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

collect_logs() {
  mkdir -p "${WORK_DIR}/logs"
  cat >"${WORK_DIR}/logs/README.txt" <<MSG
Raw service/provider logs are excluded from the shareable support bundle because
generic redaction cannot prove removal of usernames, display names, filenames,
room/event IDs, client addresses, or provider response content. The requested
tail limit (${TAIL_LINES}) is retained as a compatibility input but is not used.

Use checks/provider-capability-health.json and
checks/nextcloud-auth-security-audit.json for allowlisted cached health and
authentication-source evidence. Operators may inspect raw logs locally, but
must not attach them without a separate site-specific privacy review.
MSG
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
    {"name": "matrix_appservice_tokens_and_registration", "status": "excluded_by_bundle_scope"},
    {"name": "chat_e2e_proof_token_and_run_binding", "status": "excluded_by_bundle_scope"},
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
  collect_provider_capability_health
  collect_nextcloud_auth_security_audit
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
