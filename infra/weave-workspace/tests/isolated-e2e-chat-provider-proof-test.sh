#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
IDENTITY_SCRIPT="${ROOT_DIR}/isolated-e2e-identities.sh"
PROOF_SCRIPT="${ROOT_DIR}/isolated-e2e-chat-provider-proof.sh"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
MOCK_STATE="${TMP_DIR}/state"
OUTPUT_ROOT="${TMP_DIR}/output"
RUN_ID="matrix-provider-proof-fixture"
CANDIDATE="0123456789abcdef0123456789abcdef01234567"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }
sha256() { printf '%s' "$1" | shasum -a 256 | awk '{print $1}'; }
assert_contains() { grep -Fq -- "$2" "$1" || fail "missing expected contract '$2'"; }

mkdir -p "${MOCK_BIN}" "${MOCK_STATE}"
prepare_output="$(
  WEAVE_E2E_STACK_SCOPE=isolated \
    WEAVE_CANDIDATE_COMMIT="${CANDIDATE}" \
    WEAVE_CANDIDATE_EVIDENCE_REF=https://github.example.invalid/weave/actions/runs/85 \
    bash "${IDENTITY_SCRIPT}" prepare --run-id "${RUN_ID}" --output-root "${OUTPUT_ROOT}"
)"
eval "${prepare_output}"
export WEAVE_E2E_OUTPUT_ROOT WEAVE_E2E_RUN_NAMESPACE WEAVE_E2E_CREDENTIAL_ENV_PATH
export WEAVE_E2E_STARTUP_ENV_PATH WEAVE_E2E_IDENTITY_MANIFEST_PATH
export WEAVE_E2E_STACK_BOOTSTRAP_ENV
# shellcheck disable=SC1090
source "${WEAVE_E2E_CREDENTIAL_ENV_PATH}"
# shellcheck disable=SC1090
source "${WEAVE_E2E_STARTUP_ENV_PATH}"
BACKEND_CONTAINER="${WEAVE_E2E_RUN_NAMESPACE}-backend"
SYNAPSE_CONTAINER="${WEAVE_E2E_RUN_NAMESPACE}-synapse"

author_subject="subject-author-provider-proof"
collaborator_subject="subject-collaborator-provider-proof"
outsider_subject="subject-outsider-provider-proof"
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

printf '%s\n' '{"id":"weave-app-uuid","clientId":"weave-app","publicClient":true,"directAccessGrantsEnabled":false}' \
  >"${MOCK_STATE}/client.json"
printf 'true\n' >"${MOCK_STATE}/${BACKEND_CONTAINER}-running"
printf 'true\n' >"${MOCK_STATE}/${SYNAPSE_CONTAINER}-running"
printf '0\n' >"${MOCK_STATE}/synapse-health-failures"
printf '0\n' >"${MOCK_STATE}/token-counter"
printf '20\n' >"${MOCK_STATE}/callback-count"
printf '0\n' >"${MOCK_STATE}/callback-duplicate-count"
printf 'false\n' >"${MOCK_STATE}/callback-replay-seen"
printf '0\n' >"${MOCK_STATE}/callback-readiness-checks"
: >"${MOCK_STATE}/tokens.tsv"
: >"${MOCK_STATE}/operations.log"
for pass_index in 1 2; do
  printf '[]\n' >"${MOCK_STATE}/events-${pass_index}.json"
  printf '{}\n' >"${MOCK_STATE}/transactions-${pass_index}.json"
  printf 'false\n' >"${MOCK_STATE}/failed-${pass_index}"
  printf 'false\n' >"${MOCK_STATE}/left-${pass_index}-author"
  printf 'false\n' >"${MOCK_STATE}/left-${pass_index}-collaborator"
done

cat >"${MOCK_BIN}/sleep" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
exit 0
MOCK
chmod +x "${MOCK_BIN}/sleep"

cat >"${MOCK_BIN}/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

method=GET
url=""
output=""
write_out=""
body_file=""
inline_body=""
authorization=""
config=""
username=""
fail_http=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    -X|--request) method="$2"; shift 2 ;;
    --output|-o) output="$2"; shift 2 ;;
    --write-out|-w) write_out="$2"; shift 2 ;;
    --config|-K) config="$2"; shift 2 ;;
    --header|-H)
      header="$2"
      if [[ "${header}" == @* ]]; then
        header_file="${header#@}"
        authorization="$(sed -n 's/^Authorization: Bearer //p' "${header_file}")"
      elif [[ "${header}" == Authorization:\ Bearer\ * ]]; then
        authorization="${header#Authorization: Bearer }"
      fi
      shift 2
      ;;
    --data-binary)
      [[ "$2" == @* ]] && body_file="${2#@}" || inline_body="$2"
      shift 2
      ;;
    --data) inline_body="$2"; shift 2 ;;
    --data-urlencode)
      [[ "$2" == username=* ]] && username="${2#username=}"
      shift 2
      ;;
    --connect-timeout|--max-time|--cacert) shift 2 ;;
    --fail|--fail-with-body) fail_http=true; shift ;;
    --silent|--show-error) shift ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done

if [[ -n "${config}" ]]; then
  url="$(sed -n 's/^url = "\(.*\)"$/\1/p' "${config}")"
  method="$(sed -n 's/^request = "\(.*\)"$/\1/p' "${config}")"
  username="$(sed -n 's/^data-urlencode = "username=\(.*\)"$/\1/p' "${config}")"
fi

respond() {
  local status="$1" response_body="${2:-}"
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

base64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

role_and_pass() {
  awk -F '\t' -v token="${authorization}" '$1 == token {print $2 ":" $3; exit}' "${MOCK_STATE}/tokens.tsv"
}

pass_from_url() {
  case "${url}" in
    *room-pass-1*) printf '1' ;;
    *room-pass-2*) printf '2' ;;
    *) printf '0' ;;
  esac
}

if [[ "${url}" == */realms/master/protocol/openid-connect/token ]]; then
  respond 200 '{"access_token":"fixture-admin-token"}'
elif [[ "${url}" == */realms/weave/protocol/openid-connect/token ]]; then
  count=$(( $(<"${MOCK_STATE}/token-counter") + 1 ))
  printf '%s\n' "${count}" >"${MOCK_STATE}/token-counter"
  if ((count <= 3)); then pass_index=1; else pass_index=2; fi
  case "${username}" in
    "${MOCK_AUTHOR}") role=author; subject="${MOCK_AUTHOR_SUBJECT}" ;;
    "${MOCK_COLLABORATOR}") role=collaborator; subject="${MOCK_COLLABORATOR_SUBJECT}" ;;
    "${MOCK_OUTSIDER}") role=outsider; subject="${MOCK_OUTSIDER_SUBJECT}" ;;
    *) respond 400 '{"error":"invalid_grant"}'; exit 0 ;;
  esac
  now="$(date +%s)"
  payload="$(jq -cn \
    --arg sub "${subject}" \
    --arg username "${username}" \
    --arg issuer 'http://127.0.0.1:48080/realms/weave' \
    --arg tenant "${MOCK_TENANT}" \
    --argjson iat "${now}" \
    --argjson exp "$((now + 900))" '
      {
        sub:$sub,iss:$issuer,preferred_username:$username,weave_tenant_id:$tenant,
        aud:["weave-app"],scope:"openid profile email weave:workspace",
        groups:["workspace-members"],iat:$iat,exp:$exp
      }
    ')"
  token="$(printf '{"alg":"none","typ":"JWT"}' | base64url).$(printf '%s' "${payload}" | base64url).fixture${count}"
  printf '%s\t%s\t%s\n' "${token}" "${role}" "${pass_index}" >>"${MOCK_STATE}/tokens.tsv"
  respond 200 "$(jq -cn --arg token "${token}" '{access_token:$token}')"
elif [[ "${url}" == */admin/realms/weave/clients\?clientId=weave-app ]]; then
  respond 200 '[{"id":"weave-app-uuid","clientId":"weave-app"}]'
elif [[ "${url}" == */admin/realms/weave/clients/weave-app-uuid && "${method}" == GET ]]; then
  respond 200 "$(<"${MOCK_STATE}/client.json")"
elif [[ "${url}" == */admin/realms/weave/clients/weave-app-uuid && "${method}" == PUT ]]; then
  printf '%s\n' "${inline_body}" >"${MOCK_STATE}/client.json"
  printf 'keycloak-client:%s\n' "$(jq -r '.directAccessGrantsEnabled' <<<"${inline_body}")" >>"${MOCK_STATE}/operations.log"
  respond 204
elif [[ "${url}" == */api/health/live ]]; then
  respond 200 '{"status":"UP"}'
elif [[ "${url}" == http://127.0.0.1:*/health ]]; then
  if [[ "$(<"${MOCK_STATE}/${MOCK_SYNAPSE_CONTAINER}-running")" != true ]]; then
    respond 503 'NOT_READY'
  elif (( $(<"${MOCK_STATE}/synapse-health-failures") > 0 )); then
    printf '%s\n' "$(( $(<"${MOCK_STATE}/synapse-health-failures") - 1 ))" \
      >"${MOCK_STATE}/synapse-health-failures"
    printf 'synapse-health:503\n' >>"${MOCK_STATE}/operations.log"
    respond 503 'NOT_READY'
  else
    printf 'synapse-health:200\n' >>"${MOCK_STATE}/operations.log"
    respond 200 'OK'
  fi
elif [[ "${url}" == */api/platform/config ]]; then
  if [[ "${MOCK_FAIL_AFTER_STOP:-false}" == true && "$(<"${MOCK_STATE}/${MOCK_SYNAPSE_CONTAINER}-running")" == false ]]; then
    respond 500 '{"code":"fixture-failure"}'
  else
    respond 200 '{"apiBaseUrl":"https://api.weave.test/api","supportSafe":true}'
  fi
elif [[ "${url}" == */api/me ]]; then
  respond 200 '{"authenticated":true,"supportSafe":true}'
elif [[ "${url}" == */_matrix/client/v3/account/whoami ]]; then
  role_pass="$(role_and_pass)"
  role="${role_pass%%:*}"
  pass_index="${role_pass##*:}"
  [[ -n "${role}" && "${pass_index}" != "${role_pass}" ]] || { respond 401 '{"errcode":"M_MISSING_TOKEN"}'; exit 0; }
  printf 'register:%s:%s\n' "${role}" "${pass_index}" >>"${MOCK_STATE}/operations.log"
  respond 200 "$(jq -cn --arg user "@${role}_${pass_index}:api.weave.test" '{user_id:$user,device_id:"fixture"}')"
elif [[ "${url}" == */_matrix/client/v3/createRoom ]]; then
  pass_index="$(jq -r '.name | capture("pass-(?<value>[12])").value' "${body_file}")"
  role_pass="$(role_and_pass)"
  role="${role_pass%%:*}"
  expected_collaborator="@collaborator_${pass_index}:api.weave.test"
  if [[ "${role}" != author ]] ||
    ! jq -e --arg expected "${expected_collaborator}" '.invite == [$expected]' "${body_file}" >/dev/null; then
    respond 400 '{"errcode":"M_BAD_JSON","error":"fixture invite mismatch"}'
    exit 0
  fi
  printf 'create:target:%s\n' "${pass_index}" >>"${MOCK_STATE}/operations.log"
  respond 200 "$(jq -cn --arg room "!room-pass-${pass_index}:api.weave.test" '{room_id:$room}')"
elif [[ "${url}" == */_matrix/client/v3/join/* ]]; then
  respond 200 '{}'
elif [[ "${url}" == */send/m.room.encrypted/* ]]; then
  role_pass="$(role_and_pass)"
  role="${role_pass%%:*}"
  pass_index="$(pass_from_url)"
  if [[ "${role}" == outsider || \
    ( -f "${MOCK_STATE}/left-${pass_index}-${role}" && "$(<"${MOCK_STATE}/left-${pass_index}-${role}")" == true ) ]]; then
    respond 403 '{"errcode":"M_FORBIDDEN","error":"Membership is required."}'
    exit 0
  fi
  if [[ "$(<"${MOCK_STATE}/${MOCK_SYNAPSE_CONTAINER}-running")" != true ]]; then
    printf 'true\n' >"${MOCK_STATE}/failed-${pass_index}"
    respond 503 '{"errcode":"M_UNAVAILABLE","error":"Weave Chat is temporarily unavailable."}'
    exit 0
  fi
  transaction="${url##*/}"
  existing="$(jq -r --arg transaction "${transaction}" '.[$transaction] // empty' "${MOCK_STATE}/transactions-${pass_index}.json")"
  if [[ -n "${existing}" ]]; then
    event_id="${existing}"
  else
    case "${transaction}" in
      *-author) event_index=1 ;;
      *-collaborator) event_index=2 ;;
      *-outage) event_index=3 ;;
      *) event_index=9 ;;
    esac
    event_id="\$event-pass-${pass_index}-${event_index}:api.weave.test"
    jq --arg transaction "${transaction}" --arg event "${event_id}" '.[$transaction]=$event' \
      "${MOCK_STATE}/transactions-${pass_index}.json" >"${MOCK_STATE}/transactions-${pass_index}.tmp"
    mv "${MOCK_STATE}/transactions-${pass_index}.tmp" "${MOCK_STATE}/transactions-${pass_index}.json"
    jq --arg event "${event_id}" --slurpfile content "${body_file}" \
      '. + [{event_id:$event,type:"m.room.encrypted",content:$content[0]}]' \
      "${MOCK_STATE}/events-${pass_index}.json" >"${MOCK_STATE}/events-${pass_index}.tmp"
    mv "${MOCK_STATE}/events-${pass_index}.tmp" "${MOCK_STATE}/events-${pass_index}.json"
  fi
  [[ "${transaction}" != *-outage ]] || printf 'false\n' >"${MOCK_STATE}/failed-${pass_index}"
  respond 200 "$(jq -cn --arg event "${event_id}" '{event_id:$event}')"
elif [[ "${url}" == */messages\?dir=b\&limit=* ]]; then
  role_pass="$(role_and_pass)"
  role="${role_pass%%:*}"
  pass_index="$(pass_from_url)"
  if [[ "${role}" == outsider || \
    ( -f "${MOCK_STATE}/left-${pass_index}-${role}" && "$(<"${MOCK_STATE}/left-${pass_index}-${role}")" == true ) ]]; then
    respond 403 '{"errcode":"M_FORBIDDEN","error":"Membership is required."}'
  elif [[ "$(<"${MOCK_STATE}/${MOCK_SYNAPSE_CONTAINER}-running")" != true ]]; then
    respond 503 '{"errcode":"M_UNAVAILABLE","error":"Weave Chat is temporarily unavailable."}'
  else
    respond 200 "$(jq -c '{chunk:.,start:"fixture",end:"fixture"}' "${MOCK_STATE}/events-${pass_index}.json")"
  fi
elif [[ "${url}" == */typing/* ]]; then
  if [[ "$(<"${MOCK_STATE}/${MOCK_SYNAPSE_CONTAINER}-running")" == true ]]; then
    respond 200 '{}'
  else
    respond 503 '{"errcode":"M_UNAVAILABLE","error":"Weave Chat is temporarily unavailable."}'
  fi
elif [[ "${url}" == */api/internal/e2e/chat/provider-proof/callback-replay/readiness ]]; then
  [[ "${method}" == GET && "${authorization}" == "${MOCK_PROOF_TOKEN}" ]] || {
    respond 401 '{"code":"chat-e2e-proof-unauthorized","supportSafe":true}'
    exit 0
  }
  readiness_checks="$(( $(<"${MOCK_STATE}/callback-readiness-checks") + 1 ))"
  printf '%s\n' "${readiness_checks}" >"${MOCK_STATE}/callback-readiness-checks"
  if (( readiness_checks < 3 )); then
    respond 200 '{"contractVersion":"chat-provider-callback-replay-readiness-v1","callbackReplayReady":false,"code":"chat-provider-callback-not-captured","supportSafe":true}'
  else
    respond 200 '{"contractVersion":"chat-provider-callback-replay-readiness-v1","callbackReplayReady":true,"code":"chat-provider-callback-captured","supportSafe":true}'
  fi
elif [[ "${url}" == */api/internal/e2e/chat/provider-proof/callback-replay ]]; then
  [[ "${method}" == POST && "${authorization}" == "${MOCK_PROOF_TOKEN}" ]] || {
    respond 401 '{"code":"chat-e2e-proof-unauthorized","supportSafe":true}'
    exit 0
  }
  [[ "$(jq -r '.runId' "${body_file}")" == "${MOCK_RUN_ID}" ]] || {
    respond 403 '{"code":"chat-e2e-proof-run-mismatch","supportSafe":true}'
    exit 0
  }
  [[ "$(<"${MOCK_STATE}/callback-replay-seen")" == false ]] || {
    respond 409 '{"code":"fixture-callback-replayed-more-than-once","supportSafe":true}'
    exit 0
  }
  printf 'true\n' >"${MOCK_STATE}/callback-replay-seen"
  printf '%s\n' "$(( $(<"${MOCK_STATE}/callback-duplicate-count") + 1 ))" \
    >"${MOCK_STATE}/callback-duplicate-count"
  printf 'proof:callback-replay\n' >>"${MOCK_STATE}/operations.log"
  respond 200 "$(jq -cn \
    --arg correlation "$(printf 'captured-callback' | shasum -a 256 | awk '{print $1}')" '
      {
        contractVersion:"chat-provider-callback-replay-v1",
        callbackCorrelationHash:$correlation,
        replayed:true,
        supportSafe:true
      }
    ')"
elif [[ "${url}" == */api/internal/e2e/chat/provider-proof ]]; then
  [[ "${method}" == POST && "${authorization}" == "${MOCK_PROOF_TOKEN}" ]] || {
    respond 401 '{"code":"chat-e2e-proof-unauthorized","supportSafe":true}'
    exit 0
  }
  [[ "$(jq -r '.runId' "${body_file}")" == "${MOCK_RUN_ID}" ]] || {
    respond 403 '{"code":"chat-e2e-proof-run-mismatch","supportSafe":true}'
    exit 0
  }
  jq -e '
    ((.eventCorrelationSha256 | length) == 2 or (.eventCorrelationSha256 | length) == 3) and
    all(.eventCorrelationSha256[]; test("^[0-9a-f]{64}$"))
  ' "${body_file}" >/dev/null || {
    respond 400 '{"code":"chat-e2e-proof-correlation-invalid","supportSafe":true}'
    exit 0
  }
  pass_index="$(jq -r '.conversationId | capture("room-pass-(?<value>[12])").value' "${body_file}")"
  event_count="$(jq 'length' "${MOCK_STATE}/events-${pass_index}.json")"
  requested_correlation_count="$(jq '.eventCorrelationSha256 | length' "${body_file}")"
  printf 'proof:correlations:%s:%s:%s\n' \
    "${pass_index}" "${requested_correlation_count}" "${event_count}" >>"${MOCK_STATE}/operations.log"
  if [[ "${requested_correlation_count}" != "${event_count}" ]]; then
    respond 400 '{"code":"chat-e2e-proof-correlation-count-mismatch","supportSafe":true}'
    exit 0
  fi
  failed_count=0
  [[ "$(<"${MOCK_STATE}/failed-${pass_index}")" != true ]] || failed_count=1
  total_events=$(( $(jq 'length' "${MOCK_STATE}/events-1.json") + $(jq 'length' "${MOCK_STATE}/events-2.json") ))
  response_body="$(jq -n \
    --arg correlation "$(printf 'provider-pass-%s' "${pass_index}" | shasum -a 256 | awk '{print $1}')" \
    --arg runIdHash "$(printf '%s' "${MOCK_RUN_ID}" | shasum -a 256 | awk '{print $1}')" \
    --argjson eventCount "${event_count}" \
    --argjson failedCount "${failed_count}" \
    --argjson callbackCount "$(<"${MOCK_STATE}/callback-count")" \
    --argjson duplicateCount "$(<"${MOCK_STATE}/callback-duplicate-count")" \
    --argjson ledgerCount "$((100 + total_events))" '
      {
        contractVersion:"chat-provider-proof-v1",
        correlationHash:$correlation,
        runIdHash:$runIdHash,
        adapterConfigured:true,
        canonicalStorage:"durable-relational-flyway",
        providerCapabilityState:"available",
        providerCapabilityAvailable:true,
        providerCapabilityCode:"chat-provider-authenticated-operation-ready",
        providerObservationAgeSeconds:1,
        providerConsecutiveFailures:0,
        providerBackoffUntil:null,
        identities:[
          {role:"author",identityHash:("a"*64),providerMapped:true,canonicalJoined:true,providerJoined:true,providerReadDenied:false},
          {role:"collaborator",identityHash:("b"*64),providerMapped:true,canonicalJoined:true,providerJoined:true,providerReadDenied:false},
          {role:"outsider",identityHash:("c"*64),providerMapped:false,canonicalJoined:false,providerJoined:false,providerReadDenied:true}
        ],
        providerMembershipExact:true,
        outsiderAbsent:true,
        outsiderReadDenied:true,
        providerEncryptionStateVerified:true,
        providerEventMappingExact:true,
        providerCiphertextCorrelationExact:true,
        canonicalConversationCount:1,
        canonicalJoinedMemberCount:2,
        canonicalCommittedEventCount:$eventCount,
        canonicalEncryptedEventCount:$eventCount,
        canonicalPlaintextEventCount:0,
        providerEncryptedEventCount:$eventCount,
        providerPlaintextEventCount:0,
        pendingOperationCount:0,
        failedOperationCount:$failedCount,
        committedOperationCount:(3 + $eventCount),
        bridgeLedgerCount:$ledgerCount,
        callbackTransactionCount:$callbackCount,
        callbackDuplicateCount:$duplicateCount,
        callbackSemanticMismatchCount:0,
        quarantineCount:0,
        degradedOperationCount:0,
        observedAt:"2026-07-15T12:00:00Z",
        supportSafe:true
      }
    ')"
  printf 'proof:evidence:%s\n' "${pass_index}" >>"${MOCK_STATE}/operations.log"
  respond 200 "${response_body}"
elif [[ "${url}" == */redact/* ]]; then
  pass_index="$(pass_from_url)"
  case "${url}" in
    *event-pass-${pass_index}-1*) event_index=1 ;;
    *event-pass-${pass_index}-2*) event_index=2 ;;
    *event-pass-${pass_index}-3*) event_index=3 ;;
    *) event_index=0 ;;
  esac
  respond 200 "$(jq -cn --arg event "\$redaction-pass-${pass_index}-${event_index}:api.weave.test" '{event_id:$event}')"
elif [[ "${url}" == */leave ]]; then
  role_pass="$(role_and_pass)"
  role="${role_pass%%:*}"
  pass_index="$(pass_from_url)"
  printf 'true\n' >"${MOCK_STATE}/left-${pass_index}-${role}"
  respond 200 '{}'
else
  respond 500 '{"code":"unexpected-fixture-request"}'
fi
MOCK
chmod +x "${MOCK_BIN}/curl"

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

command="${1:-}"
shift || true
case "${command}" in
  inspect)
    format=""
    if [[ "${1:-}" == --format ]]; then format="$2"; shift 2; fi
    container="${1:-}"
    if [[ "${format}" == '{{json .Config.Env}}' ]]; then
      jq -cn \
        --arg namespace "WEAVE_ISOLATED_E2E_NAMESPACE=${MOCK_NAMESPACE}" \
        --arg author "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF=user:${MOCK_AUTHOR}" \
        --arg authorContext 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_CONTEXT_ID=workspace-default' \
        --arg authorSource 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_SOURCE=isolated-live-e2e' \
        --arg collaborator "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_PRINCIPAL_REF=user:${MOCK_COLLABORATOR}" \
        --arg collaboratorContext 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_CONTEXT_ID=workspace-default' \
        --arg collaboratorSource 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_SOURCE=isolated-live-e2e' \
        --arg outsider "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_PRINCIPAL_REF=user:${MOCK_OUTSIDER}" \
        --arg outsiderContext "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_CONTEXT_ID=${MOCK_OUTSIDE_CONTEXT}" \
        --arg outsiderSource 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_2_SOURCE=isolated-live-e2e' \
        --arg matrixInternalBaseUrl "WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL=http://${MOCK_SYNAPSE_CONTAINER}:8008" '
          [
            $namespace,$author,$authorContext,$authorSource,
            $collaborator,$collaboratorContext,$collaboratorSource,
            $outsider,$outsiderContext,$outsiderSource,
            "WEAVE_CHAT_PROVIDER=matrix-synapse",
            "WEAVE_CHAT_STORAGE_MODE=jdbc",
            $matrixInternalBaseUrl,
            "WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE=/run/weave-chat-appservice/as-token",
            "WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE=/run/weave-chat-appservice/hs-token",
            "WEAVE_E2E_STACK_SCOPE=isolated",
            "WEAVE_CHAT_E2E_PROOF_ENABLED=true",
            "WEAVE_CHAT_E2E_PROOF_TOKEN_FILE=/run/weave-chat-e2e-proof/token",
            ("WEAVE_CHAT_E2E_PROOF_RUN_ID=" + $ENV.MOCK_RUN_ID)
          ]
        '
    elif [[ "${format}" == '{{json .Mounts}}' ]]; then
      if [[ "${container}" == "${MOCK_BACKEND_CONTAINER}" ]]; then
        printf '[{"Type":"bind","Destination":"/run/weave-chat-e2e-proof/token","RW":false}]\n'
      else
        printf '[]\n'
      fi
    else
      [[ -f "${MOCK_STATE}/${container}-running" ]] || exit 1
      cat "${MOCK_STATE}/${container}-running"
    fi
    ;;
  stop)
    [[ "${1:-}" != --time ]] || shift 2
    container="$1"
    printf 'false\n' >"${MOCK_STATE}/${container}-running"
    printf 'stop:%s\n' "${container}" >>"${MOCK_STATE}/operations.log"
    ;;
  start)
    container="$1"
    printf 'true\n' >"${MOCK_STATE}/${container}-running"
    [[ "${container}" != "${MOCK_SYNAPSE_CONTAINER}" ]] || \
      printf '1\n' >"${MOCK_STATE}/synapse-health-failures"
    printf 'start:%s\n' "${container}" >>"${MOCK_STATE}/operations.log"
    printf '%s\n' "${container}"
    ;;
  restart)
    container="$1"
    printf 'true\n' >"${MOCK_STATE}/${container}-running"
    [[ "${container}" != "${MOCK_SYNAPSE_CONTAINER}" ]] || \
      printf '1\n' >"${MOCK_STATE}/synapse-health-failures"
    printf 'restart:%s\n' "${container}" >>"${MOCK_STATE}/operations.log"
    printf '%s\n' "${container}"
    ;;
  exec)
    [[ "${1:-}" != -i ]] || shift
    container="$1"
    shift
    invocation="$*"
    input="$(mktemp "${MOCK_STATE}/exec-input.XXXXXX")"
    cat >"${input}"
    if [[ "${invocation}" == *'/transactions/'* ]]; then
      if [[ "$(<"${MOCK_STATE}/callback-replay-seen")" == false ]]; then
        printf 'true\n' >"${MOCK_STATE}/callback-replay-seen"
        printf '%s\n' "$(( $(<"${MOCK_STATE}/callback-count") + 1 ))" >"${MOCK_STATE}/callback-count"
      else
        printf '%s\n' "$(( $(<"${MOCK_STATE}/callback-duplicate-count") + 1 ))" \
          >"${MOCK_STATE}/callback-duplicate-count"
      fi
      printf 'exec:callback\n' >>"${MOCK_STATE}/operations.log"
    else
      rm -f "${input}"
      exit 1
    fi
    rm -f "${input}"
    ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "${MOCK_BIN}/docker"

stack_bootstrap="${TMP_DIR}/stack-bootstrap.env"
cat >"${stack_bootstrap}" <<'ENV'
export TF_VAR_tenant_slug=weave
export TF_VAR_keycloak_host_port=48080
export TF_VAR_backend_host_port=48081
export TF_VAR_keycloak_admin_username=admin
export TF_VAR_keycloak_admin_password=fixture-admin-password
export TF_VAR_matrix_chat_appservice_as_token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export TF_VAR_matrix_chat_appservice_hs_token=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ENV

export MOCK_STATE
export MOCK_NAMESPACE="${WEAVE_E2E_RUN_NAMESPACE}"
export MOCK_BACKEND_CONTAINER="${BACKEND_CONTAINER}"
export MOCK_SYNAPSE_CONTAINER="${SYNAPSE_CONTAINER}"
export MOCK_AUTHOR="${WEAVE_E2E_AUTHOR_USERNAME}"
export MOCK_COLLABORATOR="${WEAVE_E2E_COLLABORATOR_USERNAME}"
export MOCK_OUTSIDER="${WEAVE_E2E_OUTSIDER_USERNAME}"
export MOCK_AUTHOR_SUBJECT="${author_subject}"
export MOCK_COLLABORATOR_SUBJECT="${collaborator_subject}"
export MOCK_OUTSIDER_SUBJECT="${outsider_subject}"
export MOCK_RUN_ID="${RUN_ID}"
export MOCK_PROOF_TOKEN
# Populated by the prepared startup environment sourced above.
# shellcheck disable=SC2154
MOCK_PROOF_TOKEN="$(<"${TF_VAR_chat_e2e_proof_token_host_path}")"
# Populated by the prepared startup environment sourced above.
# shellcheck disable=SC2154
export MOCK_TENANT="${TF_VAR_context_authorization_default_tenant_id}"
export MOCK_OUTSIDE_CONTEXT
# shellcheck disable=SC2154
MOCK_OUTSIDE_CONTEXT="$(jq -r '.[2].context_id' <<<"${TF_VAR_isolated_e2e_context_memberships}")"

common_args=(
  --run-id "${RUN_ID}"
  --candidate-commit "${CANDIDATE}"
  --output-root "${OUTPUT_ROOT}"
  --credentials-env "${WEAVE_E2E_CREDENTIAL_ENV_PATH}"
  --startup-env "${WEAVE_E2E_STARTUP_ENV_PATH}"
  --identity-manifest "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}"
  --stack-bootstrap-env "${stack_bootstrap}"
)

before_operations="$(wc -l <"${MOCK_STATE}/operations.log")"
if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=persistent-dogfood \
  bash "${PROOF_SCRIPT}" "${common_args[@]}" --output "${TMP_DIR}/persistent.json" >/dev/null 2>&1; then
  fail "persistent dogfood scope must be rejected"
fi
[[ "$(wc -l <"${MOCK_STATE}/operations.log")" == "${before_operations}" ]] ||
  fail "persistent-scope rejection mutated external state"

failure_log="${TMP_DIR}/failure.log"
if PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated MOCK_FAIL_AFTER_STOP=true \
  bash "${PROOF_SCRIPT}" "${common_args[@]}" --output "${TMP_DIR}/failed.json" \
  >"${failure_log}" 2>&1; then
  fail "injected outage failure unexpectedly passed"
fi
[[ "$(<"${MOCK_STATE}/${SYNAPSE_CONTAINER}-running")" == true ]] ||
  fail "failure trap did not restart Synapse"
jq -e '.directAccessGrantsEnabled == false' "${MOCK_STATE}/client.json" >/dev/null ||
  fail "failure trap did not restore the Keycloak client"
[[ ! -e "${TMP_DIR}/failed.json" ]] || fail "failed proof published an evidence artifact"
! grep -Fq 'MATRIX_SYNAPSE_PROVIDER_PERSISTENCE_RESULT status=passed' "${failure_log}" ||
  fail "failed proof emitted a green marker"

# Reset only the fixture's isolated provider model for the successful proof.
printf 'true\n' >"${MOCK_STATE}/${SYNAPSE_CONTAINER}-running"
printf 'true\n' >"${MOCK_STATE}/${BACKEND_CONTAINER}-running"
printf '0\n' >"${MOCK_STATE}/synapse-health-failures"
printf '0\n' >"${MOCK_STATE}/token-counter"
printf '20\n' >"${MOCK_STATE}/callback-count"
printf '0\n' >"${MOCK_STATE}/callback-duplicate-count"
printf 'false\n' >"${MOCK_STATE}/callback-replay-seen"
printf '0\n' >"${MOCK_STATE}/callback-readiness-checks"
: >"${MOCK_STATE}/tokens.tsv"
: >"${MOCK_STATE}/operations.log"
for pass_index in 1 2; do
  printf '[]\n' >"${MOCK_STATE}/events-${pass_index}.json"
  printf '{}\n' >"${MOCK_STATE}/transactions-${pass_index}.json"
  printf 'false\n' >"${MOCK_STATE}/failed-${pass_index}"
  printf 'false\n' >"${MOCK_STATE}/left-${pass_index}-author"
  printf 'false\n' >"${MOCK_STATE}/left-${pass_index}-collaborator"
done

proof_output="${TMP_DIR}/provider-evidence.json"
proof_log="${TMP_DIR}/proof.log"
if ! PATH="${MOCK_BIN}:${PATH}" WEAVE_E2E_STACK_SCOPE=isolated \
  bash "${PROOF_SCRIPT}" "${common_args[@]}" --output "${proof_output}" >"${proof_log}" 2>&1; then
  grep '^MATRIX_SYNAPSE_PROVIDER_PROOF_ERROR code=' "${proof_log}" >&2 || true
  grep '^proof:correlations:' "${MOCK_STATE}/operations.log" >&2 || true
  fail "successful provider proof fixture failed"
fi

for marker in \
  MATRIX_SYNAPSE_PROVIDER_PERSISTENCE_RESULT \
  MATRIX_SYNAPSE_PROVIDER_EXACTLY_ONCE_RESULT \
  MATRIX_SYNAPSE_PROVIDER_REPLAY_RESULT; do
  [[ "$(grep -c "^${marker} " "${proof_log}")" == 1 ]] || fail "${marker} must appear exactly once"
done

jq -e \
  --arg candidate "${CANDIDATE}" \
  --arg namespaceSha256 "$(sha256 "${WEAVE_E2E_RUN_NAMESPACE}")" '
    .schemaVersion == "weave.isolated-e2e-chat-provider.v1" and
    .candidateCommit == $candidate and .namespaceSha256 == $namespaceSha256 and
    .supportSafe == true and .isolatedRuntimeVerified == true and
    .providerEvidenceEndpointReadOnly == true and .callbackReplayTriggerScoped == true and
    .exactRunBindingVerified == true and
    .independentProofCredentialVerified == true and
    .applicationServiceCredentialReusedForProof == false and
    .southboundProviderAdapterVerified == true and
    .applicationServiceBoundaryVerified == true and
    .canonicalDurableStorageVerified == true and
    .persistentHumanIdentityChanged == false and .repeatCount == 2 and
    .credentialsIncluded == false and .rawIdentityIncluded == false and
    .rawProviderReferenceIncluded == false and .rawProviderPayloadIncluded == false and
    .rawCiphertextIncluded == false and .rawContentIncluded == false and
    (.passes | length) == 2 and ([.passes[].runIndex] | sort) == [1,2] and
    all(.passes[];
      .status == "passed" and .directProviderApiReadback == true and
      .authenticatedProviderReadback == true and .authorizedVirtualUserCount == 2 and
      .authorJoined == true and .collaboratorJoined == true and
      .outsiderProviderMappingAbsent == true and
      .outsiderRoomMembershipAbsent == true and .outsiderReadDenied == true and
      .outsiderWriteDenied == true and .correlatedEncryptedEventCount == 3 and
      .correlatedPlaintextEventCount == 0 and .canonicalCommittedEventCount == 3 and
      .providerAcknowledgedEventCount == 3 and (.correlationSha256 | length) == 3 and
      .providerMembershipExact == true and .providerEncryptionStateVerified == true and
      .providerEventMappingExact == true and .providerCiphertextCorrelationExact == true and
      .backendRestartContinuity == true and .providerRestartContinuity == true and
      .outageOperationInvisible == true and .providerUnavailableSupportSafe == true and
      .otherSurfacesReachableDuringOutage == true and .sameTransactionRetry == true and
      .retryCommittedExactlyOnce == true and .pendingOperationCountAfterRecovery == 0 and
      .duplicateOperationCount == 0 and .callbackReplayDeduplicated == true and
      .canonicalEventDeltaAfterReplay == 0 and .providerEventDeltaAfterReplay == 0 and
      .ledgerDeltaAfterReplay == 0 and .providerReadiness == "available" and
      .providerHealthCached == true and (.providerHealthObservationAgeSeconds >= 0) and
      (.providerHealthObservationAgeSeconds <= 120) and
      .runResourcesCleanupComplete == true and .supportSafe == true
    )
  ' "${proof_output}" >/dev/null || fail "support-safe provider evidence is incomplete"

[[ "$(grep -c "^stop:${SYNAPSE_CONTAINER}$" "${MOCK_STATE}/operations.log")" == 2 ]] ||
  fail "each independent pass must exercise a bounded Synapse outage"
[[ "$(grep -c "^restart:${BACKEND_CONTAINER}$" "${MOCK_STATE}/operations.log")" == 1 ]] ||
  fail "backend persistence restart proof must run exactly once"
[[ "$(grep -c "^restart:${SYNAPSE_CONTAINER}$" "${MOCK_STATE}/operations.log")" == 1 ]] ||
  fail "Synapse persistence restart proof must run exactly once"
[[ "$(grep -c '^synapse-health:503$' "${MOCK_STATE}/operations.log")" == 3 ]] ||
  fail "each Synapse recovery must observe listener startup before readiness"
[[ "$(grep -c '^synapse-health:200$' "${MOCK_STATE}/operations.log")" == 3 ]] ||
  fail "each Synapse recovery must prove listener readiness before provider recovery"
[[ "$(grep -c '^proof:callback-replay$' "${MOCK_STATE}/operations.log")" == 1 ]] ||
  fail "the first real private callback transaction must be replayed exactly once"
[[ "$(<"${MOCK_STATE}/callback-readiness-checks")" == 3 ]] ||
  fail "provider proof must wait for genuine callback capture readiness"
awk -F: '
  $1 == "proof" && $2 == "correlations" && $4 != $5 { mismatch = 1 }
  END { exit mismatch }
' "${MOCK_STATE}/operations.log" ||
  fail "provider evidence must request exactly the correlations committed in each phase"
for pass_index in 1 2; do
  [[ "$(grep -c "^proof:correlations:${pass_index}:2:2$" "${MOCK_STATE}/operations.log")" == 1 ]] ||
    fail "pass ${pass_index} must prove exactly two committed correlations before retry"
  [[ "$(grep -c "^proof:correlations:${pass_index}:3:3$" "${MOCK_STATE}/operations.log")" -ge 3 ]] ||
    fail "pass ${pass_index} must prove all three correlations after retry and restart"
done
for pass_index in 1 2; do
  author_registration_line="$(grep -n "^register:author:${pass_index}$" "${MOCK_STATE}/operations.log" | head -1 | cut -d: -f1)"
  collaborator_registration_line="$(grep -n "^register:collaborator:${pass_index}$" "${MOCK_STATE}/operations.log" | head -1 | cut -d: -f1)"
  create_line="$(grep -n "^create:target:${pass_index}$" "${MOCK_STATE}/operations.log" | head -1 | cut -d: -f1)"
  [[ -n "${author_registration_line}" && -n "${collaborator_registration_line}" && -n "${create_line}" &&
    "${author_registration_line}" -lt "${create_line}" &&
    "${collaborator_registration_line}" -lt "${create_line}" ]] ||
    fail "author and collaborator pass ${pass_index} must register authenticated facades before the invite"
  proof_line="$(grep -n "^proof:evidence:${pass_index}$" "${MOCK_STATE}/operations.log" | head -1 | cut -d: -f1)"
  [[ -n "${proof_line}" && "${create_line}" -lt "${proof_line}" ]] ||
    fail "pass ${pass_index} must prove the target only after its authorized collaboration"
done
jq -e '.directAccessGrantsEnabled == false' "${MOCK_STATE}/client.json" >/dev/null ||
  fail "successful proof did not restore the Keycloak client"

# The literal Matrix event prefix is intentional.
# shellcheck disable=SC2016
for sensitive in \
  "${WEAVE_E2E_AUTHOR_PASSWORD}" \
  "${WEAVE_E2E_COLLABORATOR_PASSWORD}" \
  "${WEAVE_E2E_OUTSIDER_PASSWORD}" \
  "${MOCK_PROOF_TOKEN}" \
  'fixture-admin-token' \
  '!room-pass-' \
  '$event-pass-' \
  'Authorization: Bearer'; do
  ! grep -Fq -- "${sensitive}" "${proof_log}" || fail "stdout leaked private provider proof material"
done

assert_contains "${PROOF_SCRIPT}" 'WEAVE_E2E_STACK_SCOPE'
assert_contains "${PROOF_SCRIPT}" '/api/internal/e2e/chat/provider-proof'
! grep -Fq '/api/internal/chat/matrix/appservice/evidence' "${PROOF_SCRIPT}" || fail "obsolete hs-token evidence endpoint must be absent"
assert_contains "${PROOF_SCRIPT}" 'TF_VAR_chat_e2e_proof_token_host_path'
! grep -Fq 'preproject_outsider_fixture' "${PROOF_SCRIPT}" ||
  fail "provider proof must not create an outsider mapping fixture"
# shellcheck disable=SC2016
assert_contains "${PROOF_SCRIPT}" 'runId:$runId'
# The proof verifies the Application Service runtime boundary but callback
# replay is triggered only by the independent, exact-run proof bearer.
assert_contains "${PROOF_SCRIPT}" 'WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE'
assert_contains "${PROOF_SCRIPT}" '/api/internal/e2e/chat/provider-proof/callback-replay'
assert_contains "${PROOF_SCRIPT}" '/api/internal/e2e/chat/provider-proof/callback-replay/readiness'
! grep -Fq 'docker exec -i "${BACKEND_CONTAINER}"' "${PROOF_SCRIPT}" ||
  fail "callback replay must not export raw provider payload through docker exec"
assert_contains "${PROOF_SCRIPT}" 'SYNAPSE_RESTORE_REQUIRED="true"'
assert_contains "${PROOF_SCRIPT}" '/health'
assert_contains "${PROOF_SCRIPT}" 'PROVIDER_OPERATION_RECOVERY_TIMEOUT_SECONDS=90'
assert_contains "${PROOF_SCRIPT}" 'MATRIX_SYNAPSE_PROVIDER_PERSISTENCE_RESULT'
assert_contains "${PROOF_SCRIPT}" 'MATRIX_SYNAPSE_PROVIDER_EXACTLY_ONCE_RESULT'
assert_contains "${PROOF_SCRIPT}" 'MATRIX_SYNAPSE_PROVIDER_REPLAY_RESULT'

printf 'isolated Matrix/Synapse provider proof tests passed\n'
