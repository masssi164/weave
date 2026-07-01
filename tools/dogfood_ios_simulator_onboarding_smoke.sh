#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${ROOT_DIR}/client"
SIMULATOR_ID="${WEAVE_IOS_SIMULATOR_ID:-}"
DEEPLINK="${WEAVE_DOGFOOD_DEEPLINK:-weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood/simulator-onboarding}"
BUNDLE_ID="com.massimotter.weave"
CA_URL="${WEAVE_LOCAL_CA_URL:-https://weave.test:44443/weave-local-ca.pem}"
FLUTTER_BIN="${FLUTTER_BIN:-${HOME}/flutter/bin/flutter}"

fail() {
  echo "dogfood iOS simulator smoke failed: $*" >&2
  exit 1
}

first_available_simulator() {
  xcrun simctl list devices available |
    awk -F '[()]' '/iPhone/ && /Booted/ { print $2; exit }'
  xcrun simctl list devices available |
    awk -F '[()]' '/iPhone/ && /Shutdown|Booted/ { print $2; exit }'
}

if [[ -z "${SIMULATOR_ID}" ]]; then
  SIMULATOR_ID="$(first_available_simulator)"
fi

[[ -n "${SIMULATOR_ID}" ]] || fail "set WEAVE_IOS_SIMULATOR_ID or install an available iPhone simulator"
[[ -x "${FLUTTER_BIN}" ]] || FLUTTER_BIN="$(command -v flutter || true)"
[[ -n "${FLUTTER_BIN}" && -x "${FLUTTER_BIN}" ]] || fail "flutter was not found; set FLUTTER_BIN"

mkdir -p "${EVIDENCE_DIR}"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"

curl -fsSk "${CA_URL}" -o "${EVIDENCE_DIR}/weave-local-ca.pem" \
  || fail "local dogfood CA was not reachable at ${CA_URL}; start the dogfood stack first"

xcrun simctl boot "${SIMULATOR_ID}" >/dev/null 2>&1 || true
xcrun simctl bootstatus "${SIMULATOR_ID}" -b
xcrun simctl keychain "${SIMULATOR_ID}" add-root-cert "${EVIDENCE_DIR}/weave-local-ca.pem" >/dev/null 2>&1 || true

(
  cd "${CLIENT_DIR}"
  "${FLUTTER_BIN}" build ios --simulator
)

xcrun simctl terminate "${SIMULATOR_ID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
xcrun simctl uninstall "${SIMULATOR_ID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
xcrun simctl install "${SIMULATOR_ID}" "${CLIENT_DIR}/build/ios/iphonesimulator/Runner.app"

date -u +"%Y-%m-%dT%H:%M:%SZ" > "${EVIDENCE_DIR}/simulator-launch-started-at.txt"

# simctl openurl proves URL registration but iOS shows a consent sheet before
# delivering the custom-scheme URL. For unattended CI/smoke evidence, inject the
# same pending URL through the native bridge key and launch the installed app.
xcrun simctl spawn "${SIMULATOR_ID}" defaults write "${BUNDLE_ID}" pending_deep_link_url "${DEEPLINK}"
xcrun simctl launch "${SIMULATOR_ID}" "${BUNDLE_ID}"
sleep "${WEAVE_IOS_SIMULATOR_HANDOFF_SETTLE_SECONDS:-10}"

APP_CONTAINER="$(xcrun simctl get_app_container "${SIMULATOR_ID}" "${BUNDLE_ID}" data)"
PREFS_PLIST="${APP_CONTAINER}/Library/Preferences/${BUNDLE_ID}.plist"
[[ -f "${PREFS_PLIST}" ]] || fail "app preferences were not written after simulator launch"

cp "${PREFS_PLIST}" "${EVIDENCE_DIR}/com.massimotter.weave.plist"
plutil -p "${EVIDENCE_DIR}/com.massimotter.weave.plist" > "${EVIDENCE_DIR}/simulator-app-preferences.txt"
xcrun simctl io "${SIMULATOR_ID}" screenshot "${EVIDENCE_DIR}/simulator-onboarding.png" >/dev/null

DEEPLINK="${DEEPLINK}" PREFS_PLIST="${EVIDENCE_DIR}/com.massimotter.weave.plist" python3 - <<'PY'
import json
import os
import plistlib
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
    return json.loads(raw)

handoff = load_json_key("last_handoff_consumed_v1")
visible = load_json_key("dogfood_visible_state_v1")
auth_state = load_json_key("dogfood_auth_state_v1")

checks = [
    (handoff.get("result") == "saved_configuration", "handoff result is not saved_configuration"),
    (visible.get("state") == "handoff_ready", "visible state is not handoff_ready"),
    (auth_state.get("state") == "ready_for_sso", "auth onboarding state is not ready_for_sso"),
]
for name, payload in (
    ("handoff", handoff),
    ("visible", visible),
    ("auth_state", auth_state),
):
    checks.extend([
        (payload.get("handoffRef") == expected_handoff_ref, f"{name} handoffRef mismatch"),
        (payload.get("runId") == expected_run_id, f"{name} runId mismatch"),
        (payload.get("supportSafe") is True, f"{name} is not supportSafe=true"),
    ])

for ok, message in checks:
    if not ok:
        raise SystemExit(message)

print("SIMULATOR_E2E_GREEN visibleState=handoff_ready authState=ready_for_sso")
PY

cat > "${EVIDENCE_DIR}/simulator-onboarding-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-simulator-onboarding-smoke.v1",
  "simulatorId": "${SIMULATOR_ID}",
  "bundleId": "${BUNDLE_ID}",
  "freshInstall": true,
  "deeplinkDelivery": "pending_native_deeplink_url",
  "openUrlConsentSheetAvoided": true,
  "visibleStateRequired": "handoff_ready",
  "authStateRequired": "ready_for_sso",
  "simulatorCoverageBoundary": "Fresh install plus native deeplink handoff readiness only; physical iPhone SSO, restore, reinstall/manual-login, and Mailpit capture remain separate evidence gates."
}
JSON

echo "SIMULATOR_E2E_GREEN evidence=${EVIDENCE_DIR}/simulator-onboarding-smoke.json"
echo "simulator_preferences_evidence=${EVIDENCE_DIR}/simulator-app-preferences.txt"
echo "simulator_screenshot=${EVIDENCE_DIR}/simulator-onboarding.png"
