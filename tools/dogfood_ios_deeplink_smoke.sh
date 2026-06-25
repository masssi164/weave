#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${ROOT_DIR}/client"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
BUILD_MODE="${WEAVE_IOS_BUILD_MODE:-profile}"
DEEPLINK="${WEAVE_DOGFOOD_DEEPLINK:-}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood}"

fail() {
  echo "dogfood iOS smoke failed: $*" >&2
  exit 1
}

[[ -n "${DEVICE_ID}" ]] || fail "set WEAVE_IOS_DEVICE_ID to the paired iPhone device identifier"
[[ -n "${DEEPLINK}" ]] || fail "set WEAVE_DOGFOOD_DEEPLINK to the current weave://join URL"
[[ "${BUILD_MODE}" != "debug" ]] || fail "debug builds are invalid for installed iOS custom-scheme smoke; use profile or release"
[[ "${BUILD_MODE}" == "profile" || "${BUILD_MODE}" == "release" ]] || fail "WEAVE_IOS_BUILD_MODE must be profile or release"

mkdir -p "${EVIDENCE_DIR}"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"

(
  cd "${CLIENT_DIR}"
  flutter build ios "--${BUILD_MODE}"
  xcrun devicectl device install app --device "${DEVICE_ID}" build/ios/iphoneos/Runner.app
  xcrun devicectl device info apps --device "${DEVICE_ID}" --bundle-id com.massimotter.weave --json-output "${EVIDENCE_DIR}/ios-installed-app.json" >/dev/null
  xcrun devicectl device process launch --device "${DEVICE_ID}" --terminate-existing --payload-url "${DEEPLINK}" com.massimotter.weave
)

sleep "${WEAVE_IOS_HANDOFF_SETTLE_SECONDS:-3}"

mkdir -p "${EVIDENCE_DIR}/appdata"
xcrun devicectl device copy from \
  --device "${DEVICE_ID}" \
  --domain-type appDataContainer \
  --domain-identifier com.massimotter.weave \
  --source Library/Preferences/com.massimotter.weave.plist \
  --destination "${EVIDENCE_DIR}/appdata/com.massimotter.weave.plist" >/dev/null
plutil -p "${EVIDENCE_DIR}/appdata/com.massimotter.weave.plist" > "${EVIDENCE_DIR}/ios-app-preferences.txt"
grep -q "last_handoff_consumed_v1" "${EVIDENCE_DIR}/ios-app-preferences.txt" \
  || fail "last_handoff_consumed_v1 was not written after deeplink launch"

cat > "${EVIDENCE_DIR}/ios-deeplink-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-deeplink-smoke.v1",
  "buildMode": "${BUILD_MODE}",
  "bundleId": "com.massimotter.weave",
  "deviceId": "${DEVICE_ID}",
  "deeplinkLaunchAttempted": true,
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Verify last_handoff_consumed_v1 in app preferences and Massimo-visible handoff-aware sign-in state."
}
JSON

echo "ios_install_evidence=${EVIDENCE_DIR}/ios-installed-app.json"
echo "ios_smoke_evidence=${EVIDENCE_DIR}/ios-deeplink-smoke.json"
echo "ios_preferences_evidence=${EVIDENCE_DIR}/ios-app-preferences.txt"
