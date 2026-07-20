#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2034,SC2154

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
# Reuse run-namespace ownership, Keycloak Admin API, and private-path guards.
# shellcheck source=infra/weave-workspace/isolated-e2e-identities.sh
source "${SCRIPT_DIR}/isolated-e2e-identities.sh"

EVIDENCE_PATH="${WEAVE_E2E_MCP_WORKLOAD_EVIDENCE_PATH:-}"
PRIVATE_STATE_DIR=""
ADMIN_ACCESS_TOKEN=""
KEYCLOAK_API_BASE=""
WEAVE_APP_INTERNAL_ID=""
WEAVE_MCP_INTERNAL_ID=""
CLIENT_RESTORE_PENDING="false"

fail() {
  printf 'ISOLATED_E2E_MCP_WORKLOAD_ERROR %s\n' "$*" >&2
  exit 1
}

usage_mcp_workload() {
  cat <<'EOF'
Usage: isolated-e2e-mcp-workload.sh --run-id ID [options]

Proves the exact MCP resource, supported Keycloak Standard Token Exchange V2,
workload credential rotation, and support-safe claim boundaries. The script
refuses persistent dogfood and may run only against a disposable isolated stack.

Options:
  --run-id ID                 Stable ID used by identity prepare/provision.
  --output-root PATH          Private run artifact root.
  --credentials-env PATH      Prepared private identity credential env.
  --startup-env PATH          Prepared isolated stack/OpenTofu env.
  --identity-manifest PATH    Provisioned support-safe identity evidence.
  --stack-bootstrap-env PATH  Private bootstrap env written by install.sh.
  --evidence PATH             Support-safe output JSON.
EOF
}

parse_mcp_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --run-id) RUN_ID="${2:-}"; shift 2 ;;
      --output-root) OUTPUT_ROOT="${2:-}"; shift 2 ;;
      --credentials-env) CREDENTIAL_ENV_PATH="${2:-}"; shift 2 ;;
      --startup-env) STARTUP_ENV_PATH="${2:-}"; shift 2 ;;
      --identity-manifest) IDENTITY_MANIFEST_PATH="${2:-}"; shift 2 ;;
      --stack-bootstrap-env) STACK_BOOTSTRAP_ENV="${2:-}"; shift 2 ;;
      --evidence) EVIDENCE_PATH="${2:-}"; shift 2 ;;
      -h|--help) usage_mcp_workload; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
}

resolve_client_id() {
  local client_id="$1" clients internal_id
  clients="$(request GET "${KEYCLOAK_API_BASE}/clients?clientId=$(encode "${client_id}")" "${ADMIN_ACCESS_TOKEN}")"
  internal_id="$(find_exact_id "${clients}" clientId "${client_id}")"
  [[ -n "${internal_id}" ]] || fail "required Keycloak client is unavailable"
  printf '%s' "${internal_id}"
}

restore_weave_app() {
  [[ "${CLIENT_RESTORE_PENDING}" == "true" ]] || return 0
  request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_INTERNAL_ID}" "${ADMIN_ACCESS_TOKEN}" \
    "$(<"${PRIVATE_STATE_DIR}/weave-app-original.json")" >/dev/null || return 1
  CLIENT_RESTORE_PENDING="false"
}

on_exit_mcp() {
  local status=$?
  restore_weave_app || status=1
  if [[ -n "${PRIVATE_STATE_DIR}" && -d "${PRIVATE_STATE_DIR}" ]]; then
    rm -rf -- "${PRIVATE_STATE_DIR}"
  fi
  exit "${status}"
}

enable_isolated_password_grant() {
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_INTERNAL_ID}" "${ADMIN_ACCESS_TOKEN}" \
    >"${PRIVATE_STATE_DIR}/weave-app-original.json"
  jq -e '.clientId == "weave-app" and .publicClient == true' \
    "${PRIVATE_STATE_DIR}/weave-app-original.json" >/dev/null ||
    fail "weave-app is not the expected isolated public client"
  if [[ "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/weave-app-original.json")" != "true" ]]; then
    jq '.directAccessGrantsEnabled = true' "${PRIVATE_STATE_DIR}/weave-app-original.json" \
      >"${PRIVATE_STATE_DIR}/weave-app-active.json"
    CLIENT_RESTORE_PENDING="true"
    request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_INTERNAL_ID}" "${ADMIN_ACCESS_TOKEN}" \
      "$(<"${PRIVATE_STATE_DIR}/weave-app-active.json")" >/dev/null
  fi
}

token_endpoint() {
  printf '%s/realms/%s/protocol/openid-connect/token' "$(keycloak_admin_url)" "$(encode "${REALM}")"
}

mint_member_token() {
  local response status
  response="${PRIVATE_STATE_DIR}/member-token-response.json"
  status="$(curl --silent --show-error --output "${response}" --write-out '%{http_code}' \
    --connect-timeout 5 --max-time 20 -X POST "$(token_endpoint)" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=weave-app' \
    --data-urlencode "username=${AUTHOR_USERNAME}" \
    --data-urlencode "password=${AUTHOR_PASSWORD}" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'scope=openid profile email weave:mcp')"
  [[ "${status}" == "200" ]] || fail "member MCP token mint returned status ${status}"
  jq -er '.access_token' "${response}"
}

jwt_payload_mcp() {
  python3 -c '
import base64, json, sys
parts = sys.stdin.read().strip().split(".")
if len(parts) != 3:
    raise SystemExit(1)
payload = parts[1] + "=" * (-len(parts[1]) % 4)
json.dump(json.loads(base64.urlsafe_b64decode(payload)), sys.stdout,
          separators=(",", ":"), sort_keys=True)
' <<<"$1"
}

exchange_token() {
  local client_secret="$1" subject_token="$2" output_file="$3"
  curl --silent --show-error --output "${output_file}" --write-out '%{http_code}' \
    --connect-timeout 5 --max-time 20 -X POST "$(token_endpoint)" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
    --data-urlencode 'requested_token_type=urn:ietf:params:oauth:token-type:access_token' \
    --data-urlencode 'client_id=weave-mcp-server' \
    --data-urlencode "client_secret=${client_secret}" \
    --data-urlencode "subject_token=${subject_token}" \
    --data-urlencode 'audience=weave-backend' \
    --data-urlencode 'scope=weave:mcp-backend'
}

public_url_mcp() {
  local port_suffix=""
  if [[ "${TF_VAR_proxy_host_port:-443}" != "443" ]]; then
    port_suffix=":${TF_VAR_proxy_host_port}"
  fi
  printf 'https://%s.%s%s' "${TF_VAR_api_subdomain:-api}" "${TF_VAR_tenant_domain:-weave.test}" "${port_suffix}"
}

curl_public_json() {
  local url="$1" host_port
  host_port="${url#*://}"
  host_port="${host_port%%/*}"
  curl --silent --show-error --fail --connect-timeout 5 --max-time 20 \
    --cacert "${TF_VAR_caddy_tls_ca_file}" \
    --resolve "${host_port}:127.0.0.1" "${url}"
}

wait_for_mcp() {
  local attempt
  for attempt in {1..30}; do
    if curl --silent --fail "http://127.0.0.1:${TF_VAR_mcp_host_port}/actuator/health" |
      jq -e '.status == "UP"' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "MCP server did not become healthy after credential rotation"
}

run_mcp_workload_proof() {
  command -v curl >/dev/null || fail "curl is required"
  command -v docker >/dev/null || fail "docker is required"
  command -v jq >/dev/null || fail "jq is required"
  command -v python3 >/dev/null || fail "python3 is required"

  derive_paths_and_names
  validate_paths
  EVIDENCE_PATH="${EVIDENCE_PATH:-${OUTPUT_ROOT}/${NAMESPACE}/mcp-workload-evidence.json}"
  validate_private_path "${EVIDENCE_PATH}"
  load_runtime_environment
  assert_isolated_runtime
  [[ -f "${IDENTITY_MANIFEST_PATH}" ]] || fail "identity evidence is missing"
  [[ -n "${TF_VAR_weave_mcp_client_secret:-}" ]] || fail "MCP bootstrap credential is missing"
  [[ -f "${TF_VAR_weave_mcp_client_secret_file:-}" && ! -L "${TF_VAR_weave_mcp_client_secret_file}" ]] ||
    fail "MCP runtime credential file is not a regular non-symlink file"

  PRIVATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/weave-isolated-mcp.XXXXXX")"
  chmod 700 "${PRIVATE_STATE_DIR}"
  umask 077
  trap on_exit_mcp EXIT

  ADMIN_ACCESS_TOKEN="$(admin_token)"
  [[ -n "${ADMIN_ACCESS_TOKEN}" ]] || fail "isolated Keycloak admin authentication failed"
  KEYCLOAK_API_BASE="$(api_base)"
  WEAVE_APP_INTERNAL_ID="$(resolve_client_id weave-app)"
  WEAVE_MCP_INTERNAL_ID="$(resolve_client_id weave-mcp-server)"
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_MCP_INTERNAL_ID}" "${ADMIN_ACCESS_TOKEN}" \
    >"${PRIVATE_STATE_DIR}/mcp-client.json"
  jq -e '.clientId == "weave-mcp-server" and .publicClient == false and .serviceAccountsEnabled == true and .attributes["standard.token.exchange.enabled"] == "true"' \
    "${PRIVATE_STATE_DIR}/mcp-client.json" >/dev/null || fail "MCP confidential exchange client is not declaratively active"

  enable_isolated_password_grant
  local member_token member_claims member_subject expected_resource issuer metadata
  member_token="$(mint_member_token)"
  member_claims="$(jwt_payload_mcp "${member_token}")" || fail "member token is not a JWT"
  expected_resource="$(public_url_mcp)/mcp"
  issuer="https://${TF_VAR_auth_subdomain:-auth}.${TF_VAR_tenant_domain:-weave.test}"
  [[ "${TF_VAR_proxy_host_port:-443}" == "443" ]] || issuer+=":${TF_VAR_proxy_host_port}"
  issuer+="/realms/${REALM}"
  jq -e --arg resource "${expected_resource}" --arg issuer "${issuer}" '
    .iss == $issuer and .azp == "weave-app" and
    ((.aud | if type == "array" then . else [.] end) | index($resource) != null) and
    ((.aud | if type == "array" then . else [.] end) | index("weave-mcp-server") != null) and
    ((.scope // "") | split(" ") | index("weave:mcp") != null) and
    (.sub | type == "string" and length > 0)
  ' <<<"${member_claims}" >/dev/null || fail "member token does not satisfy the exact MCP resource contract"
  member_subject="$(jq -r '.sub' <<<"${member_claims}")"

  metadata="$(curl_public_json "$(public_url_mcp)/.well-known/oauth-protected-resource/mcp")"
  jq -e --arg resource "${expected_resource}" --arg issuer "${issuer}" '
    .resource == $resource and .authorization_servers == [$issuer] and
    .bearer_methods_supported == ["header"] and (.scopes_supported | index("weave:mcp") != null)
  ' <<<"${metadata}" >/dev/null || fail "public RFC 9728 metadata does not match the exact MCP resource"

  local before_status before_token before_claims rotated_json rotated_secret old_status after_status after_token after_claims
  before_status="$(exchange_token "${TF_VAR_weave_mcp_client_secret}" "${member_token}" "${PRIVATE_STATE_DIR}/exchange-before.json")"
  [[ "${before_status}" == "200" ]] || fail "pre-rotation standard token exchange returned status ${before_status}"
  before_token="$(jq -er '.access_token' "${PRIVATE_STATE_DIR}/exchange-before.json")"
  before_claims="$(jwt_payload_mcp "${before_token}")" || fail "exchanged backend token is not a JWT"
  jq -e --arg sub "${member_subject}" --arg issuer "${issuer}" '
    .iss == $issuer and .sub == $sub and .azp == "weave-mcp-server" and
    ((.aud | if type == "array" then . else [.] end) | index("weave-backend") != null) and
    ((.scope // "") | split(" ") | index("weave:mcp-backend") != null) and
    ((.scope // "") | split(" ") | index("weave:mcp") == null) and
    ((.scope // "") | split(" ") | index("weave:workspace") == null)
  ' <<<"${before_claims}" >/dev/null || fail "exchanged token did not preserve human identity and reduce audience/scope"

  rotated_json="$(request POST "${KEYCLOAK_API_BASE}/clients/${WEAVE_MCP_INTERNAL_ID}/client-secret" "${ADMIN_ACCESS_TOKEN}")"
  rotated_secret="$(jq -er '.value' <<<"${rotated_json}")"
  [[ "${rotated_secret}" != "${TF_VAR_weave_mcp_client_secret}" ]] || fail "Keycloak did not rotate the MCP credential"
  old_status="$(exchange_token "${TF_VAR_weave_mcp_client_secret}" "${member_token}" "${PRIVATE_STATE_DIR}/exchange-old.json")"
  [[ "${old_status}" == "401" ]] || fail "retired MCP credential returned status ${old_status}, expected 401"

  printf '%s' "${rotated_secret}" >"${TF_VAR_weave_mcp_client_secret_file}.new"
  chmod 600 "${TF_VAR_weave_mcp_client_secret_file}.new"
  mv -f "${TF_VAR_weave_mcp_client_secret_file}.new" "${TF_VAR_weave_mcp_client_secret_file}"
  docker restart "$(weave_container_name mcp-server)" >/dev/null
  wait_for_mcp
  after_status="$(exchange_token "${rotated_secret}" "${member_token}" "${PRIVATE_STATE_DIR}/exchange-after.json")"
  [[ "${after_status}" == "200" ]] || fail "post-rotation standard token exchange returned status ${after_status}"
  after_token="$(jq -er '.access_token' "${PRIVATE_STATE_DIR}/exchange-after.json")"
  after_claims="$(jwt_payload_mcp "${after_token}")" || fail "post-rotation token is not a JWT"
  jq -e --arg sub "${member_subject}" '.sub == $sub and .azp == "weave-mcp-server"' \
    <<<"${after_claims}" >/dev/null || fail "post-rotation token lost the human/workload binding"

  local runtime_inspect completed_at
  runtime_inspect="$(docker inspect "$(weave_container_name mcp-server)")"
  jq -e '.[0].Config.Env | all(startswith("WEAVE_MCP_CLIENT_SECRET=") | not)' \
    <<<"${runtime_inspect}" >/dev/null || fail "MCP credential leaked into container environment"
  jq -e 'any(.[0].Mounts[]; .Destination == "/run/secrets/weave-mcp-client-secret" and .RW == false)' \
    <<<"${runtime_inspect}" >/dev/null || fail "MCP credential is not mounted read-only"

  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname -- "${EVIDENCE_PATH}")"
  jq -n \
    --arg completedAtUtc "${completed_at}" \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --arg memberSubjectSha256 "$(sha256 "${member_subject}")" \
    --arg resource "${expected_resource}" \
    --arg issuer "${issuer}" \
    --argjson oldCredentialStatus "${old_status}" \
    '{schemaVersion:"weave.isolated-e2e-mcp-workload.v1",completedAtUtc:$completedAtUtc,
      namespaceSha256:$namespaceSha256,memberSubjectSha256:$memberSubjectSha256,
      protectedResource:{resource:$resource,authorizationServer:$issuer,rfc9728Published:true},
      workload:{clientId:"weave-mcp-server",serviceAccount:true,standardTokenExchangeV2:true,
        clientSecretEnvironmentValuePresent:false,credentialFileReadOnly:true},
      exchange:{humanIssuerAndSubjectPreserved:true,workloadAzpVerified:true,
        backendAudienceVerified:true,scopeReduced:true,refreshTokenRequested:false},
      rotation:{oldCredentialStatus:$oldCredentialStatus,newCredentialExchangeStatus:200,oldCredentialRevoked:true},
      rawTokenIncluded:false,rawSecretIncluded:false,rawProviderPayloadIncluded:false,
      persistentHumanChanged:false,supportSafe:true}' >"${EVIDENCE_PATH}"
  chmod 600 "${EVIDENCE_PATH}"
  printf 'MCP_WORKLOAD_IDENTITY_RESULT status=passed oldCredentialStatus=%s newCredentialStatus=200 exactResource=true supportSafe=true\n' "${old_status}"
}

main_mcp_workload() {
  parse_mcp_args "$@"
  run_mcp_workload_proof
}

main_mcp_workload "$@"
