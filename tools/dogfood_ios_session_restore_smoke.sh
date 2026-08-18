#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood/iphone-session-restore}"
BUNDLE_ID="com.massimotter.weave"
SETTLE_SECONDS="${WEAVE_IOS_SESSION_RESTORE_SETTLE_SECONDS:-15}"

fail() {
  echo "dogfood iOS session restore smoke failed: $*" >&2
  exit 1
}

[[ -n "${DEVICE_ID}" ]] || fail "set WEAVE_IOS_DEVICE_ID to the paired iPhone device identifier"
command -v xcrun >/dev/null || fail "xcrun was not found"

mkdir -p "${EVIDENCE_DIR}/before" "${EVIDENCE_DIR}/after"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"
BEFORE_PLIST="${EVIDENCE_DIR}/before/com.massimotter.weave.plist"
AFTER_PLIST="${EVIDENCE_DIR}/after/com.massimotter.weave.plist"
INSTALLED_JSON="${EVIDENCE_DIR}/ios-installed-app.json"

rm -f "${BEFORE_PLIST}" "${AFTER_PLIST}" "${INSTALLED_JSON}"
xcrun devicectl device copy from \
  --device "${DEVICE_ID}" \
  --domain-type appDataContainer \
  --domain-identifier "${BUNDLE_ID}" \
  --source "Library/Preferences/${BUNDLE_ID}.plist" \
  --destination "${BEFORE_PLIST}" >/dev/null

xcrun devicectl device process launch \
  --device "${DEVICE_ID}" \
  --terminate-existing \
  "${BUNDLE_ID}" >/dev/null

sleep "${SETTLE_SECONDS}"

xcrun devicectl device copy from \
  --device "${DEVICE_ID}" \
  --domain-type appDataContainer \
  --domain-identifier "${BUNDLE_ID}" \
  --source "Library/Preferences/${BUNDLE_ID}.plist" \
  --destination "${AFTER_PLIST}" >/dev/null
xcrun devicectl device info apps \
  --device "${DEVICE_ID}" \
  --bundle-id "${BUNDLE_ID}" \
  --json-output "${INSTALLED_JSON}" >/dev/null

python3 "${ROOT_DIR}/tools/dogfood_ios_session_continuity_check.py" \
  --before-prefs-plist "${BEFORE_PLIST}" \
  --after-prefs-plist "${AFTER_PLIST}" \
  --installed-app-json "${INSTALLED_JSON}" \
  --expected-bundle-id "${BUNDLE_ID}" \
  --output-json "${EVIDENCE_DIR}/ios-session-continuity.json"

echo "ios_session_continuity_evidence=${EVIDENCE_DIR}/ios-session-continuity.json"
