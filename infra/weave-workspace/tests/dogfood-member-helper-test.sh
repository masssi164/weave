#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/dogfood-member.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
mkdir -p "${TMP_DIR}/bin"

cat >"${TMP_DIR}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
method=GET; url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    --data|--data-urlencode|-H|--cacert) shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
printf '%s %s\n' "${method}" "${url}" >>"${FAKE_CURL_LOG}"
if [[ "${url}" == */protocol/openid-connect/token ]]; then printf '{"access_token":"fake-admin-token"}'; exit; fi
state="$(cat "${FAKE_STATE}")"
if [[ "${url}" == *'/users?'* ]]; then
  case "${state}" in
    missing) printf '[]' ;;
    pending) printf '[{"id":"human-subject-1","username":"human","email":"human@example.test","enabled":true,"emailVerified":false,"requiredActions":["VERIFY_EMAIL","UPDATE_PASSWORD"]}]' ;;
    active) printf '[{"id":"human-subject-1","username":"human","email":"human@example.test","enabled":true,"emailVerified":true,"requiredActions":[]}]' ;;
    replacement) printf '[{"id":"human-subject-2","username":"human","email":"human@example.test","enabled":true,"emailVerified":true,"requiredActions":[]}]' ;;
  esac
elif [[ "${method}" == POST && "${url}" == */users ]]; then
  printf pending >"${FAKE_STATE}"
elif [[ "${url}" == *'/organizations?'* ]]; then printf '[{"id":"org-1","name":"weave","alias":"weave"}]'
elif [[ "${url}" == *'/clients?clientId='* ]]; then printf '[{"id":"client-1","name":"weave-app","clientId":"weave-app"}]'
elif [[ "${url}" == *'/clients/client-1/roles/member' ]]; then printf '{"id":"role-1","name":"member"}'
elif [[ "${url}" == *'/role-mappings/clients/client-1' ]]; then printf '[{"id":"role-1","name":"member"}]'
elif [[ "${url}" == *'/users/human-subject-1/groups' ]]; then printf '[{"id":"g1","name":"workspace-members"},{"id":"g2","name":"weave-board-editors"},{"id":"g3","name":"weave-calendar-editors"}]'
elif [[ "${url}" == *'/groups?search='* ]]; then
  case "${url}" in
    *workspace-members*) printf '[{"id":"g1","name":"workspace-members"}]' ;;
    *weave-board-editors*) printf '[{"id":"g2","name":"weave-board-editors"}]' ;;
    *weave-calendar-editors*) printf '[{"id":"g3","name":"weave-calendar-editors"}]' ;;
  esac
elif [[ "${url}" == *'/organizations/org-1/members/human-subject-1' ]]; then printf '{"id":"human-subject-1"}'
fi
EOF
chmod +x "${TMP_DIR}/bin/curl"

export PATH="${TMP_DIR}/bin:${PATH}"
export FAKE_CURL_LOG="${TMP_DIR}/curl.log"
export FAKE_STATE="${TMP_DIR}/state"
export TF_VAR_keycloak_admin_password=fake-secret
export WEAVE_DOGFOOD_MEMBER_USERNAME=human
export WEAVE_DOGFOOD_MEMBER_EMAIL=human@example.test
export WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME='Human Tester'
subject_file="${TMP_DIR}/member.subject"
evidence_file="${TMP_DIR}/evidence.json"

printf missing >"${FAKE_STATE}"; : >"${FAKE_CURL_LOG}"
output="$(${SCRIPT} ensure --subject-file "${subject_file}" --evidence-file "${evidence_file}")"
grep -Fq 'state=pending action=created_and_activation_sent' <<<"${output}"
[[ "$(cat "${subject_file}")" == human-subject-1 ]]
[[ "$(stat -f '%Lp' "${subject_file}" 2>/dev/null || stat -c '%a' "${subject_file}")" == 600 ]]
[[ "$(stat -f '%Lp' "$(dirname "${subject_file}")" 2>/dev/null || stat -c '%a' "$(dirname "${subject_file}")")" == 700 ]]
[[ "$(grep -c 'POST .*\/users$' "${FAKE_CURL_LOG}")" -eq 1 ]]
[[ "$(grep -c 'PUT .*execute-actions-email' "${FAKE_CURL_LOG}")" -eq 1 ]]
jq -e '.state == "pending" and .action == "created_and_activation_sent" and .activation.mailSent == true and (.activation.requiredActions | contains(["UPDATE_PASSWORD"])) and .qrOrDeeplinkCarriesSecret == false and .appStoresActivationSecret == false and .supportSafe == true and (.subjectSha256 | test("^[0-9a-f]{64}$"))' "${evidence_file}" >/dev/null
if grep -Fq human@example.test "${evidence_file}" || grep -Fq human-subject-1 "${evidence_file}"; then
  echo 'support-safe evidence leaked direct identity data' >&2; exit 1
fi

: >"${FAKE_CURL_LOG}"
output="$(${SCRIPT} ensure --subject-file "${subject_file}")"
grep -Fq 'state=pending action=unchanged' <<<"${output}"
if grep -Eq ' (POST|PUT) .*\/(users$|execute-actions-email)' "${FAKE_CURL_LOG}"; then
  echo 'repeated ensure mutated the pending member' >&2; exit 1
fi

: >"${FAKE_CURL_LOG}"
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

if env WEAVE_DOGFOOD_MEMBER_USERNAME=test "${SCRIPT}" status --subject-file "${TMP_DIR}/other.subject" >"${TMP_DIR}/test-user.out" 2>&1; then
  echo 'disposable automation username was accepted' >&2; exit 1
fi
grep -Fq "must not use the disposable automation username 'test'" "${TMP_DIR}/test-user.out"

printf 'dogfood member helper tests passed\n'
