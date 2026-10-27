#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Live evidence markers owned by this gate:
# - DOGFOOD_MEMBER_ONBOARDING_RESULT
# - DOGFOOD_ACTIVATION_MAIL_RESULT
# - DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT
# - DOGFOOD_TRUST_STABILITY_RESULT
# - DOGFOOD_TRUST_STABILITY_BLOCKED
# - DOGFOOD_SESSION_CONTINUITY_RESULT

checker_args=()
expected_handoff_ref=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --expected-handoff-ref)
      if [[ "$#" -lt 2 || -z "$2" ]]; then
        echo "--expected-handoff-ref requires an invitation reference." >&2
        exit 2
      fi
      expected_handoff_ref="$2"
      shift 2
      ;;
    *)
      checker_args+=("$1")
      shift
      ;;
  esac
done

if [[ -n "${WEAVE_DOGFOOD_POST_LOGIN_STATUS_JSON:-}" || "${WEAVE_DOGFOOD_SESSION_CONTINUITY_CHECK:-0}" == "1" ]]; then
  echo "Private preference-backed evidence is retired; supply the native functional receipt." >&2
  exit 2
fi

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

if [[ "${WEAVE_DOGFOOD_TRUST_CHECK:-0}" == "1" ]]; then
  # shellcheck disable=SC2086 # Operator-provided trust-check args are intentionally split.
  python3 "${ROOT_DIR}/tools/dogfood_trust_stability_check.py" ${WEAVE_DOGFOOD_TRUST_ARGS:-}
fi

python3 "${ROOT_DIR}/tools/dogfood_onboarding_evidence_check.py" "${checker_args[@]}"
