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
ORGANIZATION_ORIGIN="${WEAVE_DOGFOOD_ORGANIZATION_ORIGIN:-}"
EXPECTED_VISIBLE_STATE="handoff_ready"
EXPECTED_AUTH_STATE="ready_for_sso"
ORDINARY_STARTUP=0
if [[ "${1:-}" == "--ordinary-startup" && "$#" == "1" ]]; then
  ORDINARY_STARTUP=1
elif [[ "$#" != "0" ]]; then
  echo "dogfood iOS smoke failed: unknown arguments" >&2; exit 1
fi
CANDIDATE_COMMIT="${WEAVE_CANDIDATE_COMMIT:-$(git -C "${ROOT_DIR}" rev-parse HEAD)}"
BUILD_NUMBER="${WEAVE_IOS_BUILD_NUMBER:-$(git -C "${ROOT_DIR}" rev-list --count HEAD)}"
CANDIDATE_EVIDENCE_REF="${WEAVE_CANDIDATE_EVIDENCE_REF:-local:${CANDIDATE_COMMIT}}"

fail() {
  echo "dogfood iOS smoke failed: $*" >&2
  exit 1
}

[[ -n "${DEVICE_ID}" ]] || fail "set WEAVE_IOS_DEVICE_ID to the paired iPhone device identifier"
[[ "${ORDINARY_STARTUP}" == "1" || -n "${DEEPLINK}" ]] || fail "set WEAVE_DOGFOOD_DEEPLINK to the current weave://join URL"
[[ "${CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || fail "WEAVE_CANDIDATE_COMMIT must be the exact lowercase source SHA"
[[ "${BUILD_NUMBER}" =~ ^[1-9][0-9]*$ ]] || fail "WEAVE_IOS_BUILD_NUMBER must be a positive integer"
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

[[ "$(git -C "${ROOT_DIR}" rev-parse HEAD)" == "${CANDIDATE_COMMIT}" ]] || fail "candidate does not match checked-out source"
[[ -z "$(git -C "${ROOT_DIR}" status --porcelain --untracked-files=no)" ]] || fail "physical preparation requires unchanged tracked candidate source"
if [[ "${ORDINARY_STARTUP}" == "1" && "${RESET_MODE}" != "update_in_place" ]]; then
  fail "ordinary startup requires update_in_place; owner state must be preserved"
fi
PLATFORM_CONFIG_URL="$(ORDINARY_STARTUP="${ORDINARY_STARTUP}" PYTHONPATH="${ROOT_DIR}/tools" ORGANIZATION_ORIGIN="${ORGANIZATION_ORIGIN}" DEEPLINK="${DEEPLINK}" python3 - <<'PYTHON'
import os
from urllib.parse import urlencode
from dogfood_handoff_bundle import canonical_origin
origin = canonical_origin(os.environ["ORGANIZATION_ORIGIN"])
if os.environ["ORDINARY_STARTUP"] != "1" and os.environ["DEEPLINK"] != "weave://join?" + urlencode({"organization_origin": origin}):
    raise SystemExit("physical preparation requires one canonical organization-origin link")
print(origin + "api/platform/config")
PYTHON
)"
LOCAL_DOGFOOD_TLS_REQUIRED="${WEAVE_DOGFOOD_LOCAL_CA_REQUIRED:-1}"
[[ "${LOCAL_DOGFOOD_TLS_REQUIRED}" == "0" || "${LOCAL_DOGFOOD_TLS_REQUIRED}" == "1" ]] || fail "invalid local CA requirement"
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
  "${FLUTTER_BIN}" build ios "--${BUILD_MODE}" --target=lib/main.dart \
    --build-number="${BUILD_NUMBER}" \
    --dart-define="WEAVE_CANDIDATE_COMMIT=${CANDIDATE_COMMIT}" \
    --dart-define="WEAVE_CANDIDATE_EVIDENCE_REF=${CANDIDATE_EVIDENCE_REF}" \
    --dart-define=WEAVE_BUILD_CHANNEL=development-dogfood
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
if payload["bundleId"] != payload["expectedBundleId"] or not all(payload[key] for key in (
    "teamIdMatches", "developerCertificateTeamIdMatches", "profileApplicationIdentifierMatches")):
    raise SystemExit("Approved physical signing identity mismatch")
output_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  if [[ "${RESET_MODE}" == "destructive_uninstall" ]]; then
    xcrun devicectl device uninstall app --device "${DEVICE_ID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
  fi
  xcrun devicectl device install app --device "${DEVICE_ID}" build/ios/iphoneos/Runner.app
  xcrun devicectl device info apps --device "${DEVICE_ID}" --bundle-id "${BUNDLE_ID}" --json-output "${EVIDENCE_DIR}/ios-installed-app.json" >/dev/null
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${EVIDENCE_DIR}/ios-launch-started-at.txt"
  if [[ "${ORDINARY_STARTUP}" == "1" ]]; then
    xcrun devicectl device process launch --device "${DEVICE_ID}" --terminate-existing "${BUNDLE_ID}"
  else
    xcrun devicectl device process launch --device "${DEVICE_ID}" --terminate-existing --payload-url "${RUN_DEEPLINK}" "${BUNDLE_ID}"
  fi
)

if [[ "${ORDINARY_STARTUP}" == "1" ]]; then
  EVIDENCE_DIR="${EVIDENCE_DIR}" CANDIDATE_COMMIT="${CANDIDATE_COMMIT}" BUILD_NUMBER="${BUILD_NUMBER}" python3 - <<'PY'
import json, os
from pathlib import Path
root = Path(os.environ["EVIDENCE_DIR"])
installed = json.loads((root / "ios-installed-app.json").read_text())["result"]["apps"]
matching = [app for app in installed if app.get("bundleIdentifier") == "com.massimotter.weave"]
if len(matching) != 1 or str(matching[0].get("bundleVersion")) != os.environ["BUILD_NUMBER"]:
    raise SystemExit("Installed ordinary build identity mismatch")
(root / "ios-ordinary-startup.json").write_text(json.dumps({
    "schemaVersion": "weave.dogfood.ios-ordinary-startup/v1",
    "candidateCommit": os.environ["CANDIDATE_COMMIT"], "buildNumber": os.environ["BUILD_NUMBER"],
    "status": "installed-launch-requested", "functionalProof": "pending",
    "installResetMode": "update_in_place", "onboardingUrlLaunched": False,
    "supportSafe": True, "containsSecretValues": False,
}, indent=2) + "\n")
PY
  echo "DOGFOOD_ORDINARY_STARTUP_RESULT status=installed-launch-requested functionalProof=pending"
  exit 0
fi

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
  "candidateCommit": "${CANDIDATE_COMMIT}",
  "specRevision": "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["specCorpus"]["gitCommit"])' "${ROOT_DIR}/specs/weave-specs.lock.json")",
  "buildNumber": "${BUILD_NUMBER}",
  "candidateEvidenceRef": "${CANDIDATE_EVIDENCE_REF}",
  "bundleId": "${BUNDLE_ID}",
  "deviceId": "${DEVICE_ID}",
  "installTransport": "${INSTALL_TRANSPORT}",
  "installResetMode": "${RESET_MODE}",
  "usedDestructiveUninstall": $([[ "${RESET_MODE}" == "destructive_uninstall" ]] && echo true || echo false),
  "dogfoodAppStateResetRequested": $([[ "${RESET_MODE}" == "app_state" ]] && echo true || echo false),
  "expectedTeamId": "${EXPECTED_TEAM_ID}",
  "expectedDeveloperCertificateTeamId": "${EXPECTED_DEVELOPER_CERT_TEAM_ID}",
  "deeplinkLaunchAttempted": true,
  "visibleStateRequired": "${EXPECTED_VISIBLE_STATE}",
  "authStateRequired": "${EXPECTED_AUTH_STATE}",
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Continue from the visible handoff-ready screen into SSO and landing."
}
JSON

PYTHONPATH="${ROOT_DIR}/tools" EVIDENCE_DIR="${EVIDENCE_DIR}" SCRIPT_ROOT="${ROOT_DIR}" CANDIDATE_COMMIT="${CANDIDATE_COMMIT}" RUN_ID="${WEAVE_DOGFOOD_RUN_ID:-}" HANDOFF_REF="${WEAVE_DOGFOOD_HANDOFF_REF:-}" python3 - <<'PYTHON'
import json, os, plistlib
from datetime import datetime
from pathlib import Path
from dogfood_handoff_bundle import validate_device_preferences
root = Path(os.environ["SCRIPT_ROOT"])
evidence = Path(os.environ["EVIDENCE_DIR"])
spec = json.loads((root / "specs/weave-specs.lock.json").read_text())["specCorpus"]["gitCommit"]
started = datetime.fromisoformat((evidence / "ios-launch-started-at.txt").read_text().strip().replace("Z", "+00:00"))
bundle = json.loads((evidence / "handoff.json").read_text())
if bundle.get("runId") != os.environ["RUN_ID"] or bundle["testerVisible"].get("activationInviteRef") != os.environ["HANDOFF_REF"]:
    raise SystemExit("preparation run/handoff does not match current bundle")
validate_device_preferences(plistlib.loads((evidence / "appdata/com.massimotter.weave.plist").read_bytes()),
                            bundle, started,
                            os.environ["CANDIDATE_COMMIT"], spec)
print("ios_visible_state=handoff_ready")
print("ios_auth_state=ready_for_sso")
PYTHON

cat > "${EVIDENCE_DIR}/ios-deeplink-smoke.json" <<JSON
{
  "schemaVersion": "weave.dogfood.ios-deeplink-smoke.v1",
  "buildMode": "${BUILD_MODE}",
  "candidateCommit": "${CANDIDATE_COMMIT}",
  "specRevision": "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["specCorpus"]["gitCommit"])' "${ROOT_DIR}/specs/weave-specs.lock.json")",
  "buildNumber": "${BUILD_NUMBER}",
  "candidateEvidenceRef": "${CANDIDATE_EVIDENCE_REF}",
  "runId": "${WEAVE_DOGFOOD_RUN_ID:-}",
  "handoffRef": "${WEAVE_DOGFOOD_HANDOFF_REF:-}",
  "organizationOrigin": "${ORGANIZATION_ORIGIN}",
  "freshDeviceDiscoveryVerified": true,
  "physicalDeviceHttpsVerified": true,
  "bundleId": "${BUNDLE_ID}",
  "deviceId": "${DEVICE_ID}",
  "installTransport": "${INSTALL_TRANSPORT}",
  "installResetMode": "${RESET_MODE}",
  "usedDestructiveUninstall": $([[ "${RESET_MODE}" == "destructive_uninstall" ]] && echo true || echo false),
  "dogfoodAppStateResetRequested": $([[ "${RESET_MODE}" == "app_state" ]] && echo true || echo false),
  "expectedTeamId": "${EXPECTED_TEAM_ID}",
  "expectedDeveloperCertificateTeamId": "${EXPECTED_DEVELOPER_CERT_TEAM_ID}",
  "deeplinkLaunchAttempted": true,
  "visibleStateRequired": "${EXPECTED_VISIBLE_STATE}",
  "authStateRequired": "${EXPECTED_AUTH_STATE}",
  "rawLaunchIsNotSufficient": true,
  "requiredFollowUp": "Continue from the visible handoff-ready screen into SSO and landing."
}
JSON

echo "ios_install_evidence=${EVIDENCE_DIR}/ios-installed-app.json"
echo "ios_signing_evidence=${EVIDENCE_DIR}/ios-signing-evidence.json"
echo "ios_smoke_evidence=${EVIDENCE_DIR}/ios-deeplink-smoke.json"
echo "ios_preferences_evidence=${EVIDENCE_DIR}/ios-app-preferences.txt"
