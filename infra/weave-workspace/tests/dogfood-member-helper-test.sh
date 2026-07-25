#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/dogfood-member.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
mkdir -p "${TMP_DIR}/bin"

file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

cat >"${TMP_DIR}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
method=GET; url="" write_out=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    --data|--data-urlencode|-H|--cacert|--output) shift 2 ;;
    --write-out) write_out="$2"; shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
printf '%s %s\n' "${method}" "${url}" >>"${FAKE_CURL_LOG}"
if [[ "${url}" == */protocol/openid-connect/token ]]; then printf '{"access_token":"fake-admin-token"}'; exit; fi
state="$(cat "${FAKE_STATE}")"
if [[ "${url}" == *'/api/v1/messages' ]]; then
  if [[ -s "${FAKE_MAIL_SENT}" ]]; then
    printf '{"messages":[{"ID":"mail-1","Subject":"Complete your Weave account setup","To":[{"Address":"human@example.test"}]}]}'
  else
    printf '{"messages":[]}'
  fi
elif [[ "${url}" == *'/users?first=0&max=1000'* ]]; then
  case "${state}" in
    restored-bootstrap) printf '[{"id":"bootstrap-subject-1","username":"test","enabled":true,"emailVerified":true,"requiredActions":[]}]' ;;
    *) printf '[]' ;;
  esac
elif [[ "${url}" == *'/users?'* ]]; then
  case "${state}" in
    missing|restored-bootstrap) printf '[]' ;;
    pending) printf '[{"id":"human-subject-1","username":"human","email":"human@example.test","enabled":true,"emailVerified":false,"requiredActions":["VERIFY_EMAIL","UPDATE_PASSWORD"]}]' ;;
    pending-replacement) printf '[{"id":"human-subject-2","username":"human","email":"human@example.test","enabled":true,"emailVerified":false,"requiredActions":["VERIFY_EMAIL","UPDATE_PASSWORD"]}]' ;;
    active) printf '[{"id":"human-subject-1","username":"human","email":"human@example.test","enabled":true,"emailVerified":true,"requiredActions":[]}]' ;;
    replacement) printf '[{"id":"human-subject-2","username":"human","email":"human@example.test","enabled":true,"emailVerified":true,"requiredActions":[]}]' ;;
  esac
elif [[ "${method}" == GET && "${url}" == */users/human-subject-1 && -n "${write_out}" ]]; then
  if [[ "${FAKE_RECORDED_SUBJECT_PRESENT:-false}" == true ]]; then printf '200'; else printf '404'; fi
elif [[ "${method}" == POST && "${url}" == */users ]]; then
  if [[ "${FAKE_CREATE_REPLACEMENT:-false}" == true ]]; then
    printf pending-replacement >"${FAKE_STATE}"
  else
    printf pending >"${FAKE_STATE}"
  fi
elif [[ "${method}" == DELETE && "${url}" == */users/human-subject-2 ]]; then
  printf missing >"${FAKE_STATE}"
elif [[ "${method}" == DELETE && "${url}" == */users/bootstrap-subject-1 ]]; then
  printf missing >"${FAKE_STATE}"
elif [[ "${url}" == *execute-actions-email* ]]; then
  [[ "${FAKE_DROP_MAIL:-false}" == true ]] || printf sent >"${FAKE_MAIL_SENT}"
elif [[ "${method}" == POST && "${url}" == */organizations/org-1/members ]]; then
  [[ "${FAKE_FAIL_ACCESS:-false}" != true ]] || exit 22
elif [[ "${url}" == *'/organizations?first=0&max=2' ]]; then printf '[{"id":"org-1","name":"weave","alias":"weave"}]'
elif [[ "${url}" == *'/organizations/org-1/groups?search='* ]]; then
  case "${url}" in
    *members*) printf '[{"id":"g1","name":"members","path":"/members"}]' ;;
    *weaver*) printf '[{"id":"g2","name":"weaver","path":"/capabilities/weaver"}]' ;;
  esac
elif [[ "${url}" == *'/organizations/org-1/members/human-subject-'*'/groups' ]]; then
  printf '[{"id":"g1","name":"members","path":"/members"},{"id":"g2","name":"weaver","path":"/capabilities/weaver"}]'
elif [[ "${url}" == *'/organizations/org-1/members/human-subject-'* ]]; then printf '{"id":"persistent-member"}'
fi
EOF
chmod +x "${TMP_DIR}/bin/curl"

export PATH="${TMP_DIR}/bin:${PATH}"
export FAKE_CURL_LOG="${TMP_DIR}/curl.log"
export FAKE_STATE="${TMP_DIR}/state"
export FAKE_MAIL_SENT="${TMP_DIR}/mail-sent"
bootstrap_env="${TMP_DIR}/protected-bootstrap.env"
printf 'export WEAVE_KEYCLOAK_ADMIN_PASSWORD=%q\n' fake-secret >"${bootstrap_env}"
export WEAVE_DOGFOOD_BOOTSTRAP_ENV="${bootstrap_env}"
unset WEAVE_KEYCLOAK_ADMIN_PASSWORD
export WEAVE_DOGFOOD_MEMBER_USERNAME=human
export WEAVE_DOGFOOD_MEMBER_EMAIL=human@example.test
export WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME='Human Tester'
subject_file="${TMP_DIR}/member.subject"
evidence_file="${TMP_DIR}/evidence.json"

printf missing >"${FAKE_STATE}"; : >"${FAKE_CURL_LOG}"
: >"${FAKE_MAIL_SENT}"; rm -f "${FAKE_MAIL_SENT}"
output="$(${SCRIPT} ensure --subject-file "${subject_file}" --evidence-file "${evidence_file}")"
grep -Fq 'state=pending action=created_and_activation_sent' <<<"${output}"
[[ "$(cat "${subject_file}")" == human-subject-1 ]]
[[ "$(file_mode "${subject_file}")" == 600 ]]
[[ "$(file_mode "$(dirname "${subject_file}")")" == 700 ]]
[[ "$(grep -c 'POST .*\/users$' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ "$(grep -c 'PUT .*execute-actions-email' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ "$(grep -c 'PUT .*\/organizations\/org-1\/groups\/g1\/members\/human-subject-1' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ "$(grep -c 'PUT .*\/organizations\/org-1\/groups\/g2\/members\/human-subject-1' "${FAKE_CURL_LOG}")" -eq 1 ]]
if grep -Eq '/users/.*/groups|/users/.*/role-mappings' "${FAKE_CURL_LOG}"; then
  echo 'persistent member used retired realm-group or direct-role administration' >&2; exit 1
fi
jq -e '.state == "pending" and .action == "created_and_activation_sent" and .activation.mailSent == true and .activation.mailVisible == true and (.activation.messageIdSha256 | test("^[0-9a-f]{64}$")) and (.activation.verifiedAt | test("Z$")) and (.activation.requiredActions | contains(["UPDATE_PASSWORD"])) and .qrOrDeeplinkCarriesSecret == false and .appStoresActivationSecret == false and .supportSafe == true and (.subjectSha256 | test("^[0-9a-f]{64}$"))' "${evidence_file}" >/dev/null
if grep -Fq human@example.test "${evidence_file}" || grep -Fq human-subject-1 "${evidence_file}" || grep -Fq mail-1 "${evidence_file}"; then
  echo 'support-safe evidence leaked direct identity data' >&2; exit 1
fi

: >"${FAKE_CURL_LOG}"
output="$(${SCRIPT} ensure --subject-file "${subject_file}")"
grep -Fq 'state=pending action=unchanged' <<<"${output}"
if grep -Eq ' (POST|PUT) .*\/(users$|execute-actions-email)' "${FAKE_CURL_LOG}"; then
  echo 'repeated ensure mutated the pending member' >&2; exit 1
fi

: >"${FAKE_CURL_LOG}"
rm -f "${FAKE_MAIL_SENT}"
output="$(${SCRIPT} resend-activation --subject-file "${subject_file}")"
grep -Fq 'state=pending action=activation_resent' <<<"${output}"
[[ "$(grep -c 'PUT .*execute-actions-email' "${FAKE_CURL_LOG}")" -eq 1 ]]

printf active >"${FAKE_STATE}"; : >"${FAKE_CURL_LOG}"
output="$(${SCRIPT} resend-activation --subject-file "${subject_file}")"
grep -Fq 'state=active action=account_already_active' <<<"${output}"
if grep -Fq execute-actions-email "${FAKE_CURL_LOG}"; then
  echo 'active member received an activation email' >&2; exit 1
fi

printf replacement >"${FAKE_STATE}"
if ${SCRIPT} status --subject-file "${subject_file}" >"${TMP_DIR}/changed.out" 2>&1; then
  echo 'changed subject was accepted' >&2; exit 1
fi
grep -Fq identity_changed "${TMP_DIR}/changed.out"

printf missing >"${FAKE_STATE}"
if ${SCRIPT} ensure --subject-file "${subject_file}" >"${TMP_DIR}/missing.out" 2>&1; then
  echo 'missing recorded subject was recreated' >&2; exit 1
fi
grep -Fq identity_missing "${TMP_DIR}/missing.out"

recovery_evidence="${TMP_DIR}/recovery.json"
active_evidence="${TMP_DIR}/identity-recovery/active-evidence.json"
mkdir -p "$(dirname -- "${active_evidence}")"
subject_one_hash="$(printf human-subject-1 | shasum -a 256 | awk '{print $1}')"
jq -n --arg subjectSha256 "${subject_one_hash}" \
  '{schemaVersion:"weave.dogfood.persistent-member.v1",state:"active",subjectSha256:$subjectSha256,supportSafe:true}' >"${active_evidence}"
: >"${FAKE_CURL_LOG}"
if env FAKE_CREATE_REPLACEMENT=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${recovery_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity >"${TMP_DIR}/active-recovery.out" 2>&1; then
  echo 'pending recovery ignored later active evidence' >&2; exit 1
fi
grep -Fq 'active evidence exists for the recorded identity' "${TMP_DIR}/active-recovery.out"
[[ "$(grep -c 'POST .*\/users$' "${FAKE_CURL_LOG}" || true)" -eq 0 ]]

rm -f "${active_evidence}" "${FAKE_MAIL_SENT}"
: >"${FAKE_CURL_LOG}"
if env FAKE_CREATE_REPLACEMENT=true FAKE_RECORDED_SUBJECT_PRESENT=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${recovery_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity >"${TMP_DIR}/old-subject-present.out" 2>&1; then
  echo 'recovery retired a subject that still exists in Keycloak' >&2; exit 1
fi
grep -Fq 'recorded pending subject still exists in Keycloak' "${TMP_DIR}/old-subject-present.out"
[[ "$(grep -c 'POST .*\/users$' "${FAKE_CURL_LOG}" || true)" -eq 0 ]]

bootstrap_retirement_evidence="${TMP_DIR}/bootstrap-retirement.json"
printf restored-bootstrap >"${FAKE_STATE}"
: >"${FAKE_CURL_LOG}"
if "${SCRIPT}" retire-restored-bootstrap \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${bootstrap_retirement_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-bootstrap-retirement wrong-confirmation >"${TMP_DIR}/wrong-bootstrap-confirmation.out" 2>&1; then
  echo 'restored bootstrap retirement accepted the wrong confirmation' >&2; exit 1
fi
grep -Fq 'requires the exact bootstrap retirement confirmation' "${TMP_DIR}/wrong-bootstrap-confirmation.out"
[[ "$(grep -c 'DELETE .*\/users\/bootstrap-subject-1' "${FAKE_CURL_LOG}" || true)" -eq 0 ]]

: >"${FAKE_CURL_LOG}"
output="$("${SCRIPT}" retire-restored-bootstrap \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${bootstrap_retirement_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-bootstrap-retirement retire-restored-test-bootstrap)"
grep -Fq 'action=restored_disposable_bootstrap_retired' <<<"${output}"
[[ "$(cat "${FAKE_STATE}")" == missing ]]
[[ "$(grep -c 'DELETE .*\/users\/bootstrap-subject-1' "${FAKE_CURL_LOG}")" -eq 1 ]]
jq -e '
  .schemaVersion == "weave.dogfood.restored-bootstrap-retirement.v1" and
  .action == "restored_disposable_bootstrap_retired" and
  .reason == "platform-backup-predates-recorded-protected-member" and
  .protectedIdentityPresentBefore == false and
  .humanIdentityCountBefore == 1 and
  .humanIdentityCountAfter == 0 and
  .deletionBoundary == "keycloak-admin-api-exact-subject" and
  .supportSafe == true
' "${bootstrap_retirement_evidence}" >/dev/null
if grep -Eq 'bootstrap-subject-1|human-subject-1|human@example\.test|"test"' "${bootstrap_retirement_evidence}"; then
  echo 'bootstrap retirement evidence leaked direct identity data' >&2; exit 1
fi

printf missing >"${FAKE_STATE}"
: >"${FAKE_CURL_LOG}"
output="$("${SCRIPT}" retire-restored-bootstrap \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${bootstrap_retirement_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1235' \
  --confirm-bootstrap-retirement retire-restored-test-bootstrap)"
grep -Fq 'action=not_required_empty_human_boundary' <<<"${output}"
[[ "$(grep -c 'DELETE .*\/users\/' "${FAKE_CURL_LOG}" || true)" -eq 0 ]]
jq -e '
  .schemaVersion == "weave.dogfood.restored-bootstrap-retirement.v1" and
  .action == "not_required_empty_human_boundary" and
  .reason == "platform-backup-has-no-human-identity" and
  .protectedIdentityPresentBefore == false and
  .humanIdentityCountBefore == 0 and
  .humanIdentityCountAfter == 0 and
  .deletionBoundary == "none" and
  .providerMutationPerformed == false and
  .supportSafe == true
' "${bootstrap_retirement_evidence}" >/dev/null
if grep -Eq 'human-subject-1|human@example\.test' "${bootstrap_retirement_evidence}"; then
  echo 'empty bootstrap boundary evidence leaked direct identity data' >&2; exit 1
fi

: >"${FAKE_CURL_LOG}"
if env FAKE_CREATE_REPLACEMENT=true FAKE_FAIL_ACCESS=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${recovery_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity >"${TMP_DIR}/failed-access-recovery.out" 2>&1; then
  echo 'recovery accepted incomplete replacement access' >&2; exit 1
fi
grep -Fq 'replacement identity access provisioning failed' "${TMP_DIR}/failed-access-recovery.out"
[[ "$(cat "${subject_file}")" == human-subject-1 ]]
[[ "$(cat "${FAKE_STATE}")" == missing ]]
[[ "$(grep -c 'DELETE .*\/users\/human-subject-2' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ ! -e "${recovery_evidence}" ]]

blocked_evidence_parent="${TMP_DIR}/blocked-evidence-parent"
: >"${blocked_evidence_parent}"
: >"${FAKE_CURL_LOG}"
if failed_evidence_output="$(env FAKE_CREATE_REPLACEMENT=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${blocked_evidence_parent}/recovery.json" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity 2>&1)"; then
  echo 'recovery accepted an unwritable evidence destination' >&2; exit 1
fi
grep -Fq 'support-safe replacement transition evidence could not be prepared' <<<"${failed_evidence_output}"
[[ "$(cat "${subject_file}")" == human-subject-1 ]]
[[ "$(cat "${FAKE_STATE}")" == missing ]]
[[ "$(grep -c 'DELETE .*\/users\/human-subject-2' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ -z "$(find "${TMP_DIR}/retired-pending-identities" -type f -name '*.subject' -print -quit 2>/dev/null || true)" ]]

rm -f "${FAKE_MAIL_SENT}"
: >"${FAKE_CURL_LOG}"
output="$(env FAKE_CREATE_REPLACEMENT=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${recovery_evidence}" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity)"
grep -Fq 'state=pending action=lost_pending_identity_retired_and_recreated' <<<"${output}"
[[ "$(cat "${subject_file}")" == human-subject-2 ]]
[[ "$(grep -c 'POST .*\/users$' "${FAKE_CURL_LOG}")" -eq 1 ]]
retired_subject_file="$(find "${TMP_DIR}/retired-pending-identities" -type f -name '*.subject' -print -quit)"
[[ -n "${retired_subject_file}" && "$(cat "${retired_subject_file}")" == human-subject-1 ]]
[[ "$(file_mode "${retired_subject_file}")" == 600 ]]
[[ "$(file_mode "$(dirname -- "${retired_subject_file}")")" == 700 ]]
subject_two_hash="$(printf human-subject-2 | shasum -a 256 | awk '{print $1}')"
jq -e --arg previous "${subject_one_hash}" --arg replacement "${subject_two_hash}" '
  .schemaVersion == "weave.dogfood.persistent-member-recovery.v1" and
  .state == "pending" and
  .action == "lost_pending_identity_retired_and_recreated" and
  .previousSubjectSha256 == $previous and
  .subjectSha256 == $replacement and
  .retiredSubjectArchivedPrivately == true and
  .activation.mailSent == true and
  .activation.mailVisible == true and
  .readiness.blocked == true and
  (.readiness.requiredGates | contains(["private-backup","restore-smoke","repeat-deployment","activation","member-verification"])) and
  .supportSafe == true
' "${recovery_evidence}" >/dev/null
if grep -Eq 'human@example\.test|human-subject-[12]|mail-1' "${recovery_evidence}"; then
  echo 'recovery evidence leaked direct identity or Mailpit data' >&2; exit 1
fi

if env FAKE_CREATE_REPLACEMENT=true "${SCRIPT}" recover-lost-pending \
  --subject-file "${subject_file}" \
  --prior-evidence "${evidence_file}" \
  --evidence-file "${TMP_DIR}/repeat-recovery.json" \
  --approval-ref 'https://github.com/masssi164/weave/actions/runs/1234' \
  --confirm-retirement retire-lost-pending-identity >"${TMP_DIR}/repeat-recovery.out" 2>&1; then
  echo 'recovery replaced an identity that was already present' >&2; exit 1
fi
grep -Fq 'requires the configured identity to be absent' "${TMP_DIR}/repeat-recovery.out"

if env WEAVE_DOGFOOD_MEMBER_USERNAME=test "${SCRIPT}" status --subject-file "${TMP_DIR}/other.subject" >"${TMP_DIR}/test-user.out" 2>&1; then
  echo 'disposable automation username was accepted' >&2; exit 1
fi
grep -Fq "must not use the disposable automation username 'test'" "${TMP_DIR}/test-user.out"

printf missing >"${FAKE_STATE}"; rm -f "${FAKE_MAIL_SENT}"
if env FAKE_DROP_MAIL=true WEAVE_DOGFOOD_MEMBER_MAILPIT_VERIFY_TIMEOUT_SECONDS=1 \
  "${SCRIPT}" ensure --subject-file "${TMP_DIR}/unverified.subject" >"${TMP_DIR}/unverified.out" 2>&1; then
  echo 'member creation succeeded without HTTPS Mailpit evidence' >&2; exit 1
fi
grep -Fq 'activation mail was not visible through the Mailpit HTTPS API' "${TMP_DIR}/unverified.out"

printf 'dogfood member helper tests passed\n'
