#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${ROOT_DIR}/client"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
BUILD_MODE="${WEAVE_IOS_BUILD_MODE:-profile}"
DEEPLINK="${WEAVE_DOGFOOD_DEEPLINK:-}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood}"
BUNDLE_ID="com.massimotter.weave"

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
  if [[ "${WEAVE_IOS_RESET_APP_DATA:-1}" == "1" ]]; then
    xcrun devicectl device uninstall app --device "${DEVICE_ID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
  fi
  xcrun devicectl device install app --device "${DEVICE_ID}" build/ios/iphoneos/Runner.app
  xcrun devicectl device info apps --device "${DEVICE_ID}" --bundle-id "${BUNDLE_ID}" --json-output "${EVIDENCE_DIR}/ios-installed-app.json" >/dev/null
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${EVIDENCE_DIR}/ios-launch-started-at.txt"
  xcrun devicectl device process launch --device "${DEVICE_ID}" --terminate-existing --payload-url "${DEEPLINK}" "${BUNDLE_ID}"
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
grep -q "dogfood_visible_state_v1" "${EVIDENCE_DIR}/ios-app-preferences.txt" \
  || fail "dogfood_visible_state_v1 was not written after deeplink launch; visible UI readiness is unproven"

DEEPLINK="${DEEPLINK}" PREFS_PLIST="${EVIDENCE_DIR}/appdata/com.massimotter.weave.plist" python3 - <<'PY'
import json
import os
import plistlib
import sys
from urllib.parse import parse_qs, urlparse

prefs_path = os.environ["PREFS_PLIST"]
deeplink = os.environ["DEEPLINK"]
query = parse_qs(urlparse(deeplink).query)
expected_handoff_ref = query.get("handoff_ref", [""])[0]
expected_run_id = query.get("run_id", [""])[0]

with open(prefs_path, "rb") as handle:
    prefs = plistlib.load(handle)

def load_json_key(key: str) -> dict:
    raw = prefs.get(key)
    if not isinstance(raw, str) or not raw:
        raise SystemExit(f"{key} missing from app preferences")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{key} is not JSON: {exc}") from exc

handoff = load_json_key("last_handoff_consumed_v1")
visible = load_json_key("dogfood_visible_state_v1")

checks = [
    (handoff.get("result") == "saved_configuration", "handoff result is not saved_configuration"),
    (handoff.get("handoffRef") == expected_handoff_ref, "handoffRef does not match deeplink"),
    (handoff.get("runId") == expected_run_id, "runId does not match deeplink"),
    (handoff.get("supportSafe") is True, "handoff evidence is not supportSafe=true"),
    (visible.get("state") == "handoff_ready", "visible state is not handoff_ready"),
    (visible.get("handoffRef") == expected_handoff_ref, "visible handoffRef does not match deeplink"),
    (visible.get("runId") == expected_run_id, "visible runId does not match deeplink"),
    (visible.get("supportSafe") is True, "visible evidence is not supportSafe=true"),
]
for ok, message in checks:
    if not ok:
        raise SystemExit(message)

print("ios_visible_state=handoff_ready")
PY

cat > "${EVIDENCE_DIR}/ios-deeplink-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-deeplink-smoke.v1",
  "buildMode": "${BUILD_MODE}",
  "bundleId": "${BUNDLE_ID}",
  "deviceId": "${DEVICE_ID}",
  "deeplinkLaunchAttempted": true,
  "visibleStateRequired": "handoff_ready",
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Continue from the visible handoff-ready screen into SSO and landing."
}
JSON

echo "ios_install_evidence=${EVIDENCE_DIR}/ios-installed-app.json"
echo "ios_smoke_evidence=${EVIDENCE_DIR}/ios-deeplink-smoke.json"
echo "ios_preferences_evidence=${EVIDENCE_DIR}/ios-app-preferences.txt"
