#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${ROOT_DIR}/client"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
BUILD_MODE="${WEAVE_IOS_BUILD_MODE:-profile}"
DEEPLINK="${WEAVE_DOGFOOD_DEEPLINK:-}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood}"
BUNDLE_ID="com.massimotter.weave"
FLUTTER_BIN="${FLUTTER_BIN:-${HOME}/flutter/bin/flutter}"
INSTALL_TRANSPORT="${WEAVE_IOS_INSTALL_TRANSPORT:-wifi}"
RESET_MODE="${WEAVE_IOS_RESET_MODE:-update_in_place}"
EXPECTED_TEAM_ID="${WEAVE_IOS_EXPECTED_TEAM_ID:-KNDHGC2KV6}"
EXPECTED_DEVELOPER_CERT_TEAM_ID="${WEAVE_IOS_EXPECTED_DEVELOPER_CERT_TEAM_ID:-6RUS2Z848X}"
LOCAL_CA_TRUST_STATUS="${WEAVE_IOS_LOCAL_CA_TRUST_STATUS:-not_verified}"
PLATFORM_CONFIG_URL="${WEAVE_DOGFOOD_PLATFORM_CONFIG_URL:-}"

fail() {
  echo "dogfood iOS smoke failed: $*" >&2
  exit 1
}

[[ -n "${DEVICE_ID}" ]] || fail "set WEAVE_IOS_DEVICE_ID to the paired iPhone device identifier"
[[ -n "${DEEPLINK}" ]] || fail "set WEAVE_DOGFOOD_DEEPLINK to the current weave://join URL"
[[ "${BUILD_MODE}" != "debug" ]] || fail "debug builds are invalid for installed iOS custom-scheme smoke; use profile or release"
[[ "${BUILD_MODE}" == "profile" || "${BUILD_MODE}" == "release" ]] || fail "WEAVE_IOS_BUILD_MODE must be profile or release"
[[ -z "${WEAVE_IOS_RESET_APP_DATA:-}" ]] || fail "WEAVE_IOS_RESET_APP_DATA is deprecated because uninstall can destroy Developer App trust; use WEAVE_IOS_RESET_MODE=update_in_place, app_state, or destructive_uninstall"
[[ "${INSTALL_TRANSPORT}" == "wifi" || "${INSTALL_TRANSPORT}" == "usb" || "${INSTALL_TRANSPORT}" == "unknown" ]] || fail "WEAVE_IOS_INSTALL_TRANSPORT must be wifi, usb, or unknown"
[[ "${RESET_MODE}" == "update_in_place" || "${RESET_MODE}" == "app_state" || "${RESET_MODE}" == "destructive_uninstall" ]] || fail "WEAVE_IOS_RESET_MODE must be update_in_place, app_state, or destructive_uninstall"
[[ "${LOCAL_CA_TRUST_STATUS}" == "trusted" || "${LOCAL_CA_TRUST_STATUS}" == "manual_pending" || "${LOCAL_CA_TRUST_STATUS}" == "publicly_trusted" || "${LOCAL_CA_TRUST_STATUS}" == "not_required" || "${LOCAL_CA_TRUST_STATUS}" == "not_verified" ]] || fail "WEAVE_IOS_LOCAL_CA_TRUST_STATUS must be trusted, manual_pending, publicly_trusted, not_required, or not_verified"
[[ -x "${FLUTTER_BIN}" ]] || FLUTTER_BIN="$(command -v flutter || true)"
[[ -n "${FLUTTER_BIN}" && -x "${FLUTTER_BIN}" ]] || fail "flutter was not found; set FLUTTER_BIN"

mkdir -p "${EVIDENCE_DIR}"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"
RUN_DEEPLINK="${DEEPLINK}"
if [[ "${RESET_MODE}" == "app_state" ]]; then
  RUN_DEEPLINK="$(DEEPLINK="${DEEPLINK}" python3 - <<'PY'
import os
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

parts = urlsplit(os.environ["DEEPLINK"])
query = dict(parse_qsl(parts.query, keep_blank_values=True))
query["dogfood_reset"] = "app_state"
print(urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment)))
PY
)"
fi

if [[ -z "${PLATFORM_CONFIG_URL}" ]]; then
  PLATFORM_CONFIG_URL="$(DEEPLINK="${DEEPLINK}" python3 - <<'PY'
import os
from urllib.parse import parse_qs, unquote, urlparse, urlunparse

query = parse_qs(urlparse(os.environ["DEEPLINK"]).query)
value = query.get("platform_config_url", [""])[0]
if value:
    print(unquote(value))
else:
    product_base_url = unquote(query.get("product_base_url", [""])[0])
    parsed = urlparse(product_base_url)
    print(urlunparse((parsed.scheme, parsed.netloc, "/api/platform/config", "", "", "")) if parsed.scheme and parsed.netloc else "")
PY
)"
fi
LOCAL_DOGFOOD_TLS_REQUIRED="$(PLATFORM_CONFIG_URL="${PLATFORM_CONFIG_URL}" python3 - <<'PY'
import os
from urllib.parse import urlparse

url = os.environ.get("PLATFORM_CONFIG_URL", "")
parsed = urlparse(url)
host = parsed.hostname or ""
requires_local_ca = (
    parsed.scheme == "https"
    and (
        host.endswith("weave.test")
        or host.endswith(".weave.test")
        or parsed.port == 44443
    )
)
print("1" if requires_local_ca else "0")
PY
)"
cat > "${EVIDENCE_DIR}/ios-local-tls-preflight.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-local-tls-preflight.v1",
  "supportSafe": true,
  "platformConfigUrl": "${PLATFORM_CONFIG_URL}",
  "localDogfoodTlsRequired": $([[ "${LOCAL_DOGFOOD_TLS_REQUIRED}" == "1" ]] && echo true || echo false),
  "iosLocalCaTrustStatus": "${LOCAL_CA_TRUST_STATUS}",
  "manualPrecondition": "For local dogfood TLS, install the Weave Local Development CA profile on the iPhone and enable full trust before launching Weave.",
  "failureCodeWhenPending": "PHYSICAL_DEVICE_TLS_PENDING"
}
JSON
if [[ "${LOCAL_DOGFOOD_TLS_REQUIRED}" == "1" ]]; then
  case "${LOCAL_CA_TRUST_STATUS}" in
    trusted|publicly_trusted) ;;
    manual_pending)
      fail "PHYSICAL_DEVICE_TLS_PENDING: install the Weave Local Development CA profile on the iPhone, enable full trust in Settings > General > About > Certificate Trust Settings, then rerun with WEAVE_IOS_LOCAL_CA_TRUST_STATUS=trusted"
      ;;
    *)
      fail "PHYSICAL_DEVICE_TLS_PENDING: local dogfood platform config uses HTTPS with the Weave local CA; confirm iPhone CA trust first with WEAVE_IOS_LOCAL_CA_TRUST_STATUS=trusted or use a publicly trusted dogfood endpoint"
      ;;
  esac
fi

xcrun devicectl device info details \
  --device "${DEVICE_ID}" \
  --timeout "${WEAVE_IOS_DEVICE_REACHABILITY_TIMEOUT_SECONDS:-30}" \
  --json-output "${EVIDENCE_DIR}/ios-device-details.json" >/dev/null \
  || fail "iPhone ${DEVICE_ID} is not reachable; pair over USB first, enable Connect via Network for Wi-Fi, or use WEAVE_IOS_INSTALL_TRANSPORT=usb while plugged in"

(
  cd "${CLIENT_DIR}"
  "${FLUTTER_BIN}" build ios "--${BUILD_MODE}"
  EXPECTED_TEAM_ID="${EXPECTED_TEAM_ID}" EXPECTED_DEVELOPER_CERT_TEAM_ID="${EXPECTED_DEVELOPER_CERT_TEAM_ID}" \
    APP_PATH="build/ios/iphoneos/Runner.app" \
    OUTPUT_PATH="${EVIDENCE_DIR}/ios-signing-evidence.json" \
    python3 - <<'PY'
import hashlib
import json
import os
import plistlib
import re
import subprocess
from pathlib import Path

app = Path(os.environ["APP_PATH"])
output_path = Path(os.environ["OUTPUT_PATH"])
expected_team_id = os.environ["EXPECTED_TEAM_ID"]
expected_developer_cert_team_id = os.environ["EXPECTED_DEVELOPER_CERT_TEAM_ID"]
provision = app / "embedded.mobileprovision"
profile = plistlib.loads(subprocess.check_output(["security", "cms", "-D", "-i", str(provision)]))
info = plistlib.loads((app / "Info.plist").read_bytes())
codesign = subprocess.check_output(["codesign", "-dv", "--verbose=4", str(app)], stderr=subprocess.STDOUT, text=True)
authority = next((line.split("=", 1)[1] for line in codesign.splitlines() if line.startswith("Authority=Apple Development:")), "")
authority_team_match = re.search(r"\(([^)]+)\)$", authority)
entitlements = profile.get("Entitlements", {})
application_identifier = entitlements.get("application-identifier", "") if isinstance(entitlements, dict) else ""
payload = {
    "schemaVersion": "weave.dogfood.ios-signing-evidence.v1",
    "supportSafe": True,
    "bundleId": info.get("CFBundleIdentifier", ""),
    "expectedBundleId": "com.massimotter.weave",
    "teamId": (profile.get("TeamIdentifier") or [""])[0],
    "expectedTeamId": expected_team_id,
    "developerCertificateName": authority,
    "developerCertificateTeamId": authority_team_match.group(1) if authority_team_match else "",
    "expectedDeveloperCertificateTeamId": expected_developer_cert_team_id,
    "provisioningProfileName": profile.get("Name", ""),
    "provisioningProfileUuid": profile.get("UUID", ""),
    "provisioningProfileSha256": hashlib.sha256(provision.read_bytes()).hexdigest(),
    "profileApplicationIdentifier": application_identifier,
    "teamIdMatches": expected_team_id in (profile.get("TeamIdentifier") or []),
    "developerCertificateTeamIdMatches": (authority_team_match.group(1) if authority_team_match else "") == expected_developer_cert_team_id,
    "profileApplicationIdentifierMatches": str(application_identifier).endswith(".com.massimotter.weave"),
}
output_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  if [[ "${RESET_MODE}" == "destructive_uninstall" ]]; then
    xcrun devicectl device uninstall app --device "${DEVICE_ID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
  fi
  xcrun devicectl device install app --device "${DEVICE_ID}" build/ios/iphoneos/Runner.app
  xcrun devicectl device info apps --device "${DEVICE_ID}" --bundle-id "${BUNDLE_ID}" --json-output "${EVIDENCE_DIR}/ios-installed-app.json" >/dev/null
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${EVIDENCE_DIR}/ios-launch-started-at.txt"
  xcrun devicectl device process launch --device "${DEVICE_ID}" --terminate-existing --payload-url "${RUN_DEEPLINK}" "${BUNDLE_ID}"
)

sleep "${WEAVE_IOS_HANDOFF_SETTLE_SECONDS:-15}"

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
grep -q "dogfood_auth_state_v1" "${EVIDENCE_DIR}/ios-app-preferences.txt" \
  || fail "dogfood_auth_state_v1 was not written after deeplink launch; typed auth onboarding readiness is unproven"

cat > "${EVIDENCE_DIR}/ios-deeplink-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-deeplink-smoke.v1",
  "buildMode": "${BUILD_MODE}",
  "bundleId": "${BUNDLE_ID}",
  "deviceId": "${DEVICE_ID}",
  "installTransport": "${INSTALL_TRANSPORT}",
  "installResetMode": "${RESET_MODE}",
  "usedDestructiveUninstall": $([[ "${RESET_MODE}" == "destructive_uninstall" ]] && echo true || echo false),
  "dogfoodAppStateResetRequested": $([[ "${RESET_MODE}" == "app_state" ]] && echo true || echo false),
  "expectedTeamId": "${EXPECTED_TEAM_ID}",
  "expectedDeveloperCertificateTeamId": "${EXPECTED_DEVELOPER_CERT_TEAM_ID}",
  "deeplinkLaunchAttempted": true,
  "visibleStateRequired": "handoff_ready",
  "authStateRequired": "ready_for_sso",
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Continue from the visible handoff-ready screen into SSO and landing."
}
JSON

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
auth_state = load_json_key("dogfood_auth_state_v1")

checks = [
    (handoff.get("result") == "saved_configuration", "handoff result is not saved_configuration"),
    (handoff.get("handoffRef") == expected_handoff_ref, "handoffRef does not match deeplink"),
    (handoff.get("runId") == expected_run_id, "runId does not match deeplink"),
    (handoff.get("supportSafe") is True, "handoff evidence is not supportSafe=true"),
    (visible.get("state") == "handoff_ready", "visible state is not handoff_ready"),
    (visible.get("handoffRef") == expected_handoff_ref, "visible handoffRef does not match deeplink"),
    (visible.get("runId") == expected_run_id, "visible runId does not match deeplink"),
    (visible.get("supportSafe") is True, "visible evidence is not supportSafe=true"),
    (auth_state.get("state") == "ready_for_sso", "auth onboarding state is not ready_for_sso"),
    (auth_state.get("handoffRef") == expected_handoff_ref, "auth onboarding handoffRef does not match deeplink"),
    (auth_state.get("runId") == expected_run_id, "auth onboarding runId does not match deeplink"),
    (auth_state.get("supportSafe") is True, "auth onboarding evidence is not supportSafe=true"),
]
for ok, message in checks:
    if not ok:
        raise SystemExit(message)

print("ios_visible_state=handoff_ready")
print("ios_auth_state=ready_for_sso")
PY

cat > "${EVIDENCE_DIR}/ios-deeplink-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-deeplink-smoke.v1",
  "buildMode": "${BUILD_MODE}",
  "bundleId": "${BUNDLE_ID}",
  "deviceId": "${DEVICE_ID}",
  "installTransport": "${INSTALL_TRANSPORT}",
  "installResetMode": "${RESET_MODE}",
  "usedDestructiveUninstall": $([[ "${RESET_MODE}" == "destructive_uninstall" ]] && echo true || echo false),
  "dogfoodAppStateResetRequested": $([[ "${RESET_MODE}" == "app_state" ]] && echo true || echo false),
  "expectedTeamId": "${EXPECTED_TEAM_ID}",
  "expectedDeveloperCertificateTeamId": "${EXPECTED_DEVELOPER_CERT_TEAM_ID}",
  "deeplinkLaunchAttempted": true,
  "visibleStateRequired": "handoff_ready",
  "authStateRequired": "ready_for_sso",
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Continue from the visible handoff-ready screen into SSO and landing."
}
JSON

echo "ios_install_evidence=${EVIDENCE_DIR}/ios-installed-app.json"
echo "ios_signing_evidence=${EVIDENCE_DIR}/ios-signing-evidence.json"
echo "ios_smoke_evidence=${EVIDENCE_DIR}/ios-deeplink-smoke.json"
echo "ios_preferences_evidence=${EVIDENCE_DIR}/ios-app-preferences.txt"
