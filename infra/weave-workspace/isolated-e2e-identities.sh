#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${ROOT_DIR}/lib/runtime-namespace.sh"

OPERATION=""
RUN_ID="${WEAVE_E2E_RUN_ID:-}"
OUTPUT_ROOT="${WEAVE_E2E_OUTPUT_ROOT:-${ROOT_DIR}/.generated/isolated-e2e}"
CREDENTIAL_ENV_PATH="${WEAVE_E2E_CREDENTIAL_ENV_PATH:-}"
STARTUP_ENV_PATH="${WEAVE_E2E_STARTUP_ENV_PATH:-}"
IDENTITY_MANIFEST_PATH="${WEAVE_E2E_IDENTITY_MANIFEST_PATH:-}"
CLEANUP_EVIDENCE_PATH="${WEAVE_E2E_CLEANUP_EVIDENCE_PATH:-}"
AUTHORIZATION_EVIDENCE_PATH="${WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH:-}"
CHAT_PROOF_TOKEN_PATH=""
TEARDOWN_OWNERSHIP_FILE="${WEAVE_TEARDOWN_OWNERSHIP_FILE:-}"
STACK_BOOTSTRAP_ENV="${WEAVE_E2E_STACK_BOOTSTRAP_ENV:-}"

NAMESPACE=""
REALM=""
TENANT_ID=""
WORKSPACE_CONTEXT="workspace-default"
OUTSIDER_CONTEXT=""
AUTHOR_USERNAME=""
COLLABORATOR_USERNAME=""
OUTSIDER_USERNAME=""
AUTHOR_PASSWORD=""
COLLABORATOR_PASSWORD=""
OUTSIDER_PASSWORD=""

log() { printf '%s\n' "$*"; }
fail() { printf 'ISOLATED_E2E_IDENTITY_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: isolated-e2e-identities.sh prepare|provision|cleanup --run-id ID [options]

Lifecycle for the three disposable identities in a fully isolated live-E2E stack.

Options:
  --run-id ID                 Stable unique ID for one disposable run.
  --output-root PATH          Private run artifact root.
  --credentials-env PATH      Private 0600 identity credential env.
  --startup-env PATH          Startup-only isolated stack/OpenTofu env.
  --identity-manifest PATH    Support-safe hashed identity evidence.
  --cleanup-evidence PATH     Support-safe cleanup evidence.
  --stack-bootstrap-env PATH  Private bootstrap env written by install.sh.

prepare prints these stable integration variables:
  WEAVE_E2E_OUTPUT_ROOT
  WEAVE_E2E_RUN_NAMESPACE
  WEAVE_E2E_CREDENTIAL_ENV_PATH
  WEAVE_E2E_STARTUP_ENV_PATH
  WEAVE_E2E_IDENTITY_MANIFEST_PATH
  WEAVE_E2E_CLEANUP_EVIDENCE_PATH
  WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH
  WEAVE_E2E_STACK_BOOTSTRAP_ENV
  WEAVE_TEARDOWN_OWNERSHIP_FILE

provision and cleanup require WEAVE_E2E_STACK_SCOPE=isolated. They refuse
persistent dogfood/default inputs and mutate only resources carrying the exact
run marker. Provider data is removed by the isolated stack namespace teardown;
this helper never targets persistent dogfood containers or identities.
EOF
}

sha256() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
}

random_password() {
  openssl rand -base64 24 | tr -d '\n'
}

random_proof_token() {
  openssl rand -hex 48
}

parse_args() {
  [[ $# -gt 0 ]] || { usage >&2; exit 2; }
  OPERATION="$1"
  shift
  case "${OPERATION}" in
    prepare|provision|cleanup) ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown operation '${OPERATION}'" ;;
  esac

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --run-id) RUN_ID="${2:-}"; shift 2 ;;
      --output-root) OUTPUT_ROOT="${2:-}"; shift 2 ;;
      --credentials-env) CREDENTIAL_ENV_PATH="${2:-}"; shift 2 ;;
      --startup-env) STARTUP_ENV_PATH="${2:-}"; shift 2 ;;
      --identity-manifest) IDENTITY_MANIFEST_PATH="${2:-}"; shift 2 ;;
      --cleanup-evidence) CLEANUP_EVIDENCE_PATH="${2:-}"; shift 2 ;;
      --stack-bootstrap-env) STACK_BOOTSTRAP_ENV="${2:-}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
}

derive_paths_and_names() {
  [[ -n "${RUN_ID}" ]] || fail "--run-id is required"
  [[ ${#RUN_ID} -le 160 ]] || fail "run ID is too long"
  [[ "${RUN_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{7,159}$ ]] ||
    fail "run ID must use only bounded environment-safe characters"

  local run_hash
  run_hash="$(sha256 "${RUN_ID}")"
  NAMESPACE="weave-e2e-${run_hash:0:16}"
  REALM="${TF_VAR_tenant_slug:-weave}"
  TENANT_ID="${NAMESPACE}-tenant"
  OUTSIDER_CONTEXT="${NAMESPACE}-outside"
  AUTHOR_USERNAME="${NAMESPACE}-author"
  COLLABORATOR_USERNAME="${NAMESPACE}-collaborator"
  OUTSIDER_USERNAME="${NAMESPACE}-outsider"

  local run_dir="${OUTPUT_ROOT}/${NAMESPACE}"
  CREDENTIAL_ENV_PATH="${CREDENTIAL_ENV_PATH:-${run_dir}/credentials.env}"
  STARTUP_ENV_PATH="${STARTUP_ENV_PATH:-${run_dir}/startup.env}"
  IDENTITY_MANIFEST_PATH="${IDENTITY_MANIFEST_PATH:-${run_dir}/identity-manifest.json}"
  CLEANUP_EVIDENCE_PATH="${CLEANUP_EVIDENCE_PATH:-${run_dir}/cleanup-evidence.json}"
  AUTHORIZATION_EVIDENCE_PATH="${AUTHORIZATION_EVIDENCE_PATH:-${run_dir}/authorization-evidence.json}"
  CHAT_PROOF_TOKEN_PATH="${CHAT_PROOF_TOKEN_PATH:-${run_dir}/chat-provider-proof.token}"
  TEARDOWN_OWNERSHIP_FILE="${TEARDOWN_OWNERSHIP_FILE:-${run_dir}/teardown-ownership.json}"
  STACK_BOOTSTRAP_ENV="${STACK_BOOTSTRAP_ENV:-${ROOT_DIR}/.generated/isolated/${NAMESPACE}/bootstrap.env}"
}

validate_private_path() {
  local path="$1"
  [[ "${path}" == "${OUTPUT_ROOT}"/* ]] || fail "run artifacts must stay below the configured output root"
}

validate_paths() {
  validate_private_path "${CREDENTIAL_ENV_PATH}"
  validate_private_path "${STARTUP_ENV_PATH}"
  validate_private_path "${IDENTITY_MANIFEST_PATH}"
  validate_private_path "${CLEANUP_EVIDENCE_PATH}"
  validate_private_path "${AUTHORIZATION_EVIDENCE_PATH}"
  validate_private_path "${CHAT_PROOF_TOKEN_PATH}"
  validate_private_path "${TEARDOWN_OWNERSHIP_FILE}"
}

print_integration_variables() {
  printf 'WEAVE_E2E_OUTPUT_ROOT=%q\n' "${OUTPUT_ROOT}"
  printf 'WEAVE_E2E_RUN_NAMESPACE=%q\n' "${NAMESPACE}"
  printf 'WEAVE_E2E_CREDENTIAL_ENV_PATH=%q\n' "${CREDENTIAL_ENV_PATH}"
  printf 'WEAVE_E2E_STARTUP_ENV_PATH=%q\n' "${STARTUP_ENV_PATH}"
  printf 'WEAVE_E2E_IDENTITY_MANIFEST_PATH=%q\n' "${IDENTITY_MANIFEST_PATH}"
  printf 'WEAVE_E2E_CLEANUP_EVIDENCE_PATH=%q\n' "${CLEANUP_EVIDENCE_PATH}"
  printf 'WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH=%q\n' "${AUTHORIZATION_EVIDENCE_PATH}"
  printf 'WEAVE_E2E_STACK_BOOTSTRAP_ENV=%q\n' "${STACK_BOOTSTRAP_ENV}"
  printf 'WEAVE_TEARDOWN_OWNERSHIP_FILE=%q\n' "${TEARDOWN_OWNERSHIP_FILE}"
}

require_teardown_ownership_inputs() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "isolated" ]] ||
    fail "prepare requires WEAVE_E2E_STACK_SCOPE=isolated"
  [[ "${WEAVE_CANDIDATE_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]] ||
    fail "prepare requires a lowercase 40-character WEAVE_CANDIDATE_COMMIT"
  weave_validate_support_safe_evidence_url "${WEAVE_CANDIDATE_EVIDENCE_REF:-}" ||
    fail "prepare requires a support-safe HTTPS WEAVE_CANDIDATE_EVIDENCE_REF without credentials, query, or fragment"
}

write_or_validate_teardown_ownership() {
  local expected_file="${OUTPUT_ROOT%/}/${NAMESPACE}/teardown-ownership.json"
  [[ "${TEARDOWN_OWNERSHIP_FILE}" == "${expected_file}" ]] ||
    fail "teardown ownership evidence must use the exact run-owned path"

  if [[ -f "${TEARDOWN_OWNERSHIP_FILE}" ]]; then
    jq -e \
      --arg namespace "${NAMESPACE}" \
      --arg runId "${RUN_ID}" \
      --arg candidateCommit "${WEAVE_CANDIDATE_COMMIT}" \
      --arg candidateEvidenceRef "${WEAVE_CANDIDATE_EVIDENCE_REF}" \
      '.schemaVersion == "weave.isolated-e2e-teardown-ownership.v1" and
       .scope == "isolated" and
       .namespace == $namespace and
       .runId == $runId and
       .candidateCommit == $candidateCommit and
       .candidateEvidenceRef == $candidateEvidenceRef and
       .resourcePrefix == $namespace' \
      "${TEARDOWN_OWNERSHIP_FILE}" >/dev/null ||
      fail "existing teardown ownership evidence does not match this run and candidate"
    chmod 600 "${TEARDOWN_OWNERSHIP_FILE}"
    return
  fi

  jq -n \
    --arg namespace "${NAMESPACE}" \
    --arg runId "${RUN_ID}" \
    --arg candidateCommit "${WEAVE_CANDIDATE_COMMIT}" \
    --arg candidateEvidenceRef "${WEAVE_CANDIDATE_EVIDENCE_REF}" \
    '{
      schemaVersion:"weave.isolated-e2e-teardown-ownership.v1",
      scope:"isolated",
      namespace:$namespace,
      runId:$runId,
      candidateCommit:$candidateCommit,
      candidateEvidenceRef:$candidateEvidenceRef,
      resourcePrefix:$namespace
    }' >"${TEARDOWN_OWNERSHIP_FILE}"
  chmod 600 "${TEARDOWN_OWNERSHIP_FILE}"
}

write_prepare_manifest() {
  local created_at
  created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  jq -n \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --arg tenantSha256 "$(sha256 "${TENANT_ID}")" \
    --arg workspaceSha256 "$(sha256 "${WORKSPACE_CONTEXT}")" \
    --arg outsideSha256 "$(sha256 "${OUTSIDER_CONTEXT}")" \
    --arg authorSha256 "$(sha256 "${AUTHOR_USERNAME}")" \
    --arg collaboratorSha256 "$(sha256 "${COLLABORATOR_USERNAME}")" \
    --arg outsiderSha256 "$(sha256 "${OUTSIDER_USERNAME}")" \
    --arg createdAt "${created_at}" \
    '{
      schemaVersion:"weave.isolated-e2e-identities.v1",
      createdAt:$createdAt,
      namespaceSha256:$namespaceSha256,
      tenantSha256:$tenantSha256,
      contextAuthorization:{mode:"isolated-startup-real-rebac",status:"prepared",principalClaim:"preferred_username",persistentDogfoodEligible:false},
      actors:[
        {role:"author",identitySha256:$authorSha256,contextSha256:$workspaceSha256,expectedWorkspaceAccess:"member"},
        {role:"collaborator",identitySha256:$collaboratorSha256,contextSha256:$workspaceSha256,expectedWorkspaceAccess:"member"},
        {role:"outsider",identitySha256:$outsiderSha256,contextSha256:$outsideSha256,expectedWorkspaceAccess:"denied"}
      ],
      providerBindings:{keycloak:"pending",nextcloud:"created_by_real_oidc_session",matrix:"created_by_real_oidc_session"},
      credentialsIncluded:false,
      rawProviderPayloadIncluded:false,
      persistentHumanIdentityChanged:false,
      supportSafe:true
    }' >"${IDENTITY_MANIFEST_PATH}"
}

prepare() {
  command -v jq >/dev/null || fail "jq is required"
  command -v openssl >/dev/null || fail "openssl is required"
  require_teardown_ownership_inputs
  mkdir -p "$(dirname -- "${CREDENTIAL_ENV_PATH}")"
  chmod 700 "$(dirname -- "${CREDENTIAL_ENV_PATH}")"
  write_or_validate_teardown_ownership

  if [[ -f "${CREDENTIAL_ENV_PATH}" ]]; then
    # shellcheck disable=SC1090
    source "${CREDENTIAL_ENV_PATH}"
    [[ "${WEAVE_E2E_RUN_NAMESPACE:-}" == "${NAMESPACE}" ]] || fail "existing credential env belongs to another run namespace"
    AUTHOR_PASSWORD="${WEAVE_E2E_AUTHOR_PASSWORD:-}"
    COLLABORATOR_PASSWORD="${WEAVE_E2E_COLLABORATOR_PASSWORD:-}"
    OUTSIDER_PASSWORD="${WEAVE_E2E_OUTSIDER_PASSWORD:-}"
    [[ -n "${AUTHOR_PASSWORD}" && -n "${COLLABORATOR_PASSWORD}" && -n "${OUTSIDER_PASSWORD}" ]] || fail "existing credential env is incomplete"
  else
    AUTHOR_PASSWORD="$(random_password)"
    COLLABORATOR_PASSWORD="$(random_password)"
    OUTSIDER_PASSWORD="$(random_password)"
    umask 077
    {
      printf 'export WEAVE_E2E_RUN_NAMESPACE=%q\n' "${NAMESPACE}"
      printf 'export WEAVE_E2E_AUTHOR_USERNAME=%q\n' "${AUTHOR_USERNAME}"
      printf 'export WEAVE_E2E_AUTHOR_EMAIL=%q\n' "${AUTHOR_USERNAME}@example.invalid"
      printf 'export WEAVE_E2E_AUTHOR_PASSWORD=%q\n' "${AUTHOR_PASSWORD}"
      printf 'export WEAVE_E2E_COLLABORATOR_USERNAME=%q\n' "${COLLABORATOR_USERNAME}"
      printf 'export WEAVE_E2E_COLLABORATOR_EMAIL=%q\n' "${COLLABORATOR_USERNAME}@example.invalid"
      printf 'export WEAVE_E2E_COLLABORATOR_PASSWORD=%q\n' "${COLLABORATOR_PASSWORD}"
      printf 'export WEAVE_E2E_OUTSIDER_USERNAME=%q\n' "${OUTSIDER_USERNAME}"
      printf 'export WEAVE_E2E_OUTSIDER_EMAIL=%q\n' "${OUTSIDER_USERNAME}@example.invalid"
      printf 'export WEAVE_E2E_OUTSIDER_PASSWORD=%q\n' "${OUTSIDER_PASSWORD}"
    } >"${CREDENTIAL_ENV_PATH}"
    chmod 600 "${CREDENTIAL_ENV_PATH}"
  fi

  if [[ -e "${CHAT_PROOF_TOKEN_PATH}" ]]; then
    [[ -f "${CHAT_PROOF_TOKEN_PATH}" && ! -L "${CHAT_PROOF_TOKEN_PATH}" ]] ||
      fail "existing Chat proof credential is not a regular private file"
    [[ "$(<"${CHAT_PROOF_TOKEN_PATH}")" =~ ^[0-9a-f]{96}$ ]] ||
      fail "existing Chat proof credential is invalid"
  else
    umask 077
    random_proof_token >"${CHAT_PROOF_TOKEN_PATH}"
  fi
  chmod 600 "${CHAT_PROOF_TOKEN_PATH}"

  local memberships run_hash
  local port_seed port_base
  run_hash="$(sha256 "${RUN_ID}")"
  port_seed=$((16#${run_hash:0:4}))
  port_base=$((32000 + (port_seed % 1800) * 16))
  memberships="$(jq -cn \
    --arg tenant "${TENANT_ID}" \
    --arg workspace "${WORKSPACE_CONTEXT}" \
    --arg outside "${OUTSIDER_CONTEXT}" \
    --arg author "user:${AUTHOR_USERNAME}" \
    --arg collaborator "user:${COLLABORATOR_USERNAME}" \
    --arg outsider "user:${OUTSIDER_USERNAME}" \
    '[
      {tenant_id:$tenant,context_id:$workspace,principal_ref:$author,role:"MEMBER",source:"isolated-live-e2e"},
      {tenant_id:$tenant,context_id:$workspace,principal_ref:$collaborator,role:"MEMBER",source:"isolated-live-e2e"},
      {tenant_id:$tenant,context_id:$outside,principal_ref:$outsider,role:"MEMBER",source:"isolated-live-e2e"}
    ]')"
  {
    printf 'export WEAVE_LOCAL_CREDENTIAL_STATE_FILE=%q\n' none
    printf 'export WEAVE_LOCAL_TLS_STATE_DIR=%q\n' none
    printf 'export WEAVE_E2E_OUTPUT_ROOT=%q\n' "${OUTPUT_ROOT}"
    printf 'export WEAVE_E2E_RUN_ID=%q\n' "${RUN_ID}"
    printf 'export WEAVE_CANDIDATE_COMMIT=%q\n' "${WEAVE_CANDIDATE_COMMIT}"
    printf 'export WEAVE_CANDIDATE_EVIDENCE_REF=%q\n' "${WEAVE_CANDIDATE_EVIDENCE_REF}"
    printf 'export WEAVE_TEARDOWN_OWNERSHIP_FILE=%q\n' "${TEARDOWN_OWNERSHIP_FILE}"
    printf 'export TF_VAR_isolated_e2e_enabled=%q\n' true
    printf 'export TF_VAR_isolated_e2e_namespace=%q\n' "${NAMESPACE}"
    printf 'export TF_VAR_isolated_e2e_context_memberships=%q\n' "${memberships}"
    printf 'export TF_VAR_chat_e2e_proof_enabled=%q\n' true
    printf 'export TF_VAR_chat_e2e_proof_token_host_path=%q\n' "${CHAT_PROOF_TOKEN_PATH}"
    printf 'export TF_VAR_chat_e2e_proof_run_id=%q\n' "${RUN_ID}"
    printf 'export TF_VAR_tenant_slug=%q\n' weave
    printf 'export TF_VAR_docker_network_name=%q\n' "${NAMESPACE}_network"
    printf 'export TF_VAR_proxy_http_host_port=%q\n' "$((port_base + 0))"
    printf 'export TF_VAR_proxy_host_port=%q\n' "$((port_base + 1))"
    printf 'export TF_VAR_keycloak_host_port=%q\n' "$((port_base + 2))"
    printf 'export TF_VAR_keycloak_management_host_port=%q\n' "$((port_base + 3))"
    printf 'export TF_VAR_mailpit_web_host_port=%q\n' "$((port_base + 4))"
    printf 'export TF_VAR_mas_host_port=%q\n' "$((port_base + 5))"
    printf 'export TF_VAR_synapse_host_port=%q\n' "$((port_base + 6))"
    printf 'export TF_VAR_nextcloud_host_port=%q\n' "$((port_base + 7))"
    printf 'export TF_VAR_backend_host_port=%q\n' "$((port_base + 8))"
    printf 'export TF_VAR_mcp_host_port=%q\n' "$((port_base + 9))"
    printf 'export TF_VAR_keycloak_smtp_host=%q\n' "${NAMESPACE}-mailpit"
    printf 'export TF_VAR_create_test_user=%q\n' false
    printf 'export TF_VAR_context_authorization_bootstrap_enabled=%q\n' false
    printf 'export TF_VAR_context_authorization_dogfood_principal_ref=%q\n' ""
    printf 'export TF_VAR_context_authorization_default_tenant_id=%q\n' "${TENANT_ID}"
    printf 'export TF_VAR_context_authorization_principal_claim=%q\n' preferred_username
    printf 'export TF_VAR_context_authorization_principal_ref_prefix=%q\n' 'user:'
  } >"${STARTUP_ENV_PATH}"
  chmod 600 "${STARTUP_ENV_PATH}"

  [[ -f "${IDENTITY_MANIFEST_PATH}" ]] || write_prepare_manifest
  print_integration_variables
}

load_runtime_environment() {
  [[ -f "${STARTUP_ENV_PATH}" ]] || fail "startup env is missing; run prepare first"
  [[ -f "${CREDENTIAL_ENV_PATH}" ]] || fail "credential env is missing; run prepare first"
  if [[ -f "${STACK_BOOTSTRAP_ENV}" ]]; then
    # shellcheck disable=SC1090
    source "${STACK_BOOTSTRAP_ENV}"
  fi
  # shellcheck disable=SC1090
  source "${STARTUP_ENV_PATH}"
  # shellcheck disable=SC1090
  source "${CREDENTIAL_ENV_PATH}"

  AUTHOR_USERNAME="${WEAVE_E2E_AUTHOR_USERNAME:-}"
  COLLABORATOR_USERNAME="${WEAVE_E2E_COLLABORATOR_USERNAME:-}"
  OUTSIDER_USERNAME="${WEAVE_E2E_OUTSIDER_USERNAME:-}"
  AUTHOR_PASSWORD="${WEAVE_E2E_AUTHOR_PASSWORD:-}"
  COLLABORATOR_PASSWORD="${WEAVE_E2E_COLLABORATOR_PASSWORD:-}"
  OUTSIDER_PASSWORD="${WEAVE_E2E_OUTSIDER_PASSWORD:-}"
}

assert_isolated_runtime() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "isolated" ]] || fail "provision/cleanup require WEAVE_E2E_STACK_SCOPE=isolated"
  [[ "${TF_VAR_isolated_e2e_enabled:-false}" == "true" ]] || fail "isolated E2E OpenTofu gate is not enabled"
  [[ "${TF_VAR_isolated_e2e_namespace:-}" == "${NAMESPACE}" ]] || fail "runtime namespace does not match this run"
  [[ "${TF_VAR_docker_network_name:-}" == "${NAMESPACE}_network" ]] || fail "runtime does not use the run-scoped Docker network"
  [[ "${TF_VAR_create_test_user:-false}" == "false" ]] || fail "static test-user provisioning must stay disabled"
  [[ "${TF_VAR_context_authorization_principal_claim:-}" == "preferred_username" ]] || fail "isolated ReBAC must use deterministic preferred_username principals"
  [[ "${TF_VAR_context_authorization_bootstrap_enabled:-false}" == "false" ]] || fail "persistent/bootstrap membership mode must stay disabled"
  [[ -z "${TF_VAR_context_authorization_dogfood_principal_ref:-}" ]] || fail "persistent dogfood principal input must be empty"
  [[ "${AUTHOR_USERNAME}" == "${NAMESPACE}-author" ]] || fail "author identity is not run-scoped"
  [[ "${COLLABORATOR_USERNAME}" == "${NAMESPACE}-collaborator" ]] || fail "collaborator identity is not run-scoped"
  [[ "${OUTSIDER_USERNAME}" == "${NAMESPACE}-outsider" ]] || fail "outsider identity is not run-scoped"
  [[ "${AUTHOR_USERNAME}" != "test" && "${AUTHOR_USERNAME}" != "massimo" ]] || fail "persistent/default identity selected"
}

keycloak_admin_url() {
  printf '%s' "${WEAVE_E2E_KEYCLOAK_ADMIN_URL:-http://127.0.0.1:${TF_VAR_keycloak_host_port:-48080}}"
}

admin_token() {
  curl --silent --show-error --fail \
    -X POST "$(keycloak_admin_url)/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${TF_VAR_keycloak_admin_username:-admin}" \
    --data-urlencode "password=${TF_VAR_keycloak_admin_password:-}" \
    --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

encode() { jq -nr --arg value "$1" '$value|@uri'; }
api_base() { printf '%s/admin/realms/%s' "$(keycloak_admin_url)" "$(encode "${REALM}")"; }

request() {
  local method="$1" url="$2" token="$3" body="${4:-}"
  # Keep provider resource identifiers out of failure logs; callers emit only
  # operation-level support-safe errors.
  local -a args=(--silent --fail -X "${method}" -H "Authorization: Bearer ${token}")
  if [[ -n "${body}" ]]; then
    args+=(-H 'Content-Type: application/json' --data "${body}")
  fi
  curl "${args[@]}" "${url}"
}

find_exact_id() {
  local payload="$1" field="$2" value="$3"
  jq -r --arg field "${field}" --arg value "${value}" \
    '[.[] | select(.[$field] == $value)] | if length == 1 then .[0].id else empty end' <<<"${payload}"
}

group_marker_matches() {
  local payload="$1"
  jq -e --arg marker "${NAMESPACE}" '(.attributes.weave_e2e_namespace // []) | index($marker) != null' <<<"${payload}" >/dev/null
}

user_role_from_username() {
  local username="$1"
  case "${username}" in
    "${NAMESPACE}-author") printf 'author' ;;
    "${NAMESPACE}-collaborator") printf 'collaborator' ;;
    "${NAMESPACE}-outsider") printf 'outsider' ;;
    *) fail "identity username is not owned by this E2E namespace" ;;
  esac
}

user_marker_matches() {
  local payload="$1" username="$2" role="$3"
  jq -e \
    --arg username "${username}" \
    --arg email "${username}@example.invalid" \
    --arg lastName "${NAMESPACE}:${role}" '
      .username == $username and
      .email == $email and
      .enabled == true and
      .emailVerified == true and
      .firstName == "Weave E2E" and
      .lastName == $lastName
    ' <<<"${payload}" >/dev/null
}

resolve_user() {
  local base="$1" token="$2" username="$3"
  request GET "${base}/users?username=$(encode "${username}")&exact=true" "${token}"
}

resolve_user_by_id() {
  local base="$1" token="$2" subject="$3"
  request GET "${base}/users/${subject}" "${token}"
}

resolve_group() {
  local base="$1" token="$2" group_id="$3"
  request GET "${base}/groups/${group_id}" "${token}"
}

ensure_group() {
  local base="$1" token="$2" name="$3"
  local groups group_id group
  groups="$(request GET "${base}/groups?search=$(encode "${name}")&exact=true" "${token}")"
  group_id="$(find_exact_id "${groups}" name "${name}")"
  if [[ -z "${group_id}" ]]; then
    request POST "${base}/groups" "${token}" "$(jq -cn --arg name "${name}" --arg marker "${NAMESPACE}" '{name:$name,attributes:{weave_e2e_namespace:[$marker]}}')" >/dev/null
    groups="$(request GET "${base}/groups?search=$(encode "${name}")&exact=true" "${token}")"
    group_id="$(find_exact_id "${groups}" name "${name}")"
  fi
  [[ -n "${group_id}" ]] || fail "run-scoped Keycloak group could not be resolved"
  group="$(resolve_group "${base}" "${token}" "${group_id}")"
  group_marker_matches "${group}" || fail "refusing to reuse an unmarked Keycloak group"
  printf '%s' "${group_id}"
}

global_group_id() {
  local base="$1" token="$2" name="$3" groups id
  groups="$(request GET "${base}/groups?search=$(encode "${name}")&exact=true" "${token}")"
  id="$(find_exact_id "${groups}" name "${name}")"
  [[ -n "${id}" ]] || fail "required Keycloak group '${name}' is unavailable"
  printf '%s' "${id}"
}

ensure_user() {
  local base="$1" token="$2" role="$3" username="$4" password="$5" run_group_id="$6"
  local users subject user client_uuid role_payload group_id org_id organizations clients
  users="$(resolve_user "${base}" "${token}" "${username}")"
  subject="$(find_exact_id "${users}" username "${username}")"
  if [[ -z "${subject}" ]]; then
    request POST "${base}/users" "${token}" "$(jq -cn \
      --arg username "${username}" \
      --arg email "${username}@example.invalid" \
      --arg marker "${NAMESPACE}" \
      --arg role "${role}" \
      '{username:$username,email:$email,enabled:true,emailVerified:true,firstName:"Weave E2E",lastName:($marker + ":" + $role)}')" >/dev/null
    users="$(resolve_user "${base}" "${token}" "${username}")"
    subject="$(find_exact_id "${users}" username "${username}")"
  fi
  [[ -n "${subject}" ]] || fail "run-scoped Keycloak user could not be resolved"
  user="$(resolve_user_by_id "${base}" "${token}" "${subject}")"
  user_marker_matches "${user}" "${username}" "${role}" || fail "refusing to reuse an unmarked Keycloak user"

  request PUT "${base}/users/${subject}/reset-password" "${token}" "$(jq -cn --arg value "${password}" '{type:"password",value:$value,temporary:false}')" >/dev/null
  request PUT "${base}/users/${subject}/groups/${run_group_id}" "${token}" >/dev/null
  for group_id in \
    "$(global_group_id "${base}" "${token}" workspace-members)" \
    "$(global_group_id "${base}" "${token}" weave-board-editors)" \
    "$(global_group_id "${base}" "${token}" weave-calendar-editors)"; do
    request PUT "${base}/users/${subject}/groups/${group_id}" "${token}" >/dev/null
  done

  clients="$(request GET "${base}/clients?clientId=weave-app" "${token}")"
  client_uuid="$(find_exact_id "${clients}" clientId weave-app)"
  [[ -n "${client_uuid}" ]] || fail "weave-app client is unavailable"
  role_payload="$(request GET "${base}/clients/${client_uuid}/roles/member" "${token}")"
  request POST "${base}/users/${subject}/role-mappings/clients/${client_uuid}" "${token}" "[$(jq -c . <<<"${role_payload}")]" >/dev/null

  organizations="$(request GET "${base}/organizations?search=$(encode "${REALM}")&exact=true" "${token}")"
  org_id="$(jq -r --arg value "${REALM}" '[.[] | select(.name == $value or .alias == $value)] | if length == 1 then .[0].id else empty end' <<<"${organizations}")"
  [[ -n "${org_id}" ]] || fail "tenant organization is unavailable"
  if ! request GET "${base}/organizations/${org_id}/members/${subject}" "${token}" >/dev/null 2>&1; then
    request POST "${base}/organizations/${org_id}/members" "${token}" "$(jq -cn --arg id "${subject}" '$id')" >/dev/null
  fi

  printf '%s' "${subject}"
}

verify_backend_rebac_runtime() {
  local backend_container="${WEAVE_E2E_BACKEND_CONTAINER:-$(weave_container_name backend)}"
  local runtime_env
  runtime_env="$(docker inspect --format '{{json .Config.Env}}' "${backend_container}")"

  jq -e --arg expected "WEAVE_ISOLATED_E2E_NAMESPACE=${NAMESPACE}" 'index($expected) != null' <<<"${runtime_env}" >/dev/null ||
    fail "backend runtime is not marked for this isolated namespace"

  local index expected_context expected_user
  for index in 0 1 2; do
    case "${index}" in
      0) expected_user="${AUTHOR_USERNAME}"; expected_context="${WORKSPACE_CONTEXT}" ;;
      1) expected_user="${COLLABORATOR_USERNAME}"; expected_context="${WORKSPACE_CONTEXT}" ;;
      2) expected_user="${OUTSIDER_USERNAME}"; expected_context="${OUTSIDER_CONTEXT}" ;;
    esac
    jq -e --arg expected "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_${index}_PRINCIPAL_REF=user:${expected_user}" 'index($expected) != null' <<<"${runtime_env}" >/dev/null ||
      fail "backend runtime is missing a run-scoped ReBAC principal"
    jq -e --arg expected "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_${index}_CONTEXT_ID=${expected_context}" 'index($expected) != null' <<<"${runtime_env}" >/dev/null ||
      fail "backend runtime has an incorrect run-scoped ReBAC context"
    jq -e --arg expected "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_${index}_SOURCE=isolated-live-e2e" 'index($expected) != null' <<<"${runtime_env}" >/dev/null ||
      fail "backend runtime membership did not come from the isolated live-E2E source"
  done

  if jq -e 'any(.[]; contains("local-dogfood-bootstrap") or contains("user:massimo"))' <<<"${runtime_env}" >/dev/null; then
    fail "persistent dogfood membership leaked into the isolated backend runtime"
  fi
}

write_provisioned_manifest() {
  local author_subject="$1" collaborator_subject="$2" outsider_subject="$3"
  local updated_at tmp
  updated_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  tmp="${IDENTITY_MANIFEST_PATH}.tmp"
  jq \
    --arg updatedAt "${updated_at}" \
    --arg authorSubject "$(sha256 "${author_subject}")" \
    --arg collaboratorSubject "$(sha256 "${collaborator_subject}")" \
    --arg outsiderSubject "$(sha256 "${outsider_subject}")" \
    '.updatedAt=$updatedAt
     | .contextAuthorization.status="active_runtime_verified"
     | .providerBindings.keycloak="provisioned"
     | .actors[0].subjectSha256=$authorSubject
     | .actors[1].subjectSha256=$collaboratorSubject
     | .actors[2].subjectSha256=$outsiderSubject' \
    "${IDENTITY_MANIFEST_PATH}" >"${tmp}"
  mv "${tmp}" "${IDENTITY_MANIFEST_PATH}"
}

provision() {
  command -v curl >/dev/null || fail "curl is required"
  command -v docker >/dev/null || fail "docker is required"
  command -v jq >/dev/null || fail "jq is required"
  load_runtime_environment
  assert_isolated_runtime
  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "isolated Keycloak admin credential is missing"

  local token base workspace_group outside_group author_subject collaborator_subject outsider_subject
  token="$(admin_token)"
  [[ -n "${token}" ]] || fail "isolated Keycloak admin authentication failed"
  base="$(api_base)"
  workspace_group="$(ensure_group "${base}" "${token}" "${NAMESPACE}-workspace-a")"
  outside_group="$(ensure_group "${base}" "${token}" "${NAMESPACE}-outside")"
  author_subject="$(ensure_user "${base}" "${token}" author "${AUTHOR_USERNAME}" "${AUTHOR_PASSWORD}" "${workspace_group}")"
  collaborator_subject="$(ensure_user "${base}" "${token}" collaborator "${COLLABORATOR_USERNAME}" "${COLLABORATOR_PASSWORD}" "${workspace_group}")"
  outsider_subject="$(ensure_user "${base}" "${token}" outsider "${OUTSIDER_USERNAME}" "${OUTSIDER_PASSWORD}" "${outside_group}")"

  verify_backend_rebac_runtime
  write_provisioned_manifest "${author_subject}" "${collaborator_subject}" "${outsider_subject}"
  log "ISOLATED_E2E_IDENTITIES state=provisioned namespaceSha256=$(sha256 "${NAMESPACE}") actors=3 supportSafe=true"
  print_integration_variables
}

delete_marked_user() {
  local base="$1" token="$2" username="$3"
  local users subject user role
  users="$(resolve_user "${base}" "${token}" "${username}")"
  subject="$(find_exact_id "${users}" username "${username}")"
  [[ -n "${subject}" ]] || { printf '0'; return; }
  user="$(resolve_user_by_id "${base}" "${token}" "${subject}")"
  role="$(user_role_from_username "${username}")"
  user_marker_matches "${user}" "${username}" "${role}" || fail "refusing to delete an unmarked Keycloak user"
  request DELETE "${base}/users/${subject}" "${token}" >/dev/null
  printf '1'
}

delete_marked_group() {
  local base="$1" token="$2" name="$3"
  local groups id group
  groups="$(request GET "${base}/groups?search=$(encode "${name}")&exact=true" "${token}")"
  id="$(find_exact_id "${groups}" name "${name}")"
  [[ -n "${id}" ]] || { printf '0'; return; }
  group="$(resolve_group "${base}" "${token}" "${id}")"
  group_marker_matches "${group}" || fail "refusing to delete an unmarked Keycloak group"
  request DELETE "${base}/groups/${id}" "${token}" >/dev/null
  printf '1'
}

cleanup_identities() {
  command -v curl >/dev/null || fail "curl is required"
  command -v jq >/dev/null || fail "jq is required"
  load_runtime_environment
  assert_isolated_runtime
  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "isolated Keycloak admin credential is missing"

  local token base users_deleted=0 groups_deleted=0 value completed_at
  token="$(admin_token)"
  [[ -n "${token}" ]] || fail "isolated Keycloak admin authentication failed"
  base="$(api_base)"
  for value in "${AUTHOR_USERNAME}" "${COLLABORATOR_USERNAME}" "${OUTSIDER_USERNAME}"; do
    users_deleted=$((users_deleted + $(delete_marked_user "${base}" "${token}" "${value}")))
  done
  for value in "${NAMESPACE}-workspace-a" "${NAMESPACE}-outside"; do
    groups_deleted=$((groups_deleted + $(delete_marked_group "${base}" "${token}" "${value}")))
  done

  for value in "${AUTHOR_USERNAME}" "${COLLABORATOR_USERNAME}" "${OUTSIDER_USERNAME}"; do
    [[ -z "$(find_exact_id "$(resolve_user "${base}" "${token}" "${value}")" username "${value}")" ]] ||
      fail "run-scoped Keycloak user remained after cleanup"
  done
  for value in "${NAMESPACE}-workspace-a" "${NAMESPACE}-outside"; do
    [[ -z "$(find_exact_id "$(request GET "${base}/groups?search=$(encode "${value}")&exact=true" "${token}")" name "${value}")" ]] ||
      fail "run-scoped Keycloak group remained after cleanup"
  done

  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname -- "${CLEANUP_EVIDENCE_PATH}")"
  jq -n \
    --arg completedAt "${completed_at}" \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --argjson usersDeleted "${users_deleted}" \
    --argjson groupsDeleted "${groups_deleted}" \
    '{
      schemaVersion:"weave.isolated-e2e-identity-cleanup.v1",
      completedAt:$completedAt,
      namespaceSha256:$namespaceSha256,
      keycloak:{usersDeleted:$usersDeleted,groupsDeleted:$groupsDeleted,runMarkerVerified:true},
      contextAuthorization:{cleanup:"isolated_stack_teardown_required"},
      providerData:{cleanup:"isolated_stack_teardown_required"},
      persistentHumanIdentityChanged:false,
      broadCleanupPerformed:false,
      expectedResourcesAbsent:true,
      credentialsIncluded:false,
      rawProviderPayloadIncluded:false,
      supportSafe:true
    }' >"${CLEANUP_EVIDENCE_PATH}"
  log "ISOLATED_E2E_IDENTITIES state=cleaned namespaceSha256=$(sha256 "${NAMESPACE}") usersDeleted=${users_deleted} groupsDeleted=${groups_deleted} supportSafe=true"
  if [[ "${users_deleted}:${groups_deleted}" == "3:2" ]]; then
    log "MULTI_USER_CLEANUP_RESULT status=passed usersDeleted=3 groupsDeleted=2 persistentHumanChanged=false supportSafe=true"
  else
    log "MULTI_USER_CLEANUP_RESULT status=passed usersDeleted=${users_deleted} groupsDeleted=${groups_deleted} persistentHumanChanged=false supportSafe=true expectedResourcesAbsent=true"
  fi
  print_integration_variables
}

main() {
  parse_args "$@"
  derive_paths_and_names
  validate_paths
  case "${OPERATION}" in
    prepare) prepare ;;
    provision) provision ;;
    cleanup) cleanup_identities ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
