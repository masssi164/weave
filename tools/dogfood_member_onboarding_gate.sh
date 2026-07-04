#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Live evidence markers owned by this gate:
# - DOGFOOD_MEMBER_ONBOARDING_RESULT
# - DOGFOOD_ACTIVATION_MAIL_RESULT
# - DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT
# - DOGFOOD_TRUST_STABILITY_RESULT
# - DOGFOOD_TRUST_STABILITY_BLOCKED

original_args=("$@")
prefs_plist=""
expected_handoff_ref=""
expected_run_id=""
index=0
while [[ "${index}" -lt "${#original_args[@]}" ]]; do
  case "${original_args[$index]}" in
    --prefs-plist)
      prefs_plist="${original_args[$((index + 1))]:-}"
      index=$((index + 2))
      ;;
    --expected-handoff-ref)
      expected_handoff_ref="${original_args[$((index + 1))]:-}"
      index=$((index + 2))
      ;;
    --expected-run-id)
      expected_run_id="${original_args[$((index + 1))]:-}"
      index=$((index + 2))
      ;;
    *)
      index=$((index + 1))
      ;;
  esac
done

python3 "${ROOT_DIR}/tools/dogfood_onboarding_evidence_check.py" "$@"

if [[ -n "${WEAVE_DOGFOOD_ACTIVATION_EVIDENCE_JSON:-}" ]]; then
  if [[ -z "${expected_handoff_ref}" ]]; then
    echo "WEAVE_DOGFOOD_ACTIVATION_EVIDENCE_JSON requires --expected-handoff-ref." >&2
    exit 2
  fi
  activation_args=(
    --activation-evidence-file "${WEAVE_DOGFOOD_ACTIVATION_EVIDENCE_JSON}"
    --expected-invite-ref "${expected_handoff_ref}"
    --mailpit-api "${WEAVE_DOGFOOD_MAILPIT_API:-http://127.0.0.1:8025/api/v1/messages}"
  )
  if [[ -n "${WEAVE_DOGFOOD_EXPECTED_EMAIL_SHA256:-}" ]]; then
    activation_args+=(--expected-email-sha256 "${WEAVE_DOGFOOD_EXPECTED_EMAIL_SHA256}")
  fi
  python3 "${ROOT_DIR}/tools/dogfood_activation_mail_check.py" "${activation_args[@]}"
fi

if [[ -n "${WEAVE_DOGFOOD_POST_LOGIN_STATUS_JSON:-}" ]]; then
  if [[ -z "${prefs_plist}" || -z "${expected_handoff_ref}" || -z "${expected_run_id}" ]]; then
    echo "WEAVE_DOGFOOD_POST_LOGIN_STATUS_JSON requires --prefs-plist, --expected-handoff-ref, and --expected-run-id." >&2
    exit 2
  fi
  python3 "${ROOT_DIR}/tools/dogfood_post_login_chat_files_check.py" \
    --prefs-plist "${prefs_plist}" \
    --expected-handoff-ref "${expected_handoff_ref}" \
    --expected-run-id "${expected_run_id}" \
    --status-json "${WEAVE_DOGFOOD_POST_LOGIN_STATUS_JSON}"
fi

if [[ "${WEAVE_DOGFOOD_TRUST_CHECK:-0}" == "1" ]]; then
  # shellcheck disable=SC2086 # Operator-provided trust-check args are intentionally split.
  python3 "${ROOT_DIR}/tools/dogfood_trust_stability_check.py" ${WEAVE_DOGFOOD_TRUST_ARGS:-}
fi
