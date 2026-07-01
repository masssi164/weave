#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Live evidence markers owned by this gate:
# - DOGFOOD_MEMBER_ONBOARDING_RESULT
# - DOGFOOD_TRUST_STABILITY_RESULT
# - DOGFOOD_TRUST_STABILITY_BLOCKED

python3 "${ROOT_DIR}/tools/dogfood_onboarding_evidence_check.py" "$@"

if [[ "${WEAVE_DOGFOOD_TRUST_CHECK:-0}" == "1" ]]; then
  # shellcheck disable=SC2086 # Operator-provided trust-check args are intentionally split.
  python3 "${ROOT_DIR}/tools/dogfood_trust_stability_check.py" ${WEAVE_DOGFOOD_TRUST_ARGS:-}
fi
