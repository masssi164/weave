#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
DEFAULT_CADDY_TLS_CA_FILE="${ROOT_DIR}/01-infrastructure/.generated/caddy/certs/weave-local-ca.pem"
CADDY_TLS_CA_FILE=""

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

normalize_repo_local_cert_path_var() {
  local name="$1"
  local value="${!name:-}"
  local repo_generated_suffix="/weave-workspace/01-infrastructure/.generated/caddy/certs/"

  if [[ -z "${value}" || "${value}" != *"${repo_generated_suffix}"* ]]; then
    return
  fi

  export "${name}=${ROOT_DIR}/01-infrastructure/.generated/caddy/certs/$(basename -- "${value}")"
}

load_bootstrap_env() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
    normalize_repo_local_cert_path_var TF_VAR_caddy_tls_cert_file
    normalize_repo_local_cert_path_var TF_VAR_caddy_tls_key_file
    normalize_repo_local_cert_path_var TF_VAR_caddy_tls_ca_file
  fi
}

public_port_suffix() {
  local scheme="${TF_VAR_public_scheme:-https}"
  local port="${TF_VAR_proxy_host_port:-443}"

  if [[ "${scheme}" == "http" && "${port}" == "80" ]] || [[ "${scheme}" == "https" && "${port}" == "443" ]]; then
    printf ''
    return
  fi

  printf ':%s' "${port}"
}

public_url() {
  local subdomain="$1"
  printf '%s://%s.%s%s' \
    "${TF_VAR_public_scheme:-https}" \
    "${subdomain}" \
    "${TF_VAR_tenant_domain:?Expected TF_VAR_tenant_domain in env or bootstrap env}" \
    "$(public_port_suffix)"
}

host_port_from_url() {
  local url="$1"
  local host_port

  host_port="${url#*://}"
  host_port="${host_port%%/*}"
  if [[ "${host_port}" != *:* ]]; then
    case "${url%%://*}" in
      https) host_port="${host_port}:443" ;;
      http) host_port="${host_port}:80" ;;
    esac
  fi

  printf '%s\n' "${host_port}"
}

curl_form() {
  local url="$1"
  shift
  local host_port

  host_port="$(host_port_from_url "${url}")"
  curl --silent --show-error --fail \
    --cacert "${CADDY_TLS_CA_FILE}" \
    --resolve "${host_port}:127.0.0.1" \
    -H 'content-type: application/x-www-form-urlencoded' \
    "$url" "$@"
}

curl_auth_status_to_file() {
  local token="$1"
  local method="$2"
  local url="$3"
  local output_file="$4"
  shift 4
  local host_port

  host_port="$(host_port_from_url "${url}")"
  curl --silent --show-error \
    --cacert "${CADDY_TLS_CA_FILE}" \
    --resolve "${host_port}:127.0.0.1" \
    --request "${method}" \
    -H "Authorization: Bearer ${token}" \
    -H 'content-type: application/json' \
    -o "${output_file}" \
    -w '%{http_code}' \
    "$url" "$@"
}

assert_json() {
  local json="$1"
  local jq_filter="$2"
  local description="$3"

  jq -e "${jq_filter}" >/dev/null <<<"${json}" || fail "OpenProject Boards live E2E failed: ${description}"
}

assert_support_safe_file() {
  local file="$1"
  local secret="${TF_VAR_boards_openproject_api_token:-${WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_SECRET:-}}"
  local base_url="${TF_VAR_boards_openproject_base_url:-${WEAVE_OPENPROJECT_LIVE_E2E_EXPECTED_BASE_URL:-}}"

  if [[ -n "${secret}" ]]; then
    ! grep -Fq "${secret}" "${file}" || fail "OpenProject Boards live E2E failed: response leaked OpenProject API token"
  fi
  if [[ -n "${base_url}" ]]; then
    ! grep -Fq "${base_url}" "${file}" || fail "OpenProject Boards live E2E failed: response leaked raw OpenProject base URL"
  fi
  ! grep -Eiq 'authorization|api[_-]?key|secret|password|/api/v3/|/work_packages/|/projects/' "${file}" || \
    fail "OpenProject Boards live E2E failed: response contained non-support-safe provider details"
}

mint_access_token() {
  local issuer_config
  local token_endpoint
  local token_response

  issuer_config="$(curl_form "${WEAVE_OIDC_ISSUER_URL}/.well-known/openid-configuration")"
  assert_json "${issuer_config}" ".issuer == \"${WEAVE_OIDC_ISSUER_URL}\"" "Keycloak issuer should match the public contract"
  token_endpoint="$(jq -r '.token_endpoint' <<<"${issuer_config}")"

  token_response="$(curl_form "${token_endpoint}" \
    --data-urlencode grant_type=password \
    --data-urlencode client_id="${WEAVE_OIDC_CLIENT_ID}" \
    --data-urlencode username="${WEAVE_TEST_USERNAME}" \
    --data-urlencode password="${WEAVE_TEST_PASSWORD}" \
    --data-urlencode scope='openid profile email')"
  jq -r '.access_token' <<<"${token_response}"
}

probe_preview() {
  local token="$1"
  local body_file="$2"
  curl_auth_status_to_file "${token}" GET "${WEAVE_BASE_URL}/boards/preview" "${body_file}"
}

probe_create_refusal() {
  local token="$1"
  local body_file="$2"
  curl_auth_status_to_file "${token}" POST "${WEAVE_BASE_URL}/boards/openproject:board:42/tasks" "${body_file}" \
    --data '{"columnId":"openproject:status:1","title":"live e2e write refusal"}'
}

require_command curl
require_command jq
load_bootstrap_env

CADDY_TLS_CA_FILE="${TF_VAR_caddy_tls_ca_file:-${DEFAULT_CADDY_TLS_CA_FILE}}"
[[ -f "${CADDY_TLS_CA_FILE}" ]] || fail "Expected a trusted Caddy TLS CA file at ${CADDY_TLS_CA_FILE}. Set TF_VAR_caddy_tls_ca_file explicitly or run install.sh first."

WEAVE_API_BASE_URL="${WEAVE_API_BASE_URL:-${WEAVE_BASE_URL:-$(public_url "${TF_VAR_api_subdomain:-api}")/api}}"
WEAVE_BASE_URL="${WEAVE_API_BASE_URL%/}"
WEAVE_OIDC_ISSUER_URL="${WEAVE_OIDC_ISSUER_URL:-$(public_url "${TF_VAR_auth_subdomain:-auth}")/realms/${TF_VAR_tenant_slug:-weave}}"
: "${WEAVE_OIDC_CLIENT_ID:?Expected WEAVE_OIDC_CLIENT_ID in env or bootstrap env}"
: "${WEAVE_TEST_USERNAME:?Expected WEAVE_TEST_USERNAME in env or bootstrap env}"
: "${WEAVE_TEST_PASSWORD:?Expected WEAVE_TEST_PASSWORD in env or bootstrap env}"

expected_enabled="${WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_ENABLED:-false}"
access_token="$(mint_access_token)"
[[ -n "${access_token}" && "${access_token}" != "null" ]] || fail "OpenProject Boards live E2E failed: Keycloak did not return an access token"

preview_body="$(mktemp)"
write_body="$(mktemp)"
trap 'rm -f -- "${preview_body}" "${write_body}"' EXIT

log "Checking OpenProject Boards preview through Weave API..."
preview_status="$(probe_preview "${access_token}" "${preview_body}" || true)"
assert_support_safe_file "${preview_body}"

if [[ "${expected_enabled}" == "true" ]]; then
  [[ "${preview_status}" == "200" ]] || fail "OpenProject Boards live E2E failed: expected enabled read-only preview HTTP 200, got ${preview_status}: $(cat "${preview_body}")"
  preview_json="$(cat "${preview_body}")"
  assert_json "${preview_json}" '.preview == true' "Boards preview should be marked preview=true"
  assert_json "${preview_json}" '.source == "openproject-read-sync-backend-facade"' "Boards preview must come through the Weave OpenProject read-sync facade"
  assert_json "${preview_json}" '.capabilities.provider == "openproject" and .capabilities.enabled == true' "OpenProject capabilities should be enabled"
  assert_json "${preview_json}" '.syncMetadata.provider == "openproject" and .syncMetadata.mode == "read-only-sync" and .syncMetadata.readOnly == true and .syncMetadata.contextScoped == true and .syncMetadata.supportSafe == true' "sync metadata should be read-only, context-scoped, and support-safe"
  assert_json "${preview_json}" '(.projects | length) >= 1 and (.boards | length) >= 1 and (.tasks | length) >= 1' "read-sync preview should contain provider-neutral projects, boards, and tasks"
  log "OpenProject read-only Boards preview passed through Weave API."
else
  case "${preview_status}" in
    401|403|503) ;;
    *) fail "OpenProject Boards live E2E failed: expected disabled/misconfigured/context-gated preview to fail closed with 401/403/503, got ${preview_status}: $(cat "${preview_body}")" ;;
  esac
  assert_json "$(cat "${preview_body}")" '(.code // .error // "") | tostring | startswith("boards-")' "fail-closed response should use a Boards API error code"
  log "OpenProject Boards preview failed closed support-safely with HTTP ${preview_status}."
fi

log "Checking provider write refusal through Weave API..."
write_status="$(probe_create_refusal "${access_token}" "${write_body}" || true)"
assert_support_safe_file "${write_body}"
case "${write_status}" in
  401|403|404|405|409|422|503) ;;
  *) fail "OpenProject Boards live E2E failed: provider write endpoint should be refused until audit/consent promotion, got HTTP ${write_status}: $(cat "${write_body}")" ;;
esac
log "OpenProject provider writes remain refused support-safely with HTTP ${write_status}."

log "OpenProject Boards live E2E checks passed."
