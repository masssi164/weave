#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC1091,SC2034

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
# Reuse the identity lifecycle's namespace derivation, marker checks, Keycloak
# Admin API helpers, and isolated backend runtime verification. Its main is
# deliberately source-safe.
# shellcheck source=infra/weave-workspace/isolated-e2e-identities.sh
source "${SCRIPT_DIR}/isolated-e2e-identities.sh"

CANDIDATE_COMMIT="${WEAVE_CANDIDATE_COMMIT:-}"
OUTPUT_PATH=""
BACKEND_ORIGIN="${WEAVE_E2E_BACKEND_ORIGIN:-}"
BACKEND_CONTAINER=""
SYNAPSE_CONTAINER=""

PRIVATE_STATE_DIR=""
FINAL_TEMP_OUTPUT=""
PROOF_TOKEN_PATH=""
PROOF_AUTH_HEADER_FILE=""
ADMIN_ACCESS_TOKEN=""
KEYCLOAK_API_BASE=""
WEAVE_APP_CLIENT_ID=""
CLIENT_RESTORE_PENDING="false"
BACKEND_RESTORE_REQUIRED="false"
SYNAPSE_RESTORE_REQUIRED="false"

readonly SYNAPSE_LISTENER_READY_TIMEOUT_SECONDS=60
readonly PROVIDER_OPERATION_RECOVERY_TIMEOUT_SECONDS=90

fail() {
  printf 'MATRIX_SYNAPSE_PROVIDER_PROOF_ERROR code=%s\n' "$*" >&2
  exit 1
}

usage_provider_proof() {
  cat <<'EOF'
Usage: isolated-e2e-chat-provider-proof.sh --run-id ID --candidate-commit SHA --output PATH [options]

Prove two real, isolated Matrix/Synapse collaboration passes through the
northbound Weave Matrix facade. Persistent dogfood is always rejected.

Options:
  --run-id ID                Stable ID used by identity prepare/provision.
  --candidate-commit SHA     Exact 40-character candidate commit.
  --output-root PATH         Private disposable run root.
  --credentials-env PATH     Prepared private identity credential env.
  --startup-env PATH         Prepared isolated stack/OpenTofu env.
  --identity-manifest PATH   Provisioned support-safe identity evidence.
  --stack-bootstrap-env PATH Private bootstrap env written by install.sh.
  --output PATH              Support-safe provider proof JSON.

Environment:
  WEAVE_E2E_STACK_SCOPE=isolated  Required; persistent dogfood is rejected.
  WEAVE_E2E_BACKEND_ORIGIN        Optional loopback backend origin.

The independently random proof credential is mounted only into the isolated
backend and read from its runner-private host file by this proof caller. It is
never reused as an Application Service credential. Provider references,
canonical identifiers, opaque encrypted envelopes, access tokens, credentials,
and raw responses remain private and are removed on every exit path.
EOF
}

parse_provider_proof_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --run-id) RUN_ID="${2:-}"; shift 2 ;;
      --candidate-commit) CANDIDATE_COMMIT="${2:-}"; shift 2 ;;
      --output-root) OUTPUT_ROOT="${2:-}"; shift 2 ;;
      --credentials-env) CREDENTIAL_ENV_PATH="${2:-}"; shift 2 ;;
      --startup-env) STARTUP_ENV_PATH="${2:-}"; shift 2 ;;
      --identity-manifest) IDENTITY_MANIFEST_PATH="${2:-}"; shift 2 ;;
      --stack-bootstrap-env) STACK_BOOTSTRAP_ENV="${2:-}"; shift 2 ;;
      --output) OUTPUT_PATH="${2:-}"; shift 2 ;;
      -h|--help) usage_provider_proof; exit 0 ;;
      *) fail "unknown-argument" ;;
    esac
  done
}

require_command() {
  command -v "$1" >/dev/null || fail "missing-command-$1"
}

assert_provider_identity_manifest() {
  [[ -f "${IDENTITY_MANIFEST_PATH}" ]] || fail "identity-manifest-missing"
  jq -e --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" '
    .schemaVersion == "weave.isolated-e2e-identities.v1" and
    .namespaceSha256 == $namespaceSha256 and
    .contextAuthorization.status == "active_runtime_verified" and
    .providerBindings.keycloak == "provisioned" and
    .persistentHumanIdentityChanged == false and
    .credentialsIncluded == false and
    .rawProviderPayloadIncluded == false and
    .supportSafe == true and
    ([.actors[].role] | sort) == ["author", "collaborator", "outsider"] and
    all(.actors[]; (.subjectSha256 | type == "string") and (.subjectSha256 | test("^[0-9a-f]{64}$")))
  ' "${IDENTITY_MANIFEST_PATH}" >/dev/null 2>&1 || fail "identity-manifest-not-provisioned"
}

verify_chat_runtime() {
  local runtime_env backend_mounts synapse_mounts
  runtime_env="$(docker inspect --format '{{json .Config.Env}}' "${BACKEND_CONTAINER}")" ||
    fail "backend-runtime-unavailable"
  jq -e \
    --arg runId "${RUN_ID}" \
    --arg matrixInternalBaseUrl "http://${SYNAPSE_CONTAINER}:8008" '
    index("WEAVE_CHAT_PROVIDER=matrix-synapse") != null and
    index("WEAVE_CHAT_STORAGE_MODE=jdbc") != null and
    index("WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL=" + $matrixInternalBaseUrl) != null and
    index("WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE=/run/weave-chat-appservice/as-token") != null and
    index("WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE=/run/weave-chat-appservice/hs-token") != null and
    index("WEAVE_E2E_STACK_SCOPE=isolated") != null and
    index("WEAVE_CHAT_E2E_PROOF_ENABLED=true") != null and
    index("WEAVE_CHAT_E2E_PROOF_TOKEN_FILE=/run/weave-chat-e2e-proof/token") != null and
    index("WEAVE_CHAT_E2E_PROOF_RUN_ID=" + $runId) != null
  ' <<<"${runtime_env}" >/dev/null || fail "chat-runtime-contract-mismatch"
  backend_mounts="$(docker inspect --format '{{json .Mounts}}' "${BACKEND_CONTAINER}")" ||
    fail "backend-proof-mount-unavailable"
  jq -e '
    ([.[] | select(
      .Type == "bind" and
      .Destination == "/run/weave-chat-e2e-proof/token" and
      .RW == false
    )] | length) == 1
  ' <<<"${backend_mounts}" >/dev/null || fail "backend-proof-mount-invalid"
  synapse_mounts="$(docker inspect --format '{{json .Mounts}}' "${SYNAPSE_CONTAINER}")" ||
    fail "synapse-mounts-unavailable"
  jq -e '
    all(.[]; .Destination != "/run/weave-chat-e2e-proof/token")
  ' <<<"${synapse_mounts}" >/dev/null || fail "proof-credential-mounted-into-provider"
}

proof_file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

prepare_private_proof_authentication() {
  local proof_token
  [[ "${TF_VAR_chat_e2e_proof_enabled:-false}" == "true" ]] ||
    fail "proof-boundary-not-enabled"
  [[ "${TF_VAR_chat_e2e_proof_run_id:-}" == "${RUN_ID}" ]] ||
    fail "proof-run-binding-mismatch"
  PROOF_TOKEN_PATH="${TF_VAR_chat_e2e_proof_token_host_path:-}"
  [[ -n "${PROOF_TOKEN_PATH}" ]] || fail "proof-credential-path-missing"
  [[ -f "${PROOF_TOKEN_PATH}" && ! -L "${PROOF_TOKEN_PATH}" ]] ||
    fail "proof-credential-file-invalid"
  [[ "$(proof_file_mode "${PROOF_TOKEN_PATH}")" == "600" ]] ||
    fail "proof-credential-mode-invalid"
  proof_token="$(<"${PROOF_TOKEN_PATH}")"
  [[ "${proof_token}" =~ ^[0-9a-f]{96}$ ]] || fail "proof-credential-material-invalid"
  [[ "${proof_token}" != "${TF_VAR_matrix_chat_appservice_as_token:-}" &&
     "${proof_token}" != "${TF_VAR_matrix_chat_appservice_hs_token:-}" ]] ||
    fail "proof-credential-reuses-appservice-authority"
  PROOF_AUTH_HEADER_FILE="${PRIVATE_STATE_DIR}/proof-authorization.header"
  printf 'Authorization: Bearer %s\n' "${proof_token}" >"${PROOF_AUTH_HEADER_FILE}"
  chmod 600 "${PROOF_AUTH_HEADER_FILE}"
  unset proof_token
}

resolve_weave_app_client() {
  local clients client_id
  clients="$(request GET "${KEYCLOAK_API_BASE}/clients?clientId=weave-app" "${ADMIN_ACCESS_TOKEN}")" ||
    return 1
  client_id="$(find_exact_id "${clients}" clientId weave-app)"
  [[ -n "${client_id}" ]] || return 1
  printf '%s' "${client_id}"
}

enable_direct_grants_for_sessions() {
  WEAVE_APP_CLIENT_ID="$(resolve_weave_app_client)" || fail "weave-app-client-unavailable"
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
    >"${PRIVATE_STATE_DIR}/weave-app-original.json" || fail "weave-app-client-read-failed"
  jq -e '
    .clientId == "weave-app" and
    .publicClient == true and
    (.directAccessGrantsEnabled | type == "boolean")
  ' "${PRIVATE_STATE_DIR}/weave-app-original.json" >/dev/null || fail "weave-app-client-not-safe"
  if [[ "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/weave-app-original.json")" != "true" ]]; then
    jq '.directAccessGrantsEnabled = true' "${PRIVATE_STATE_DIR}/weave-app-original.json" \
      >"${PRIVATE_STATE_DIR}/weave-app-session.json"
    CLIENT_RESTORE_PENDING="true"
    request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
      "$(<"${PRIVATE_STATE_DIR}/weave-app-session.json")" >/dev/null ||
      fail "weave-app-session-enable-failed"
    request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
      >"${PRIVATE_STATE_DIR}/weave-app-session-active.json" || fail "weave-app-session-verify-failed"
    jq -e '.directAccessGrantsEnabled == true' \
      "${PRIVATE_STATE_DIR}/weave-app-session-active.json" >/dev/null ||
      fail "weave-app-session-not-active"
  fi
}

restore_direct_grants() {
  [[ "${CLIENT_RESTORE_PENDING}" == "true" ]] || return 0
  local token="${ADMIN_ACCESS_TOKEN}"
  if ! request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${token}" \
    "$(<"${PRIVATE_STATE_DIR}/weave-app-original.json")" >/dev/null 2>&1; then
    token="$(admin_token 2>/dev/null)" || return 1
    [[ -n "${token}" ]] || return 1
    request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${token}" \
      "$(<"${PRIVATE_STATE_DIR}/weave-app-original.json")" >/dev/null 2>&1 || return 1
  fi
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${token}" \
    >"${PRIVATE_STATE_DIR}/weave-app-restored.json" 2>/dev/null || return 1
  [[ "$(jq -c '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/weave-app-restored.json")" == \
    "$(jq -c '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/weave-app-original.json")" ]] || return 1
  CLIENT_RESTORE_PENDING="false"
}

container_running() {
  [[ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

wait_container_running() {
  local container="$1" deadline=$(( $(date +%s) + 30 ))
  until container_running "${container}"; do
    (( $(date +%s) < deadline )) || return 1
    sleep 1
  done
}

wait_synapse_listener_ready() {
  local deadline status
  deadline=$(( $(date +%s) + SYNAPSE_LISTENER_READY_TIMEOUT_SECONDS ))
  while :; do
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --connect-timeout 2 --max-time 5 \
      "http://127.0.0.1:${TF_VAR_synapse_host_port:-48008}/health" \
      2>/dev/null || true)"
    [[ "${status}" == "200" ]] && return 0
    (( $(date +%s) < deadline )) || return 1
    sleep 2
  done
}

restore_runtime_containers() {
  local failed=0
  if [[ "${SYNAPSE_RESTORE_REQUIRED}" == "true" ]]; then
    docker start "${SYNAPSE_CONTAINER}" >/dev/null 2>&1 || failed=1
    wait_container_running "${SYNAPSE_CONTAINER}" || failed=1
    [[ "${failed}" -ne 0 ]] || wait_synapse_listener_ready || failed=1
    [[ "${failed}" -ne 0 ]] || SYNAPSE_RESTORE_REQUIRED="false"
  fi
  if [[ "${BACKEND_RESTORE_REQUIRED}" == "true" ]]; then
    docker start "${BACKEND_CONTAINER}" >/dev/null 2>&1 || failed=1
    wait_container_running "${BACKEND_CONTAINER}" || failed=1
    [[ "${failed}" -ne 0 ]] || BACKEND_RESTORE_REQUIRED="false"
  fi
  return "${failed}"
}

on_provider_proof_exit() {
  local status=$?
  trap - EXIT INT TERM
  if ! restore_runtime_containers; then
    printf 'MATRIX_SYNAPSE_PROVIDER_PROOF_ERROR code=container-restoration-failed\n' >&2
    status=1
  fi
  if ! restore_direct_grants; then
    printf 'MATRIX_SYNAPSE_PROVIDER_PROOF_ERROR code=keycloak-client-restoration-failed\n' >&2
    status=1
  fi
  [[ -z "${FINAL_TEMP_OUTPUT}" ]] || rm -f -- "${FINAL_TEMP_OUTPUT}"
  [[ -z "${PRIVATE_STATE_DIR}" ]] || rm -rf -- "${PRIVATE_STATE_DIR}"
  exit "${status}"
}

write_jwt_facts() {
  local token="$1" expected_username="$2" output="$3"
  python3 - "${expected_username}" 3<<<"${token}" >"${output}" <<'PY'
import base64
import json
import sys
import time

expected = sys.argv[1]
token = open(3, encoding="utf-8").read().strip()
parts = token.split(".")
if len(parts) != 3:
    raise SystemExit(1)
payload = parts[1] + "=" * (-len(parts[1]) % 4)
value = json.loads(base64.urlsafe_b64decode(payload.encode("ascii")))
audience = value.get("aud", [])
if isinstance(audience, str):
    audience = [audience]
scope = value.get("scope", "").split()
required = ("sub", "iss", "preferred_username", "weave_tenant_id", "exp")
if any(not value.get(field) for field in required):
    raise SystemExit(1)
if value["preferred_username"] != expected or "weave-app" not in audience:
    raise SystemExit(1)
if "weave:workspace" not in scope or int(value["exp"]) <= int(time.time()) + 120:
    raise SystemExit(1)
json.dump(
    {
        "subject": value["sub"],
        "issuer": value["iss"],
        "tenant": value["weave_tenant_id"],
        "preferredUsername": value["preferred_username"],
        "expiresAtEpoch": int(value["exp"]),
    },
    sys.stdout,
    separators=(",", ":"),
    sort_keys=True,
)
PY
}

mint_user_session() {
  local pass_index="$1" role="$2" username="$3" password="$4"
  local pass_dir="${PRIVATE_STATE_DIR}/pass-${pass_index}"
  local response="${pass_dir}/${role}-token-response.json"
  local config="${pass_dir}/${role}-token-request.conf"
  local token status device
  device="WEAVEE2E$(sha256 "${NAMESPACE}:${pass_index}:${role}" | cut -c1-24 | tr '[:lower:]' '[:upper:]')"
  {
    printf 'url = "%s/realms/%s/protocol/openid-connect/token"\n' "$(keycloak_admin_url)" "$(encode "${REALM}")"
    printf 'request = "POST"\n'
    printf 'header = "Content-Type: application/x-www-form-urlencoded"\n'
    printf 'data-urlencode = "client_id=weave-app"\n'
    printf 'data-urlencode = "username=%s"\n' "${username}"
    printf 'data-urlencode = "password=%s"\n' "${password}"
    printf 'data-urlencode = "grant_type=password"\n'
    printf 'data-urlencode = "scope=openid profile email weave:workspace"\n'
  } >"${config}"
  if ! status="$(curl --silent --output "${response}" --write-out '%{http_code}' \
    --connect-timeout 5 --max-time 15 --config "${config}")"; then
    rm -f -- "${config}" "${response}"
    fail "keycloak-session-transport-failed"
  fi
  rm -f -- "${config}"
  [[ "${status}" == "200" ]] || {
    rm -f -- "${response}"
    fail "keycloak-session-status-${status}"
  }
  token="$(jq -r '.access_token // empty' "${response}")"
  rm -f -- "${response}"
  [[ -n "${token}" ]] || fail "keycloak-session-token-missing"
  write_jwt_facts "${token}" "${username}" "${pass_dir}/${role}-facts.json" ||
    fail "keycloak-session-claims-invalid"
  {
    printf 'Authorization: Bearer %s\n' "${token}"
    printf 'X-Weave-Matrix-Device-Id: %s\n' "${device}"
    printf 'Accept: application/json\n'
  } >"${pass_dir}/${role}-headers"
  unset token
}

validate_session_identity_binding() {
  local pass_index="$1" role="$2" expected_username="$3"
  local facts="${PRIVATE_STATE_DIR}/pass-${pass_index}/${role}-facts.json"
  local subject_hash
  subject_hash="$(sha256 "$(jq -r '.subject' "${facts}")")"
  jq -e \
    --arg role "${role}" \
    --arg subjectHash "${subject_hash}" \
    'any(.actors[]; .role == $role and .subjectSha256 == $subjectHash)' \
    "${IDENTITY_MANIFEST_PATH}" >/dev/null || fail "session-subject-binding-${role}"
  # Populated by the sourced, validated startup environment.
  # shellcheck disable=SC2154
  jq -e \
    --arg username "${expected_username}" \
    --arg tenant "${TF_VAR_context_authorization_default_tenant_id}" '
      .preferredUsername == $username and .tenant == $tenant and
      (.issuer | type == "string") and (.issuer | length > 0) and
      (.subject | type == "string") and (.subject | length > 0)
    ' "${facts}" >/dev/null || fail "session-runtime-binding-${role}"
}

northbound_request() {
  local method="$1" path="$2" headers="$3" body="$4" output="$5"
  local status
  local -a args=(
    --silent --output "${output}" --write-out '%{http_code}'
    --connect-timeout 5 --max-time 20 --request "${method}"
  )
  [[ "${headers}" == "-" ]] || args+=(--header "@${headers}")
  if [[ "${body}" != "-" ]]; then
    args+=(--header 'Content-Type: application/json' --data-binary "@${body}")
  fi
  if ! status="$(curl "${args[@]}" "${BACKEND_ORIGIN}${path}")"; then
    : >"${output}"
    printf '000'
    return 0
  fi
  printf '%s' "${status}"
}

expect_northbound_status() {
  local expected="$1" method="$2" path="$3" headers="$4" body="$5" output="$6"
  local status
  status="$(northbound_request "${method}" "${path}" "${headers}" "${body}" "${output}")"
  [[ "${status}" == "${expected}" ]] || fail "northbound-${method,,}-status-${status}"
}

uri_encode() {
  jq -nr --arg value "$1" '$value | @uri'
}

pass_dir() {
  printf '%s/pass-%s' "${PRIVATE_STATE_DIR}" "$1"
}

register_session() {
  local pass_index="$1" role="$2"
  local directory response status
  directory="$(pass_dir "${pass_index}")"
  response="${directory}/${role}-whoami-response.json"
  status="$(northbound_request GET '/_matrix/client/v3/account/whoami' \
    "${directory}/${role}-headers" - "${response}")"
  [[ "${status}" == "200" ]] || fail "whoami-${role}-status-${status}"
  jq -er '.user_id | select(type == "string" and length > 3)' "${response}" \
    >"${directory}/${role}-matrix-user" || fail "whoami-${role}-projection-invalid"
  rm -f -- "${response}"
}

register_pass_sessions() {
  local pass_index="$1" role
  for role in author collaborator outsider; do
    register_session "${pass_index}" "${role}"
  done
}

create_encrypted_body() {
  local pass_index="$1" event_index="$2"
  local directory opaque sender_key session_id device_id
  directory="$(pass_dir "${pass_index}")"
  opaque="$(openssl rand -hex 96)"
  sender_key="$(openssl rand -hex 32)"
  session_id="$(openssl rand -hex 24)"
  device_id="WEAVEE2E$(openssl rand -hex 8 | tr '[:lower:]' '[:upper:]')"
  jq -n \
    --arg algorithm 'm.megolm.v1.aes-sha2' \
    --arg opaque "${opaque}" \
    --arg senderKey "${sender_key}" \
    --arg sessionId "${session_id}" \
    --arg deviceId "${device_id}" \
    '{algorithm:$algorithm,ciphertext:$opaque,sender_key:$senderKey,session_id:$sessionId,device_id:$deviceId}' \
    >"${directory}/event-${event_index}-body.json"
  sha256 "${opaque}" >"${directory}/event-${event_index}-correlation.sha256"
  unset opaque sender_key session_id device_id
}

create_encrypted_room() {
  local pass_index="$1" directory collaborator_user response status room_id conversation_id
  directory="$(pass_dir "${pass_index}")"
  collaborator_user="$(<"${directory}/collaborator-matrix-user")"
  jq -n \
    --arg name "isolated-${NAMESPACE}-matrix-pass-${pass_index}" \
    --arg collaborator "${collaborator_user}" '
      {
        name:$name,
        is_direct:false,
        invite:[$collaborator],
        initial_state:[{
          type:"m.room.encryption",
          state_key:"",
          content:{algorithm:"m.megolm.v1.aes-sha2"}
        }]
      }
    ' >"${directory}/create-room-request.json"
  response="${directory}/create-room-response.json"
  status="$(northbound_request POST '/_matrix/client/v3/createRoom' \
    "${directory}/author-headers" "${directory}/create-room-request.json" "${response}")"
  [[ "${status}" == "200" ]] || fail "create-room-status-${status}"
  room_id="$(jq -r '.room_id // empty' "${response}")"
  rm -f -- "${response}" "${directory}/create-room-request.json"
  [[ "${room_id}" == '!'*:* ]] || fail "create-room-projection-invalid"
  conversation_id="${room_id#!}"
  conversation_id="${conversation_id%:*}"
  [[ "${conversation_id}" == room-* ]] || fail "create-room-canonical-projection-invalid"
  printf '%s' "${room_id}" >"${directory}/room-id"
  printf '%s' "${conversation_id}" >"${directory}/conversation-id"
  unset room_id conversation_id collaborator_user
}

join_collaborator() {
  local pass_index="$1" directory room_id encoded response status
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  encoded="$(uri_encode "${room_id}")"
  response="${directory}/join-response.json"
  status="$(northbound_request POST "/_matrix/client/v3/join/${encoded}" \
    "${directory}/collaborator-headers" - "${response}")"
  rm -f -- "${response}"
  [[ "${status}" == "200" ]] || fail "collaborator-join-status-${status}"
}

send_encrypted_event() {
  local pass_index="$1" event_index="$2" role="$3" transaction_id="$4" output_id="$5"
  local directory room_id encoded_room encoded_tx response status event_id
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  encoded_room="$(uri_encode "${room_id}")"
  encoded_tx="$(uri_encode "${transaction_id}")"
  response="${directory}/send-${event_index}-${role}-response.json"
  status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/send/m.room.encrypted/${encoded_tx}" \
    "${directory}/${role}-headers" "${directory}/event-${event_index}-body.json" "${response}")"
  [[ "${status}" == "200" ]] || {
    rm -f -- "${response}"
    fail "send-${event_index}-status-${status}"
  }
  event_id="$(jq -r '.event_id // empty' "${response}")"
  rm -f -- "${response}"
  [[ "${event_id}" == '$'*:* ]] || fail "send-${event_index}-projection-invalid"
  printf '%s' "${event_id}" >"${output_id}"
  unset room_id event_id
}

room_messages() {
  local pass_index="$1" role="$2" output="$3"
  local directory room_id encoded
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  encoded="$(uri_encode "${room_id}")"
  northbound_request GET \
    "/_matrix/client/v3/rooms/${encoded}/messages?dir=b&limit=100" \
    "${directory}/${role}-headers" - "${output}"
}

verify_event_observed_once() {
  local pass_index="$1" observer="$2" event_index="$3"
  local directory response status event_id opaque
  directory="$(pass_dir "${pass_index}")"
  response="${directory}/${observer}-messages-${event_index}.json"
  status="$(room_messages "${pass_index}" "${observer}" "${response}")"
  [[ "${status}" == "200" ]] || fail "messages-${observer}-status-${status}"
  event_id="$(<"${directory}/event-${event_index}-id")"
  opaque="$(jq -r '.ciphertext' "${directory}/event-${event_index}-body.json")"
  jq -e --arg expectedEvent "${event_id}" --arg expectedOpaque "${opaque}" '
    ([.chunk[] | select(
      .event_id == $expectedEvent and
      .type == "m.room.encrypted" and
      .content.algorithm == "m.megolm.v1.aes-sha2" and
      .content.ciphertext == $expectedOpaque and
      (.content | has("body") | not)
    )] | length) == 1 and
    ([.chunk[] | select(.type == "m.room.message")] | length) == 0
  ' "${response}" >/dev/null || fail "encrypted-event-readback-${event_index}-invalid"
  rm -f -- "${response}"
  unset event_id opaque
}

verify_complete_room_readback() {
  local pass_index="$1" role="$2" directory response status event_index event_id opaque
  directory="$(pass_dir "${pass_index}")"
  response="${directory}/${role}-complete-readback.json"
  status="$(room_messages "${pass_index}" "${role}" "${response}")"
  [[ "${status}" == "200" ]] || fail "complete-readback-${role}-status-${status}"
  jq -e '
    ([.chunk[] | select(.type == "m.room.encrypted")] | length) == 3 and
    ([.chunk[] | select(.type == "m.room.message")] | length) == 0
  ' "${response}" >/dev/null || fail "complete-readback-${role}-count-invalid"
  for event_index in 1 2 3; do
    event_id="$(<"${directory}/event-${event_index}-id")"
    opaque="$(jq -r '.ciphertext' "${directory}/event-${event_index}-body.json")"
    jq -e --arg expectedEvent "${event_id}" --arg expectedOpaque "${opaque}" '
      ([.chunk[] | select(
        .event_id == $expectedEvent and .type == "m.room.encrypted" and
        .content.ciphertext == $expectedOpaque
      )] | length) == 1
    ' "${response}" >/dev/null || fail "complete-readback-event-${event_index}-invalid"
  done
  rm -f -- "${response}"
  unset event_id opaque
}

support_safe_provider_error() {
  local response="$1"
  jq -e '
    (.errcode == "M_UNAVAILABLE" or .errcode == "M_LIMIT_EXCEEDED") and
    (.error | type == "string") and (.error | length > 0)
  ' "${response}" >/dev/null 2>&1 || return 1
  # The literal Matrix event prefix is intentionally part of the scanner.
  # shellcheck disable=SC2016
  ! grep -Eiq 'authorization|bearer[[:space:]]|https?://|_weave_|room-|\$event|ciphertext|sender_key|session_id|device_id' \
    "${response}"
}

assert_outsider_denied() {
  local pass_index="$1" directory response read_status write_status room_id encoded_room encoded_tx
  directory="$(pass_dir "${pass_index}")"
  response="${directory}/outsider-read-response.json"
  read_status="$(room_messages "${pass_index}" outsider "${response}")"
  [[ "${read_status}" == "403" ]] || fail "outsider-read-status-${read_status}"
  rm -f -- "${response}"
  room_id="$(<"${directory}/room-id")"
  encoded_room="$(uri_encode "${room_id}")"
  encoded_tx="$(uri_encode "outsider-denied-${pass_index}-$(sha256 "${NAMESPACE}" | cut -c1-16)")"
  response="${directory}/outsider-write-response.json"
  write_status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/send/m.room.encrypted/${encoded_tx}" \
    "${directory}/outsider-headers" "${directory}/event-1-body.json" "${response}")"
  [[ "${write_status}" == "403" ]] || fail "outsider-write-status-${write_status}"
  rm -f -- "${response}"
}

build_evidence_request() {
  local pass_index="$1" directory tenant conversation author_issuer collaborator_issuer outsider_issuer
  local author_subject collaborator_subject outsider_subject correlation1 correlation2 correlation3
  directory="$(pass_dir "${pass_index}")"
  tenant="$(jq -r '.tenant' "${directory}/author-facts.json")"
  conversation="$(<"${directory}/conversation-id")"
  author_issuer="$(jq -r '.issuer' "${directory}/author-facts.json")"
  collaborator_issuer="$(jq -r '.issuer' "${directory}/collaborator-facts.json")"
  outsider_issuer="$(jq -r '.issuer' "${directory}/outsider-facts.json")"
  author_subject="$(jq -r '.subject' "${directory}/author-facts.json")"
  collaborator_subject="$(jq -r '.subject' "${directory}/collaborator-facts.json")"
  outsider_subject="$(jq -r '.subject' "${directory}/outsider-facts.json")"
  correlation1="$(<"${directory}/event-1-correlation.sha256")"
  correlation2="$(<"${directory}/event-2-correlation.sha256")"
  correlation3="$(<"${directory}/event-3-correlation.sha256")"
  jq -n \
    --arg runId "${RUN_ID}" \
    --arg tenant "${tenant}" \
    --arg conversation "${conversation}" \
    --arg authorIssuer "${author_issuer}" \
    --arg authorRef "user:${author_subject}" \
    --arg collaboratorIssuer "${collaborator_issuer}" \
    --arg collaboratorRef "user:${collaborator_subject}" \
    --arg outsiderIssuer "${outsider_issuer}" \
    --arg outsiderRef "user:${outsider_subject}" \
    --arg correlation1 "${correlation1}" \
    --arg correlation2 "${correlation2}" \
    --arg correlation3 "${correlation3}" '
      {
        runId:$runId,
        tenantId:$tenant,
        conversationId:$conversation,
        author:{identityIssuer:$authorIssuer,actorRef:$authorRef},
        collaborator:{identityIssuer:$collaboratorIssuer,actorRef:$collaboratorRef},
        outsider:{identityIssuer:$outsiderIssuer,actorRef:$outsiderRef},
        eventCorrelationSha256:[$correlation1,$correlation2,$correlation3]
      }
    ' >"${directory}/private-evidence-request.json"
  unset tenant conversation author_issuer collaborator_issuer outsider_issuer
  unset author_subject collaborator_subject outsider_subject
  unset correlation1 correlation2 correlation3
}

capture_private_evidence() {
  local pass_index="$1" output="$2" directory status
  directory="$(pass_dir "${pass_index}")"
  status="$(curl --silent --connect-timeout 3 --max-time 20 \
    --output "${output}" --write-out '%{http_code}' \
    --request POST \
    --header @"${PROOF_AUTH_HEADER_FILE}" \
    --header 'Content-Type: application/json' \
    --data-binary @"${directory}/private-evidence-request.json" \
    "${BACKEND_ORIGIN}/api/internal/e2e/chat/provider-proof" 2>/dev/null || true)"
  if [[ "${status}" != "200" ]]; then
    : >"${output}"
    fail "private-provider-evidence-unavailable"
  fi
  jq -e '
    .supportSafe == true and
    (.correlationHash | test("^[0-9a-f]{64}$")) and
    (.runIdHash | test("^[0-9a-f]{64}$"))
  ' \
    "${output}" >/dev/null 2>&1 || fail "private-provider-evidence-invalid"
}

assert_provider_evidence_counts() {
  local evidence="$1" expected_events="$2" expected_failed="$3"
  jq -e \
    --argjson expectedEvents "${expected_events}" \
    --argjson expectedFailed "${expected_failed}" '
      .contractVersion == "chat-provider-proof-v1" and
      .adapterConfigured == true and
      .canonicalStorage == "durable-relational-flyway" and
      .providerCapabilityState == "available" and
      .providerCapabilityAvailable == true and
      (.providerCapabilityCode | type == "string") and
      (.providerObservationAgeSeconds | type == "number") and
      .providerObservationAgeSeconds >= 0 and .providerObservationAgeSeconds <= 120 and
      .providerConsecutiveFailures == 0 and
      (.providerBackoffUntil == null or (.providerBackoffUntil | type == "string")) and
      .supportSafe == true and
      .providerMembershipExact == true and
      .providerEncryptionStateVerified == true and
      .providerEventMappingExact == true and
      .providerCiphertextCorrelationExact == true and
      .outsiderAbsent == true and .outsiderReadDenied == true and
      .canonicalConversationCount == 1 and .canonicalJoinedMemberCount == 2 and
      .canonicalCommittedEventCount == $expectedEvents and
      .canonicalEncryptedEventCount == $expectedEvents and
      .canonicalPlaintextEventCount == 0 and
      .providerEncryptedEventCount == $expectedEvents and
      .providerPlaintextEventCount == 0 and
      .pendingOperationCount == 0 and .failedOperationCount == $expectedFailed and
      .quarantineCount == 0 and .degradedOperationCount == 0 and
      ([.identities[] | select(
        (.role == "author" or .role == "collaborator") and
        .providerMapped == true and .canonicalJoined == true and
        .providerJoined == true and .providerReadDenied == false
      )] | length) == 2 and
      ([.identities[] | select(
        .role == "outsider" and .providerMapped == false and
        .canonicalJoined == false and .providerJoined == false and .providerReadDenied == true
      )] | length) == 1
    ' "${evidence}" >/dev/null || fail "provider-evidence-counts-invalid"
}

stop_synapse_for_outage() {
  SYNAPSE_RESTORE_REQUIRED="true"
  docker stop --time 10 "${SYNAPSE_CONTAINER}" >/dev/null || fail "synapse-stop-failed"
  ! container_running "${SYNAPSE_CONTAINER}" || fail "synapse-outage-not-active"
}

start_synapse_after_outage() {
  docker start "${SYNAPSE_CONTAINER}" >/dev/null || fail "synapse-start-failed"
  wait_container_running "${SYNAPSE_CONTAINER}" || fail "synapse-start-timeout"
  wait_synapse_listener_ready || fail "synapse-listener-readiness-timeout"
  SYNAPSE_RESTORE_REQUIRED="false"
}

wait_for_provider_operation() {
  local pass_index="$1" directory room_id author_user encoded_room encoded_user response status deadline
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  author_user="$(<"${directory}/author-matrix-user")"
  encoded_room="$(uri_encode "${room_id}")"
  encoded_user="$(uri_encode "${author_user}")"
  printf '%s' '{"typing":false,"timeout":0}' >"${directory}/typing-request.json"
  response="${directory}/typing-response.json"
  # Container process state is not provider readiness. The listener gate
  # avoids manufacturing backoff while Synapse starts; this wider bounded
  # window then proves that the authenticated Application Service path also
  # recovers without weakening the backend's fail-closed retry policy.
  deadline=$(( $(date +%s) + PROVIDER_OPERATION_RECOVERY_TIMEOUT_SECONDS ))
  while :; do
    status="$(northbound_request PUT \
      "/_matrix/client/v3/rooms/${encoded_room}/typing/${encoded_user}" \
      "${directory}/author-headers" "${directory}/typing-request.json" "${response}")"
    if [[ "${status}" == "200" ]]; then
      rm -f -- "${response}" "${directory}/typing-request.json"
      return 0
    fi
    (( $(date +%s) < deadline )) || {
      rm -f -- "${response}" "${directory}/typing-request.json"
      return 1
    }
    sleep 2
  done
}

probe_other_surfaces_during_outage() {
  local pass_index="$1" directory platform_status profile_status
  directory="$(pass_dir "${pass_index}")"
  platform_status="$(northbound_request GET '/api/platform/config' - - \
    "${directory}/outage-platform-response.json")"
  profile_status="$(northbound_request GET '/api/me' "${directory}/author-headers" - \
    "${directory}/outage-profile-response.json")"
  rm -f -- "${directory}/outage-platform-response.json" "${directory}/outage-profile-response.json"
  [[ "${platform_status}:${profile_status}" == "200:200" ]] ||
    fail "outage-other-surfaces-unavailable"
}

assert_outage_operation_invisible() {
  local pass_index="$1" directory response status opaque
  directory="$(pass_dir "${pass_index}")"
  response="${directory}/outage-read-response.json"
  status="$(room_messages "${pass_index}" collaborator "${response}")"
  opaque="$(jq -r '.ciphertext' "${directory}/event-3-body.json")"
  if [[ "${status}" == "200" ]]; then
    ! grep -Fq -- "${opaque}" "${response}" || fail "outage-operation-visible"
    jq -e '([.chunk[] | select(.type == "m.room.encrypted")] | length) == 2' \
      "${response}" >/dev/null || fail "outage-visible-count-invalid"
  elif [[ "${status}" == "503" || "${status}" == "429" ]]; then
    support_safe_provider_error "${response}" || fail "outage-read-error-not-support-safe"
    ! grep -Fq -- "${opaque}" "${response}" || fail "outage-error-leaked-opaque-content"
  else
    fail "outage-read-status-${status}"
  fi
  rm -f -- "${response}"
  unset opaque
}

run_outage_retry() {
  local pass_index="$1" directory room_id encoded_room encoded_tx response status opaque retry_response retry_event repeat_event
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  encoded_room="$(uri_encode "${room_id}")"
  encoded_tx="$(uri_encode "$(<"${directory}/event-3-transaction")")"
  stop_synapse_for_outage
  probe_other_surfaces_during_outage "${pass_index}"
  response="${directory}/outage-send-response.json"
  status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/send/m.room.encrypted/${encoded_tx}" \
    "${directory}/author-headers" "${directory}/event-3-body.json" "${response}")"
  [[ "${status}" == "503" || "${status}" == "429" ]] || fail "outage-send-status-${status}"
  support_safe_provider_error "${response}" || fail "outage-send-error-not-support-safe"
  opaque="$(jq -r '.ciphertext' "${directory}/event-3-body.json")"
  ! grep -Fq -- "${opaque}" "${response}" || fail "outage-send-leaked-opaque-content"
  rm -f -- "${response}"
  assert_outage_operation_invisible "${pass_index}"
  start_synapse_after_outage
  wait_for_provider_operation "${pass_index}" || fail "provider-recovery-timeout"
  capture_private_evidence "${pass_index}" "${directory}/outage-before-retry-evidence.json"
  assert_provider_evidence_counts "${directory}/outage-before-retry-evidence.json" 2 1

  retry_response="${directory}/outage-retry-response.json"
  status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/send/m.room.encrypted/${encoded_tx}" \
    "${directory}/author-headers" "${directory}/event-3-body.json" "${retry_response}")"
  [[ "${status}" == "200" ]] || fail "outage-retry-status-${status}"
  retry_event="$(jq -r '.event_id // empty' "${retry_response}")"
  [[ "${retry_event}" == '$'*:* ]] || fail "outage-retry-projection-invalid"
  printf '%s' "${retry_event}" >"${directory}/event-3-id"

  response="${directory}/outage-repeat-response.json"
  status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/send/m.room.encrypted/${encoded_tx}" \
    "${directory}/author-headers" "${directory}/event-3-body.json" "${response}")"
  [[ "${status}" == "200" ]] || fail "outage-repeat-status-${status}"
  repeat_event="$(jq -r '.event_id // empty' "${response}")"
  [[ "${retry_event}" == "${repeat_event}" ]] || fail "outage-repeat-event-mismatch"
  rm -f -- "${retry_response}" "${response}"

  capture_private_evidence "${pass_index}" "${directory}/outage-after-retry-evidence.json"
  assert_provider_evidence_counts "${directory}/outage-after-retry-evidence.json" 3 0
  verify_event_observed_once "${pass_index}" collaborator 3
  unset room_id opaque retry_event repeat_event
}

run_collaboration_pass() {
  local pass_index="$1" directory namespace_short tx1 tx2 tx3
  directory="$(pass_dir "${pass_index}")"
  namespace_short="$(sha256 "${NAMESPACE}" | cut -c1-16)"
  register_pass_sessions "${pass_index}"
  create_encrypted_room "${pass_index}"
  join_collaborator "${pass_index}"
  create_encrypted_body "${pass_index}" 1
  create_encrypted_body "${pass_index}" 2
  create_encrypted_body "${pass_index}" 3
  tx1="e2e-${namespace_short}-${pass_index}-author"
  tx2="e2e-${namespace_short}-${pass_index}-collaborator"
  tx3="e2e-${namespace_short}-${pass_index}-outage"
  printf '%s' "${tx1}" >"${directory}/event-1-transaction"
  printf '%s' "${tx2}" >"${directory}/event-2-transaction"
  printf '%s' "${tx3}" >"${directory}/event-3-transaction"
  build_evidence_request "${pass_index}"

  send_encrypted_event "${pass_index}" 1 author "${tx1}" "${directory}/event-1-id"
  verify_event_observed_once "${pass_index}" collaborator 1
  send_encrypted_event "${pass_index}" 2 collaborator "${tx2}" "${directory}/event-2-id"
  verify_event_observed_once "${pass_index}" author 2
  assert_outsider_denied "${pass_index}"
  run_outage_retry "${pass_index}"
  verify_complete_room_readback "${pass_index}" author
  verify_complete_room_readback "${pass_index}" collaborator
}

wait_backend_liveness() {
  local deadline=$(( $(date +%s) + 60 )) status output="${PRIVATE_STATE_DIR}/backend-liveness.json"
  while :; do
    status="$(northbound_request GET '/api/health/live' - - "${output}")"
    if [[ "${status}" == "200" ]]; then
      rm -f -- "${output}"
      return 0
    fi
    (( $(date +%s) < deadline )) || {
      rm -f -- "${output}"
      return 1
    }
    sleep 2
  done
}

prove_restart_continuity() {
  local pass_index
  BACKEND_RESTORE_REQUIRED="true"
  docker restart "${BACKEND_CONTAINER}" >/dev/null || fail "backend-restart-failed"
  wait_container_running "${BACKEND_CONTAINER}" || fail "backend-restart-timeout"
  wait_backend_liveness || fail "backend-liveness-timeout"
  BACKEND_RESTORE_REQUIRED="false"
  for pass_index in 1 2; do
    register_pass_sessions "${pass_index}"
    verify_complete_room_readback "${pass_index}" author
    verify_complete_room_readback "${pass_index}" collaborator
  done

  SYNAPSE_RESTORE_REQUIRED="true"
  docker restart "${SYNAPSE_CONTAINER}" >/dev/null || fail "synapse-restart-failed"
  wait_container_running "${SYNAPSE_CONTAINER}" || fail "synapse-restart-timeout"
  wait_synapse_listener_ready || fail "synapse-restart-listener-readiness-timeout"
  SYNAPSE_RESTORE_REQUIRED="false"
  wait_for_provider_operation 1 || fail "synapse-restart-provider-timeout"
  for pass_index in 1 2; do
    verify_complete_room_readback "${pass_index}" author
    verify_complete_room_readback "${pass_index}" collaborator
    capture_private_evidence "${pass_index}" "$(pass_dir "${pass_index}")/restart-evidence.json"
    assert_provider_evidence_counts "$(pass_dir "${pass_index}")/restart-evidence.json" 3 0
  done
}

evidence_stability_tuple() {
  jq -c '[
    .callbackTransactionCount,
    .callbackDuplicateCount,
    .bridgeLedgerCount,
    .canonicalCommittedEventCount,
    .providerEncryptedEventCount,
    .pendingOperationCount,
    .failedOperationCount,
    .committedOperationCount
  ]' "$1"
}

wait_for_evidence_stability() {
  local pass_index="$1" output="$2" directory previous="" current="" attempt snapshot
  directory="$(pass_dir "${pass_index}")"
  for attempt in 1 2 3 4 5 6; do
    snapshot="${directory}/stability-${attempt}.json"
    capture_private_evidence "${pass_index}" "${snapshot}"
    current="$(evidence_stability_tuple "${snapshot}")"
    if [[ -n "${previous}" && "${current}" == "${previous}" ]]; then
      mv "${snapshot}" "${output}"
      rm -f -- "${directory}"/stability-*.json
      return 0
    fi
    previous="${current}"
    sleep 2
  done
  rm -f -- "${directory}"/stability-*.json
  return 1
}

private_callback_replay() {
  local request_path="$1" response_path="$2" status
  jq -cn --arg runId "${RUN_ID}" '{runId:$runId}' >"${request_path}"
  status="$(curl --silent --connect-timeout 3 --max-time 20 \
    --output "${response_path}" --write-out '%{http_code}' \
    --request POST \
    --header @"${PROOF_AUTH_HEADER_FILE}" \
    --header 'Content-Type: application/json' \
    --data-binary @"${request_path}" \
    "${BACKEND_ORIGIN}/api/internal/e2e/chat/provider-proof/callback-replay" 2>/dev/null || true)"
  rm -f -- "${request_path}"
  [[ "${status}" == "200" ]] || return 1
  jq -e '
    .contractVersion == "chat-provider-callback-replay-v1" and
    .replayed == true and .supportSafe == true and
    (.callbackCorrelationHash | test("^[0-9a-f]{64}$"))
  ' "${response_path}" >/dev/null 2>&1
}

prove_callback_replay() {
  local pass_index request response before after
  for pass_index in 1 2; do
    wait_for_evidence_stability "${pass_index}" "$(pass_dir "${pass_index}")/replay-before.json" ||
      fail "provider-evidence-not-stable"
  done
  request="${PRIVATE_STATE_DIR}/callback-replay-request.json"
  response="${PRIVATE_STATE_DIR}/callback-replay-response.json"
  # The backend captured the first successfully processed, non-empty encrypted
  # Synapse callback. This trigger re-enters that exact transaction and body;
  # neither provider references nor the raw payload leave the backend process.
  private_callback_replay "${request}" "${response}" || fail "callback-replay-failed"
  rm -f -- "${response}"

  for pass_index in 1 2; do
    before="$(pass_dir "${pass_index}")/replay-before.json"
    after="$(pass_dir "${pass_index}")/replay-after.json"
    capture_private_evidence "${pass_index}" "${after}"
    assert_provider_evidence_counts "${after}" 3 0
    jq -e --slurpfile before "${before}" '
      .canonicalCommittedEventCount == $before[0].canonicalCommittedEventCount and
      .providerEncryptedEventCount == $before[0].providerEncryptedEventCount and
      .pendingOperationCount == $before[0].pendingOperationCount and
      .failedOperationCount == $before[0].failedOperationCount and
      .committedOperationCount == $before[0].committedOperationCount and
      .bridgeLedgerCount == $before[0].bridgeLedgerCount and
      .callbackTransactionCount == $before[0].callbackTransactionCount and
      .callbackDuplicateCount == ($before[0].callbackDuplicateCount + 1)
    ' "${after}" >/dev/null || fail "callback-replay-delta-invalid"
  done
}

redact_event() {
  local pass_index="$1" event_index="$2" role="$3" directory room_id event_id redaction_event_id
  local encoded_room encoded_event tx response status
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  event_id="$(<"${directory}/event-${event_index}-id")"
  encoded_room="$(uri_encode "${room_id}")"
  encoded_event="$(uri_encode "${event_id}")"
  tx="cleanup-${pass_index}-${event_index}-$(sha256 "${NAMESPACE}" | cut -c1-12)"
  response="${directory}/cleanup-redact-${event_index}.json"
  status="$(northbound_request PUT \
    "/_matrix/client/v3/rooms/${encoded_room}/redact/${encoded_event}/$(uri_encode "${tx}")" \
    "${directory}/${role}-headers" - "${response}")"
  [[ "${status}" == "200" ]] || fail "cleanup-redact-${event_index}-status-${status}"
  redaction_event_id="$(jq -r '.event_id // empty' "${response}")"
  [[ "${redaction_event_id}" == '$'*:* && "${redaction_event_id}" != "${event_id}" ]] ||
    fail "cleanup-redact-${event_index}-projection-invalid"
  rm -f -- "${response}"
}

leave_room() {
  local pass_index="$1" role="$2" directory room_id encoded response status
  directory="$(pass_dir "${pass_index}")"
  room_id="$(<"${directory}/room-id")"
  encoded="$(uri_encode "${room_id}")"
  response="${directory}/cleanup-leave-${role}.json"
  status="$(northbound_request POST "/_matrix/client/v3/rooms/${encoded}/leave" \
    "${directory}/${role}-headers" - "${response}")"
  rm -f -- "${response}"
  [[ "${status}" == "200" ]] || fail "cleanup-leave-${role}-status-${status}"
}

cleanup_run_resources() {
  local pass_index directory response author_status collaborator_status
  for pass_index in 1 2; do
    redact_event "${pass_index}" 1 author
    redact_event "${pass_index}" 2 collaborator
    redact_event "${pass_index}" 3 author
    leave_room "${pass_index}" collaborator
    leave_room "${pass_index}" author
    directory="$(pass_dir "${pass_index}")"
    response="${directory}/cleanup-author-membership.json"
    author_status="$(room_messages "${pass_index}" author "${response}")"
    rm -f -- "${response}"
    response="${directory}/cleanup-collaborator-membership.json"
    collaborator_status="$(room_messages "${pass_index}" collaborator "${response}")"
    rm -f -- "${response}"
    [[ "${author_status}:${collaborator_status}" == "403:403" ]] ||
      fail "cleanup-membership-readback-invalid"
  done
}

write_pass_evidence() {
  local pass_index="$1" output="$2" directory measured scenario_hash correlation1 correlation2 correlation3 age
  directory="$(pass_dir "${pass_index}")"
  measured="${directory}/replay-after.json"
  scenario_hash="$(sha256 "${NAMESPACE}:${CANDIDATE_COMMIT}:matrix-synapse-pass-${pass_index}")"
  correlation1="$(<"${directory}/event-1-correlation.sha256")"
  correlation2="$(<"${directory}/event-2-correlation.sha256")"
  correlation3="$(<"${directory}/event-3-correlation.sha256")"
  age="$(jq -r '.providerObservationAgeSeconds' "${measured}")"
  jq -n \
    --argjson runIndex "${pass_index}" \
    --arg scenarioSha256 "${scenario_hash}" \
    --arg correlation1 "${correlation1}" \
    --arg correlation2 "${correlation2}" \
    --arg correlation3 "${correlation3}" \
    --argjson providerAge "${age}" '
      {
        runIndex:$runIndex,
        status:"passed",
        scenarioSha256:$scenarioSha256,
        directProviderApiReadback:true,
        authenticatedProviderReadback:true,
        authorizedVirtualUserCount:2,
        authorJoined:true,
        collaboratorJoined:true,
        outsiderProviderMappingAbsent:true,
        outsiderRoomMembershipAbsent:true,
        outsiderReadDenied:true,
        outsiderWriteDenied:true,
        correlatedEncryptedEventCount:3,
        correlatedPlaintextEventCount:0,
        plaintextSentinelAbsent:true,
        canonicalCommittedEventCount:3,
        providerAcknowledgedEventCount:3,
        providerMembershipExact:true,
        providerEncryptionStateVerified:true,
        providerEventMappingExact:true,
        providerCiphertextCorrelationExact:true,
        correlationSha256:[$correlation1,$correlation2,$correlation3],
        backendRestartContinuity:true,
        providerRestartContinuity:true,
        outageOperationInvisible:true,
        providerUnavailableSupportSafe:true,
        otherSurfacesReachableDuringOutage:true,
        sameTransactionRetry:true,
        retryCommittedExactlyOnce:true,
        pendingOperationCountAfterRecovery:0,
        duplicateOperationCount:0,
        callbackReplayDeduplicated:true,
        canonicalEventDeltaAfterReplay:0,
        providerEventDeltaAfterReplay:0,
        ledgerDeltaAfterReplay:0,
        providerReadiness:"available",
        providerHealthCached:true,
        providerHealthObservationAgeSeconds:$providerAge,
        runResourcesCleanupComplete:true,
        supportSafe:true
      }
    ' >"${output}"
}

write_support_safe_evidence() {
  local namespace_hash completed_at pass1 pass2
  namespace_hash="$(sha256 "${NAMESPACE}")"
  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  pass1="${PRIVATE_STATE_DIR}/pass-1-support-safe.json"
  pass2="${PRIVATE_STATE_DIR}/pass-2-support-safe.json"
  write_pass_evidence 1 "${pass1}"
  write_pass_evidence 2 "${pass2}"
  mkdir -p -- "$(dirname -- "${OUTPUT_PATH}")"
  FINAL_TEMP_OUTPUT="${OUTPUT_PATH}.tmp.$$"
  jq -n \
    --arg candidate "${CANDIDATE_COMMIT}" \
    --arg namespaceSha256 "${namespace_hash}" \
    --arg completedAtUtc "${completed_at}" \
    --slurpfile pass1 "${pass1}" \
    --slurpfile pass2 "${pass2}" '
      {
        schemaVersion:"weave.isolated-e2e-chat-provider.v1",
        candidateCommit:$candidate,
        namespaceSha256:$namespaceSha256,
        completedAtUtc:$completedAtUtc,
        supportSafe:true,
        isolatedRuntimeVerified:true,
        providerEvidenceEndpointReadOnly:true,
        callbackReplayTriggerScoped:true,
        exactRunBindingVerified:true,
        independentProofCredentialVerified:true,
        applicationServiceCredentialReusedForProof:false,
        southboundProviderAdapterVerified:true,
        applicationServiceBoundaryVerified:true,
        canonicalDurableStorageVerified:true,
        persistentHumanIdentityChanged:false,
        repeatCount:2,
        credentialsIncluded:false,
        rawIdentityIncluded:false,
        rawProviderReferenceIncluded:false,
        rawProviderPayloadIncluded:false,
        rawCiphertextIncluded:false,
        rawContentIncluded:false,
        passes:[$pass1[0],$pass2[0]]
      }
    ' >"${FINAL_TEMP_OUTPUT}"
  chmod 600 "${FINAL_TEMP_OUTPUT}"
  mv -- "${FINAL_TEMP_OUTPUT}" "${OUTPUT_PATH}"
  FINAL_TEMP_OUTPUT=""
}

initialize_provider_proof() {
  local expected_origin pass_index
  for command in curl docker jq openssl python3 shasum; do
    require_command "${command}"
  done
  [[ "${CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || fail "candidate-commit-invalid"
  [[ -n "${OUTPUT_PATH}" ]] || fail "output-path-required"
  derive_paths_and_names
  validate_paths
  [[ -f "${CREDENTIAL_ENV_PATH}" ]] || fail "credentials-env-missing"
  [[ -f "${STARTUP_ENV_PATH}" ]] || fail "startup-env-missing"
  load_runtime_environment
  assert_isolated_runtime
  BACKEND_CONTAINER="${WEAVE_E2E_BACKEND_CONTAINER:-$(weave_container_name backend)}"
  SYNAPSE_CONTAINER="${WEAVE_E2E_SYNAPSE_CONTAINER:-$(weave_container_name synapse)}"
  verify_backend_rebac_runtime
  assert_provider_identity_manifest
  verify_chat_runtime
  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "keycloak-admin-credential-missing"
  expected_origin="http://127.0.0.1:${TF_VAR_backend_host_port:-48084}"
  BACKEND_ORIGIN="${BACKEND_ORIGIN:-${expected_origin}}"
  [[ "${BACKEND_ORIGIN}" == "${expected_origin}" || \
    "${BACKEND_ORIGIN}" == "http://localhost:${TF_VAR_backend_host_port:-48084}" ]] ||
    fail "backend-origin-not-isolated-loopback"

  PRIVATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/weave-chat-provider-proof.XXXXXX")"
  chmod 700 "${PRIVATE_STATE_DIR}"
  umask 077
  trap on_provider_proof_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  mkdir -p "${PRIVATE_STATE_DIR}/pass-1" "${PRIVATE_STATE_DIR}/pass-2"
  prepare_private_proof_authentication

  ADMIN_ACCESS_TOKEN="$(admin_token)"
  [[ -n "${ADMIN_ACCESS_TOKEN}" ]] || fail "keycloak-admin-authentication-failed"
  KEYCLOAK_API_BASE="$(api_base)"
  enable_direct_grants_for_sessions
  for pass_index in 1 2; do
    mint_user_session "${pass_index}" author "${AUTHOR_USERNAME}" "${AUTHOR_PASSWORD}"
    mint_user_session "${pass_index}" collaborator "${COLLABORATOR_USERNAME}" "${COLLABORATOR_PASSWORD}"
    mint_user_session "${pass_index}" outsider "${OUTSIDER_USERNAME}" "${OUTSIDER_PASSWORD}"
    validate_session_identity_binding "${pass_index}" author "${AUTHOR_USERNAME}"
    validate_session_identity_binding "${pass_index}" collaborator "${COLLABORATOR_USERNAME}"
    validate_session_identity_binding "${pass_index}" outsider "${OUTSIDER_USERNAME}"
  done
  restore_direct_grants || fail "keycloak-client-restoration-failed"
  ADMIN_ACCESS_TOKEN=""
}

main_provider_proof() {
  parse_provider_proof_args "$@"
  initialize_provider_proof
  run_collaboration_pass 1
  run_collaboration_pass 2
  prove_callback_replay
  prove_restart_continuity
  cleanup_run_resources
  write_support_safe_evidence
  printf 'MATRIX_SYNAPSE_PROVIDER_PERSISTENCE_RESULT status=passed repeatCount=2 southboundProviderAdapterVerified=true applicationServiceBoundaryVerified=true canonicalDurableStorageVerified=true directProviderApiReadback=true providerMembershipExact=true providerEncryptionStateVerified=true providerEventMappingExact=true providerCiphertextCorrelationExact=true proofCredential=independent exactRunBinding=true backendRestartContinuity=true providerRestartContinuity=true supportSafe=true\n'
  printf 'MATRIX_SYNAPSE_PROVIDER_EXACTLY_ONCE_RESULT status=passed repeatCount=2 correlatedEncryptedEventCountPerPass=3 outageOperationInvisible=true sameTransactionRetry=true retryCommittedExactlyOnce=true duplicateOperationCount=0 supportSafe=true\n'
  printf 'MATRIX_SYNAPSE_PROVIDER_REPLAY_RESULT status=passed callbackReplayTriggerScoped=true callbackReplayDeduplicated=true canonicalEventDeltaAfterReplay=0 providerEventDeltaAfterReplay=0 ledgerDeltaAfterReplay=0 supportSafe=true\n'
}

main_provider_proof "$@"
