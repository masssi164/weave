#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/isolated-e2e-identities.sh"
INFRA_MAIN="${ROOT_DIR}/01-infrastructure/main.tf"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
MOCK_STATE="${TMP_DIR}/mock-state"
OUTPUT_ROOT="${TMP_DIR}/output"
RUN_ID="fixture-run-42"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }

file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

grep -Fq 'var.isolated_e2e_enabled || (var.isolated_e2e_namespace == "" && length(var.isolated_e2e_context_memberships) == 0)' "${INFRA_MAIN}"
grep -Fq '!var.create_test_user' "${INFRA_MAIN}"
grep -Fq 'var.context_authorization_dogfood_principal_ref == ""' "${INFRA_MAIN}"
grep -Fq 'var.context_authorization_principal_claim == "preferred_username"' "${INFRA_MAIN}"

mkdir -p "${MOCK_BIN}" "${MOCK_STATE}"
printf '[]\n' >"${MOCK_STATE}/users.json"
cat >"${MOCK_STATE}/groups.json" <<'JSON'
[
  {"id":"global-members","name":"workspace-members","attributes":{}},
  {"id":"global-boards","name":"weave-board-editors","attributes":{}},
  {"id":"global-calendar","name":"weave-calendar-editors","attributes":{}}
]
JSON

grep -Fq 'global_group_id "${base}" "${token}" weave-board-editors' "${SCRIPT}" ||
  fail "disposable members must receive the existing Boards mutation capability"

prepare_output="$(bash "${SCRIPT}" prepare --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}")"
grep -Fq 'WEAVE_E2E_RUN_NAMESPACE=' <<<"${prepare_output}"
grep -Fq 'WEAVE_E2E_CREDENTIAL_ENV_PATH=' <<<"${prepare_output}"
grep -Fq 'WEAVE_E2E_STARTUP_ENV_PATH=' <<<"${prepare_output}"
grep -Fq 'WEAVE_E2E_IDENTITY_MANIFEST_PATH=' <<<"${prepare_output}"
grep -Fq 'WEAVE_E2E_CLEANUP_EVIDENCE_PATH=' <<<"${prepare_output}"
! grep -Fq 'CHAT_PROOF_TOKEN' <<<"${prepare_output}" || fail "prepare output must not publish proof credential paths"

eval "${prepare_output}"
export WEAVE_E2E_RUN_NAMESPACE WEAVE_E2E_CREDENTIAL_ENV_PATH WEAVE_E2E_STARTUP_ENV_PATH WEAVE_E2E_IDENTITY_MANIFEST_PATH WEAVE_E2E_CLEANUP_EVIDENCE_PATH
# shellcheck disable=SC1090
source "${WEAVE_E2E_CREDENTIAL_ENV_PATH}"
# shellcheck disable=SC1090
source "${WEAVE_E2E_STARTUP_ENV_PATH}"
: "${TF_VAR_isolated_e2e_context_memberships:?startup membership list is required}"

[[ "$(file_mode "${WEAVE_E2E_CREDENTIAL_ENV_PATH}")" == 600 ]] || fail "credential env must be mode 0600"
[[ "$(file_mode "${TF_VAR_chat_e2e_proof_token_host_path}")" == 600 ]] || fail "Chat provider proof credential must be mode 0600"
[[ "$(<"${TF_VAR_chat_e2e_proof_token_host_path}")" =~ ^[0-9a-f]{96}$ ]] || fail "Chat provider proof credential must be independently random 384-bit hex"
# Sourced from the generated startup environment above.
# shellcheck disable=SC2154
[[ "${TF_VAR_chat_e2e_proof_enabled}" == true ]] || fail "isolated startup must enable the private Chat proof boundary"
# shellcheck disable=SC2154
[[ "$(basename -- "${TF_VAR_chat_e2e_proof_token_host_path}")" == "chat-provider-proof.token" ]] || fail "proof credential path binding is inconsistent"
# shellcheck disable=SC2154
[[ "${TF_VAR_chat_e2e_proof_run_id}" == "${RUN_ID}" ]] || fail "proof run binding is not exact"
jq -e 'length == 3 and .[0].context_id == .[1].context_id and .[2].context_id != .[0].context_id and all(.[]; .source == "isolated-live-e2e")' \
  <<<"${TF_VAR_isolated_e2e_context_memberships}" >/dev/null
jq -e '.contextAuthorization.mode == "isolated-startup-real-rebac" and .contextAuthorization.persistentDogfoodEligible == false and (.actors | length == 3)' \
  "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}" >/dev/null
if grep -Fq "${WEAVE_E2E_AUTHOR_USERNAME}" "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}" ||
  grep -Fq "${WEAVE_E2E_AUTHOR_PASSWORD}" "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}"; then
  fail "support-safe identity manifest leaked an identity or credential"
fi

proof_token_before="$(<"${TF_VAR_chat_e2e_proof_token_host_path}")"
second_prepare="$(bash "${SCRIPT}" prepare --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}")"
[[ "${second_prepare}" == "${prepare_output}" ]] || fail "prepare must be idempotent for the same run ID"
[[ "$(<"${TF_VAR_chat_e2e_proof_token_host_path}")" == "${proof_token_before}" ]] || fail "prepare must preserve the run-scoped proof credential"

cat >"${MOCK_BIN}/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
method=GET
body=""
url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    --data) body="$2"; shift 2 ;;
    --data-urlencode|-H|--cacert|--connect-timeout|--max-time) shift 2 ;;
    --silent|--show-error|--fail|--fail-with-body) shift ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done

users="${MOCK_STATE}/users.json"
groups="${MOCK_STATE}/groups.json"
tmp="${MOCK_STATE}/tmp.json"

if [[ "${url}" == */realms/master/protocol/openid-connect/token ]]; then
  printf '{"access_token":"fixture-admin-token"}\n'
elif [[ "${method}:${url}" == GET:*/users\?* ]]; then
  jq '[.[] | {id,username}]' "${users}"
elif [[ "${method}:${url}" == GET:*/users/* ]]; then
  id="${url##*/}"
  jq -c --arg id "${id}" '.[] | select(.id == $id)' "${users}"
elif [[ "${method}:${url}" == POST:*/users ]]; then
  username="$(jq -r '.username' <<<"${body}")"
  jq --arg id "user-$(printf '%s' "${username}" | cksum | awk '{print $1}')" --argjson item "${body}" '. + [$item + {id:$id}]' "${users}" >"${tmp}"
  mv "${tmp}" "${users}"
elif [[ "${method}:${url}" == DELETE:*/users/* ]]; then
  id="${url##*/}"
  jq --arg id "${id}" '[.[] | select(.id != $id)]' "${users}" >"${tmp}"
  mv "${tmp}" "${users}"
elif [[ "${method}:${url}" == GET:*/groups\?* ]]; then
  jq '[.[] | {id,name}]' "${groups}"
elif [[ "${method}:${url}" == GET:*/groups/* ]]; then
  id="${url##*/}"
  jq -c --arg id "${id}" '.[] | select(.id == $id)' "${groups}"
elif [[ "${method}:${url}" == POST:*/groups ]]; then
  name="$(jq -r '.name' <<<"${body}")"
  jq --arg id "group-$(printf '%s' "${name}" | cksum | awk '{print $1}')" --argjson item "${body}" '. + [$item + {id:$id}]' "${groups}" >"${tmp}"
  mv "${tmp}" "${groups}"
elif [[ "${method}:${url}" == DELETE:*/groups/* ]]; then
  id="${url##*/}"
  jq --arg id "${id}" '[.[] | select(.id != $id)]' "${groups}" >"${tmp}"
  mv "${tmp}" "${groups}"
elif [[ "${method}:${url}" == GET:*/clients\?* ]]; then
  printf '[{"id":"weave-app-uuid","clientId":"weave-app"}]\n'
elif [[ "${method}:${url}" == GET:*/clients/weave-app-uuid/roles/member ]]; then
  printf '{"id":"member-role-id","name":"member"}\n'
elif [[ "${method}:${url}" == GET:*/organizations\?* ]]; then
  printf '[{"id":"org-id","name":"weave","alias":"weave"}]\n'
elif [[ "${method}:${url}" == GET:*/organizations/org-id/members/* ]]; then
  printf '{}\n'
else
  printf '{}\n'
fi
MOCK
chmod +x "${MOCK_BIN}/curl"

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == inspect ]]; then
  jq -cn \
    --arg namespace "WEAVE_ISOLATED_E2E_NAMESPACE=${MOCK_NAMESPACE}" \
    --arg author "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF=user:${MOCK_AUTHOR}" \
    --arg authorContext "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_CONTEXT_ID=workspace-default" \
    --arg authorSource "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_SOURCE=isolated-live-e2e" \
    --arg collaborator "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_PRINCIPAL_REF=user:${MOCK_COLLABORATOR}" \
    --arg collaboratorContext "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_CONTEXT_ID=workspace-default" \
    --arg collaboratorSource "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_SOURCE=isolated-live-e2e" \
    --arg outsider "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_PRINCIPAL_REF=user:${MOCK_OUTSIDER}" \
    --arg outsiderContext "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_CONTEXT_ID=${MOCK_OUTSIDE_CONTEXT}" \
    --arg outsiderSource "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_SOURCE=isolated-live-e2e" \
    '[$namespace,$author,$authorContext,$authorSource,$collaborator,$collaboratorContext,$collaboratorSource,$outsider,$outsiderContext,$outsiderSource]'
  exit 0
fi
exit 1
MOCK
chmod +x "${MOCK_BIN}/docker"

stack_bootstrap="${TMP_DIR}/stack-bootstrap.env"
cat >"${stack_bootstrap}" <<'ENV'
export TF_VAR_tenant_slug=weave
export TF_VAR_keycloak_host_port=48080
export TF_VAR_keycloak_admin_username=admin
export TF_VAR_keycloak_admin_password=fixture-admin-password
ENV

export MOCK_STATE
export MOCK_NAMESPACE="${WEAVE_E2E_RUN_NAMESPACE}"
export MOCK_AUTHOR="${WEAVE_E2E_AUTHOR_USERNAME}"
export MOCK_COLLABORATOR="${WEAVE_E2E_COLLABORATOR_USERNAME}"
export MOCK_OUTSIDER="${WEAVE_E2E_OUTSIDER_USERNAME}"
export MOCK_OUTSIDE_CONTEXT
MOCK_OUTSIDE_CONTEXT="$(jq -r '.[2].context_id' <<<"${TF_VAR_isolated_e2e_context_memberships}")"

if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=persistent-dogfood \
  bash "${SCRIPT}" provision --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}" --stack-bootstrap-env "${stack_bootstrap}" >/dev/null 2>&1; then
  fail "persistent dogfood scope must not provision disposable identities"
fi

for _pass in 1 2; do
  PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated \
    bash "${SCRIPT}" provision --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}" --stack-bootstrap-env "${stack_bootstrap}" >/dev/null
done
jq -e '.contextAuthorization.status == "active_runtime_verified" and .providerBindings.keycloak == "provisioned" and all(.actors[]; has("subjectSha256"))' \
  "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}" >/dev/null
[[ "$(jq 'length' "${MOCK_STATE}/users.json")" == 3 ]] || fail "provision should create exactly three run-scoped users"
jq -e --arg namespace "${WEAVE_E2E_RUN_NAMESPACE}" '
  all(.[];
    .firstName == "Weave E2E" and
    (.lastName | startswith($namespace + ":")) and
    .email == (.username + "@example.invalid")
  )
' "${MOCK_STATE}/users.json" >/dev/null || fail "provisioned users are missing their standard-field run markers"
[[ "$(jq '[.[] | select(.attributes.weave_e2e_namespace != null)] | length' "${MOCK_STATE}/groups.json")" == 2 ]] || fail "provision should create exactly two run-scoped groups"

cleanup_output="$(PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated \
  bash "${SCRIPT}" cleanup --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}" --stack-bootstrap-env "${stack_bootstrap}")"
grep -Fq 'MULTI_USER_CLEANUP_RESULT status=passed usersDeleted=3 groupsDeleted=2 persistentHumanChanged=false supportSafe=true' <<<"${cleanup_output}"
jq -e '.expectedResourcesAbsent == true and .persistentHumanIdentityChanged == false and .keycloak.usersDeleted == 3 and .keycloak.groupsDeleted == 2' \
  "${WEAVE_E2E_CLEANUP_EVIDENCE_PATH}" >/dev/null

repeat_cleanup="$(PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated \
  bash "${SCRIPT}" cleanup --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}" --stack-bootstrap-env "${stack_bootstrap}")"
grep -Fq 'usersDeleted=0 groupsDeleted=0 persistentHumanChanged=false supportSafe=true expectedResourcesAbsent=true' <<<"${repeat_cleanup}"
[[ "$(jq 'length' "${MOCK_STATE}/users.json")" == 0 ]] || fail "cleanup left run-scoped users"
[[ "$(jq '[.[] | select(.attributes.weave_e2e_namespace != null)] | length' "${MOCK_STATE}/groups.json")" == 0 ]] || fail "cleanup left run-scoped groups"

printf 'isolated E2E identity lifecycle tests passed\n'
