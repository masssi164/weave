#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
IDENTITY_SCRIPT="${ROOT_DIR}/isolated-e2e-identities.sh"
AUTHORIZATION_SCRIPT="${ROOT_DIR}/isolated-e2e-authorization-probes.sh"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
MOCK_STATE="${TMP_DIR}/state"
OUTPUT_ROOT="${TMP_DIR}/output"
RUN_ID="authorization-fixture-run"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }
sha256() { printf '%s' "$1" | shasum -a 256 | awk '{print $1}'; }

mkdir -p "${MOCK_BIN}" "${MOCK_STATE}"
prepare_output="$(bash "${IDENTITY_SCRIPT}" prepare --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}")"
for variable in \
  WEAVE_E2E_OUTPUT_ROOT \
  WEAVE_E2E_RUN_NAMESPACE \
  WEAVE_E2E_CREDENTIAL_ENV_PATH \
  WEAVE_E2E_STARTUP_ENV_PATH \
  WEAVE_E2E_IDENTITY_MANIFEST_PATH \
  WEAVE_E2E_CLEANUP_EVIDENCE_PATH \
  WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH \
  WEAVE_E2E_STACK_BOOTSTRAP_ENV; do
  grep -Fq "${variable}=" <<<"${prepare_output}" || fail "prepare did not export ${variable}"
done
eval "${prepare_output}"
export WEAVE_E2E_OUTPUT_ROOT WEAVE_E2E_RUN_NAMESPACE WEAVE_E2E_CREDENTIAL_ENV_PATH
export WEAVE_E2E_STARTUP_ENV_PATH WEAVE_E2E_IDENTITY_MANIFEST_PATH
export WEAVE_E2E_CLEANUP_EVIDENCE_PATH WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH
export WEAVE_E2E_STACK_BOOTSTRAP_ENV
# shellcheck disable=SC1090
source "${WEAVE_E2E_CREDENTIAL_ENV_PATH}"
# shellcheck disable=SC1090
source "${WEAVE_E2E_STARTUP_ENV_PATH}"

author_subject="subject-author-fixture"
collaborator_subject="subject-collaborator-fixture"
outsider_subject="subject-outsider-fixture"
persistent_subject="persistent-human-must-not-change"
cat >"${MOCK_STATE}/users.json" <<JSON
[
  {"id":"${author_subject}","username":"${WEAVE_E2E_AUTHOR_USERNAME}","email":"${WEAVE_E2E_AUTHOR_USERNAME}@example.invalid","enabled":true,"emailVerified":true,"firstName":"Weave E2E","lastName":"${WEAVE_E2E_RUN_NAMESPACE}:author"},
  {"id":"${collaborator_subject}","username":"${WEAVE_E2E_COLLABORATOR_USERNAME}","email":"${WEAVE_E2E_COLLABORATOR_USERNAME}@example.invalid","enabled":true,"emailVerified":true,"firstName":"Weave E2E","lastName":"${WEAVE_E2E_RUN_NAMESPACE}:collaborator"},
  {"id":"${outsider_subject}","username":"${WEAVE_E2E_OUTSIDER_USERNAME}","email":"${WEAVE_E2E_OUTSIDER_USERNAME}@example.invalid","enabled":true,"emailVerified":true,"firstName":"Weave E2E","lastName":"${WEAVE_E2E_RUN_NAMESPACE}:outsider"},
  {"id":"${persistent_subject}","username":"massimo","attributes":{}}
]
JSON
persistent_before="$(jq -c --arg id "${persistent_subject}" '.[] | select(.id == $id)' "${MOCK_STATE}/users.json" | shasum -a 256 | awk '{print $1}')"

jq \
  --arg authorHash "$(sha256 "${author_subject}")" \
  --arg collaboratorHash "$(sha256 "${collaborator_subject}")" \
  --arg outsiderHash "$(sha256 "${outsider_subject}")" '
    .contextAuthorization.status = "active_runtime_verified" |
    .providerBindings.keycloak = "provisioned" |
    (.actors[] | select(.role == "author") | .subjectSha256) = $authorHash |
    (.actors[] | select(.role == "collaborator") | .subjectSha256) = $collaboratorHash |
    (.actors[] | select(.role == "outsider") | .subjectSha256) = $outsiderHash
  ' "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}" >"${MOCK_STATE}/manifest.json"
mv "${MOCK_STATE}/manifest.json" "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}"

cat >"${MOCK_STATE}/realm.json" <<'JSON'
{"realm":"weave","enabled":true,"accessTokenLifespan":300}
JSON
cat >"${MOCK_STATE}/client.json" <<'JSON'
{"id":"weave-app-uuid","clientId":"weave-app","publicClient":true,"directAccessGrantsEnabled":false}
JSON
printf 'true\n' >"${MOCK_STATE}/collaborator-calendar-membership"
printf '0\n' >"${MOCK_STATE}/token-counter"
printf 'false\n' >"${MOCK_STATE}/revoked"
: >"${MOCK_STATE}/mutations.log"

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == inspect ]] || exit 1
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
MOCK
chmod +x "${MOCK_BIN}/docker"

cat >"${MOCK_BIN}/sleep" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf 'sleep:%s\n' "${1:-}" >>"${MOCK_STATE}/mutations.log"
MOCK
chmod +x "${MOCK_BIN}/sleep"

cat >"${MOCK_BIN}/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

method=GET
url=""
output=""
dump_header=""
write_out=""
body=""
authorization=""
username=""
fail_http=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -H)
      case "$2" in Authorization:\ Bearer\ *) authorization="${2#Authorization: Bearer }" ;; esac
      shift 2
      ;;
    --data) body="$2"; shift 2 ;;
    --data-binary) shift 2 ;;
    --data-urlencode)
      case "$2" in username=*) username="${2#username=}" ;; esac
      shift 2
      ;;
    --output|-o) output="$2"; shift 2 ;;
    --dump-header|-D) dump_header="$2"; shift 2 ;;
    --write-out|-w) write_out="$2"; shift 2 ;;
    --connect-timeout|--max-time|--cacert) shift 2 ;;
    --fail|--fail-with-body) fail_http=true; shift ;;
    --silent|--show-error) shift ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done

encode_segment() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

respond() {
  local status="$1" response_body="${2:-}" headers="${3:-}"
  if [[ -n "${dump_header}" ]]; then
    {
      printf 'HTTP/1.1 %s Fixture\r\n' "${status}"
      [[ -z "${headers}" ]] || printf '%b' "${headers}"
      printf '\r\n'
    } >"${dump_header}"
  fi
  if [[ -n "${output}" && "${output}" != /dev/null ]]; then
    printf '%s' "${response_body}" >"${output}"
  elif [[ -z "${output}" ]]; then
    printf '%s' "${response_body}"
  fi
  [[ -z "${write_out}" ]] || printf '%s' "${status}"
  if [[ "${fail_http}" == true && "${status}" -ge 400 ]]; then
    exit 22
  fi
}

if [[ "${url}" == */realms/master/protocol/openid-connect/token ]]; then
  respond 200 '{"access_token":"fixture-admin-token"}'
elif [[ "${url}" == */realms/weave/protocol/openid-connect/token ]]; then
  lifespan="$(jq -r '.accessTokenLifespan' "${MOCK_STATE}/realm.json")"
  if [[ "${MOCK_FAIL_SHORT_TOKEN:-false}" == true && "${lifespan}" == 2 ]]; then
    respond 500 '{"error":"fixture-short-token-failure"}'
    exit 0
  fi
  if [[ "${MOCK_FAIL_COLLABORATOR_TOKEN:-false}" == true && "${username}" == "${MOCK_COLLABORATOR}" ]]; then
    respond 500 '{"error":"fixture-collaborator-token-failure"}'
    exit 0
  fi
  count=$(( $(cat "${MOCK_STATE}/token-counter") + 1 ))
  printf '%s\n' "${count}" >"${MOCK_STATE}/token-counter"
  now="$(date +%s)"
  groups='["workspace-members","weave-calendar-editors"]'
  if [[ "${username}" == "${MOCK_COLLABORATOR}" && "$(cat "${MOCK_STATE}/collaborator-calendar-membership")" != true ]]; then
    groups='["workspace-members"]'
  fi
  payload="$(jq -cn \
    --arg username "${username}" \
    --arg subject "subject-${count}" \
    --arg scope 'openid profile email weave:workspace' \
    --argjson groups "${groups}" \
    --argjson iat "${now}" \
    --argjson exp "$((now + lifespan))" \
    --arg jti "fixture-${count}" \
    '{sub:$subject,preferred_username:$username,aud:["weave-app"],scope:$scope,groups:$groups,iat:$iat,exp:$exp,jti:$jti}')"
  token="$(printf '{"alg":"none","typ":"JWT"}' | encode_segment).$(printf '%s' "${payload}" | encode_segment).fixture"
  printf '%s' "${token}" >"${MOCK_STATE}/token-${count}"
  respond 200 "$(jq -cn --arg token "${token}" '{access_token:$token}')"
elif [[ "${url}" == */admin/realms/weave/users\?* ]]; then
  respond 200 "$(jq '[.[] | {id,username}]' "${MOCK_STATE}/users.json")"
elif [[ "${url}" == */admin/realms/weave/users/*/groups\?* && "${method}" == GET ]]; then
  if [[ "$(cat "${MOCK_STATE}/collaborator-calendar-membership")" == true ]]; then
    respond 200 '[{"id":"calendar-group","name":"weave-calendar-editors"}]'
  else
    respond 200 '[]'
  fi
elif [[ "${url}" =~ /admin/realms/weave/users/[^/]+$ && "${method}" == GET ]]; then
  id="${url##*/}"
  respond 200 "$(jq -c --arg id "${id}" '.[] | select(.id == $id)' "${MOCK_STATE}/users.json")"
elif [[ "${url}" == */admin/realms/weave/users/*/groups/calendar-group && "${method}" == DELETE ]]; then
  printf 'false\n' >"${MOCK_STATE}/collaborator-calendar-membership"
  printf 'group:false\n' >>"${MOCK_STATE}/mutations.log"
  respond 204
elif [[ "${url}" == */admin/realms/weave/users/*/groups/calendar-group && "${method}" == PUT ]]; then
  printf 'true\n' >"${MOCK_STATE}/collaborator-calendar-membership"
  printf 'group:true\n' >>"${MOCK_STATE}/mutations.log"
  respond 204
elif [[ "${url}" == */admin/realms/weave/groups\?* ]]; then
  respond 200 '[{"id":"calendar-group","name":"weave-calendar-editors"}]'
elif [[ "${url}" == */admin/realms/weave/clients\?* ]]; then
  respond 200 '[{"id":"weave-app-uuid","clientId":"weave-app"}]'
elif [[ "${url}" == */admin/realms/weave/clients/weave-app-uuid && "${method}" == GET ]]; then
  respond 200 "$(cat "${MOCK_STATE}/client.json")"
elif [[ "${url}" == */admin/realms/weave/clients/weave-app-uuid && "${method}" == PUT ]]; then
  printf '%s\n' "${body}" >"${MOCK_STATE}/client.json"
  printf 'client:%s\n' "$(jq -r '.directAccessGrantsEnabled' <<<"${body}")" >>"${MOCK_STATE}/mutations.log"
  respond 204
elif [[ "${url}" == */admin/realms/weave && "${method}" == GET ]]; then
  respond 200 "$(cat "${MOCK_STATE}/realm.json")"
elif [[ "${url}" == */admin/realms/weave && "${method}" == PUT ]]; then
  printf '%s\n' "${body}" >"${MOCK_STATE}/realm.json"
  printf 'realm:%s\n' "$(jq -r '.accessTokenLifespan' <<<"${body}")" >>"${MOCK_STATE}/mutations.log"
  respond 204
elif [[ "${url}" == */caldav/workspace/*-missing-capability.ics && "${method}" == PUT && "${authorization}" == "$(cat "${MOCK_STATE}/token-2" 2>/dev/null || true)" ]]; then
  respond 403 '<error code="capability-policy-blocked">Action blocked.</error>' 'X-Weave-Error-Code: capability-policy-blocked\r\n'
elif [[ "${authorization}" == "$(cat "${MOCK_STATE}/token-3" 2>/dev/null || true)" ]]; then
  respond 401 '{"code":"unauthorized","supportSafe":true}'
elif [[ "${url}" == */_matrix/client/v3/logout && "${method}" == POST && "${authorization}" == "$(cat "${MOCK_STATE}/token-1" 2>/dev/null || true)" ]]; then
  printf 'true\n' >"${MOCK_STATE}/revoked"
  respond 200 '{}'
elif [[ "${url}" == */_matrix/client/v3/account/whoami && "${authorization}" == "$(cat "${MOCK_STATE}/token-1" 2>/dev/null || true)" && "$(cat "${MOCK_STATE}/revoked")" == true ]]; then
  respond 401 '{"errcode":"M_UNKNOWN_TOKEN","supportSafe":true}'
elif [[ "${url}" == */caldav/workspace/*-missing-capability.ics && "${method}" == DELETE ]]; then
  respond 204
else
  respond 500 '{"error":"unexpected-fixture-request"}'
fi
MOCK
chmod +x "${MOCK_BIN}/curl"

stack_bootstrap="${TMP_DIR}/stack-bootstrap.env"
cat >"${stack_bootstrap}" <<'ENV'
export TF_VAR_tenant_slug=weave
export TF_VAR_keycloak_host_port=48080
export TF_VAR_backend_host_port=48081
export TF_VAR_keycloak_admin_username=admin
export TF_VAR_keycloak_admin_password=fixture-admin-password
ENV

export MOCK_STATE
export MOCK_NAMESPACE="${WEAVE_E2E_RUN_NAMESPACE}"
export MOCK_AUTHOR="${WEAVE_E2E_AUTHOR_USERNAME}"
export MOCK_COLLABORATOR="${WEAVE_E2E_COLLABORATOR_USERNAME}"
export MOCK_OUTSIDER="${WEAVE_E2E_OUTSIDER_USERNAME}"
export MOCK_OUTSIDE_CONTEXT
# Populated by the sourced prepared startup env.
# shellcheck disable=SC2154
MOCK_OUTSIDE_CONTEXT="$(jq -r '.[2].context_id' <<<"${TF_VAR_isolated_e2e_context_memberships}")"

common_args=(
  --run-id "${RUN_ID}"
  --output-root "${OUTPUT_ROOT}"
  --credentials-env "${WEAVE_E2E_CREDENTIAL_ENV_PATH}"
  --startup-env "${WEAVE_E2E_STARTUP_ENV_PATH}"
  --identity-manifest "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}"
  --stack-bootstrap-env "${stack_bootstrap}"
)

if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=persistent-dogfood \
  bash "${AUTHORIZATION_SCRIPT}" "${common_args[@]}" >/dev/null 2>&1; then
  fail "persistent dogfood scope must not run isolated authorization probes"
fi
[[ ! -s "${MOCK_STATE}/mutations.log" ]] || fail "persistent scope rejection mutated Keycloak state"

authorization_output="$(PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated \
  bash "${AUTHORIZATION_SCRIPT}" "${common_args[@]}")"
expected_marker='MULTI_USER_AUTHORIZATION_FIXTURES_RESULT status=passed missingCapabilityStatus=403 expiredChatStatus=401 expiredFilesStatus=401 expiredCalendarStatus=401 matrixLogoutStatus=200 revokedChatStatus=401 persistentHumanChanged=false supportSafe=true'
grep -Fqx "${expected_marker}" <<<"${authorization_output}"

evidence="${WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH}"
jq -e \
  --arg namespaceHash "$(sha256 "${WEAVE_E2E_RUN_NAMESPACE}")" \
  --arg authorSubjectHash "$(sha256 "${author_subject}")" \
  --arg collaboratorSubjectHash "$(sha256 "${collaborator_subject}")" '
    .schemaVersion == "weave.isolated-e2e-authorization.v1" and
    .namespaceSha256 == $namespaceHash and
    .isolatedRuntimeVerified == true and
    .markerOwnedIdentitiesVerified == true and
    .missingCapability.actorSha256 == $collaboratorSubjectHash and
    .missingCapability.calendarWriteStatus == 403 and
    .missingCapability.groupRestored == true and
    .expiredToken.chatStatus == 401 and
    .expiredToken.filesStatus == 401 and
    .expiredToken.calendarStatus == 401 and
    .revokedSession.actorSha256 == $authorSubjectHash and
    .revokedSession.matrixLogoutStatus == 200 and
    .revokedSession.chatReuseStatus == 401 and
    all(.restoration[]; . == true) and
    .persistentHumanChanged == false and
    .rawIdentityIncluded == false and
    .rawTokenIncluded == false and
    .rawProviderPayloadIncluded == false and
    .supportSafe == true
  ' "${evidence}" >/dev/null

if grep -Fq "${WEAVE_E2E_AUTHOR_USERNAME}" "${evidence}" ||
  grep -Fq "${WEAVE_E2E_COLLABORATOR_USERNAME}" "${evidence}" ||
  grep -Fq "${WEAVE_E2E_AUTHOR_PASSWORD}" "${evidence}" ||
  grep -Fq 'fixture-admin-token' "${evidence}"; then
  fail "authorization evidence leaked a raw identity or token"
fi
[[ "$(cat "${MOCK_STATE}/collaborator-calendar-membership")" == true ]] || fail "calendar membership was not restored"
[[ "$(jq -r '.accessTokenLifespan' "${MOCK_STATE}/realm.json")" == 300 ]] || fail "realm lifespan was not restored"
[[ "$(jq -r '.directAccessGrantsEnabled' "${MOCK_STATE}/client.json")" == false ]] || fail "client direct grants were not restored"
realm_restore_line="$(grep -n '^realm:300$' "${MOCK_STATE}/mutations.log" | tail -1 | cut -d: -f1)"
sleep_line="$(grep -n '^sleep:' "${MOCK_STATE}/mutations.log" | tail -1 | cut -d: -f1)"
((realm_restore_line < sleep_line)) || fail "realm lifespan was not restored before waiting for token expiry"
grep -Fqx 'sleep:67' "${MOCK_STATE}/mutations.log" || fail "expiry probe did not exceed the Resource Server clock-skew window"

persistent_after="$(jq -c --arg id "${persistent_subject}" '.[] | select(.id == $id)' "${MOCK_STATE}/users.json" | shasum -a 256 | awk '{print $1}')"
[[ "${persistent_before}" == "${persistent_after}" ]] || fail "persistent identity fixture changed"

# A failure while the collaborator group is absent must restore both the group
# and temporary direct-grant client setting through the EXIT trap.
printf 'true\n' >"${MOCK_STATE}/collaborator-calendar-membership"
printf '0\n' >"${MOCK_STATE}/token-counter"
printf 'false\n' >"${MOCK_STATE}/revoked"
jq '.accessTokenLifespan = 300' "${MOCK_STATE}/realm.json" >"${MOCK_STATE}/tmp.json"
mv "${MOCK_STATE}/tmp.json" "${MOCK_STATE}/realm.json"
jq '.directAccessGrantsEnabled = false' "${MOCK_STATE}/client.json" >"${MOCK_STATE}/tmp.json"
mv "${MOCK_STATE}/tmp.json" "${MOCK_STATE}/client.json"
group_failure_evidence="${OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/group-failure-authorization-evidence.json"
rm -f "${group_failure_evidence}"
if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated MOCK_FAIL_COLLABORATOR_TOKEN=true \
  bash "${AUTHORIZATION_SCRIPT}" "${common_args[@]}" --authorization-evidence "${group_failure_evidence}" >/dev/null 2>&1; then
  fail "collaborator-token mint failure fixture unexpectedly passed"
fi
[[ ! -e "${group_failure_evidence}" ]] || fail "failed group-restoration run wrote passing evidence"
[[ "$(cat "${MOCK_STATE}/collaborator-calendar-membership")" == true ]] || fail "failure trap did not restore group membership"
[[ "$(jq -r '.directAccessGrantsEnabled' "${MOCK_STATE}/client.json")" == false ]] || fail "failure trap did not restore direct grants"

# A failure while the bounded realm lifetime is active must still restore both
# the realm and temporary direct-grant client setting through the EXIT trap.
printf 'true\n' >"${MOCK_STATE}/collaborator-calendar-membership"
printf '0\n' >"${MOCK_STATE}/token-counter"
printf 'false\n' >"${MOCK_STATE}/revoked"
jq '.accessTokenLifespan = 300' "${MOCK_STATE}/realm.json" >"${MOCK_STATE}/tmp.json"
mv "${MOCK_STATE}/tmp.json" "${MOCK_STATE}/realm.json"
jq '.directAccessGrantsEnabled = false' "${MOCK_STATE}/client.json" >"${MOCK_STATE}/tmp.json"
mv "${MOCK_STATE}/tmp.json" "${MOCK_STATE}/client.json"
failure_evidence="${OUTPUT_ROOT}/${WEAVE_E2E_RUN_NAMESPACE}/failure-authorization-evidence.json"
rm -f "${failure_evidence}"
if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated MOCK_FAIL_SHORT_TOKEN=true \
  bash "${AUTHORIZATION_SCRIPT}" "${common_args[@]}" --authorization-evidence "${failure_evidence}" >/dev/null 2>&1; then
  fail "short-token mint failure fixture unexpectedly passed"
fi
[[ ! -e "${failure_evidence}" ]] || fail "failed authorization run wrote passing evidence"
[[ "$(cat "${MOCK_STATE}/collaborator-calendar-membership")" == true ]] || fail "failure trap did not restore group membership"
[[ "$(jq -r '.accessTokenLifespan' "${MOCK_STATE}/realm.json")" == 300 ]] || fail "failure trap did not restore realm lifespan"
[[ "$(jq -r '.directAccessGrantsEnabled' "${MOCK_STATE}/client.json")" == false ]] || fail "failure trap did not restore direct grants"

printf 'isolated E2E authorization probe tests passed\n'
