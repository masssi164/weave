#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Live evidence markers owned by this gate:
# - DOGFOOD_MEMBER_ONBOARDING_RESULT
# - DOGFOOD_POST_LOGIN_CHAT_FILES_RESULT
# - DOGFOOD_TRUST_STABILITY_RESULT
# - DOGFOOD_TRUST_STABILITY_BLOCKED

python3 "${ROOT_DIR}/tools/dogfood_onboarding_evidence_check.py" "$@"

if [[ -n "${WEAVE_DOGFOOD_POST_LOGIN_STATUS_JSON:-}" ]]; then
  prefs_plist=""
  expected_handoff_ref=""
  expected_run_id=""
  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --prefs-plist)
        prefs_plist="${2:-}"
        shift 2
        ;;
      --expected-handoff-ref)
        expected_handoff_ref="${2:-}"
        shift 2
        ;;
      --expected-run-id)
        expected_run_id="${2:-}"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
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
