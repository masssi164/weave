#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${ROOT_DIR}/client"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
CANDIDATE_COMMIT="${WEAVE_CANDIDATE_COMMIT:-}"
LANE_CANDIDATE_COMMIT="${WEAVE_LANE_CANDIDATE_COMMIT:-}"
CANDIDATE_EVIDENCE_REF="${WEAVE_CANDIDATE_EVIDENCE_REF:-}"
DISTRIBUTION_RUN_URL="${WEAVE_DISTRIBUTION_RUN_URL:-${CANDIDATE_EVIDENCE_REF}}"
LIVE_E2E_RUN_URL="${WEAVE_LIVE_E2E_RUN_URL:-}"
CANDIDATE_MANIFEST_DIGEST="${WEAVE_CANDIDATE_MANIFEST_DIGEST:-}"
BUILD_NUMBER="${WEAVE_BUILD_NUMBER:-}"
TEAM_ID="${WEAVE_APPLE_TEAM_ID:-KNDHGC2KV6}"
BUNDLE_ID="${WEAVE_BUNDLE_ID:-com.massimotter.weave}"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood/ios-development-fallback}"
DERIVED_DATA_DIR="${CLIENT_DIR}/build/ios-development-fallback"
APP_PATH="${DERIVED_DATA_DIR}/Build/Products/Release-iphoneos/Runner.app"
FALLBACK_ENTITLEMENTS="Runner/RunnerDevelopment.entitlements"

fail() {
  echo "dogfood iOS development fallback failed: $*" >&2
  exit 1
}

[[ -n "${DEVICE_ID}" ]] || fail "set WEAVE_IOS_DEVICE_ID to the paired physical iPhone identifier"
[[ "${CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || fail "WEAVE_CANDIDATE_COMMIT must be a full lowercase commit SHA"
[[ "${LANE_CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] || fail "WEAVE_LANE_CANDIDATE_COMMIT must be a full lowercase dogfood lane SHA"
[[ "${CANDIDATE_MANIFEST_DIGEST}" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "WEAVE_CANDIDATE_MANIFEST_DIGEST must be an immutable SHA-256 digest"
[[ "${BUILD_NUMBER}" =~ ^[1-9][0-9]*$ ]] || fail "WEAVE_BUILD_NUMBER must be a positive integer"
[[ "${BUNDLE_ID}" == "com.massimotter.weave" ]] || fail "the fallback cannot change the stable bundle identifier"
[[ "${TEAM_ID}" == "KNDHGC2KV6" ]] || fail "the fallback cannot change the stable Apple team"

python3 - "${CANDIDATE_EVIDENCE_REF}" "${DISTRIBUTION_RUN_URL}" "${LIVE_E2E_RUN_URL}" <<'PY'
import sys
from urllib.parse import urlparse

for label, raw_value in (
    ("WEAVE_CANDIDATE_EVIDENCE_REF", sys.argv[1]),
    ("WEAVE_DISTRIBUTION_RUN_URL", sys.argv[2]),
    ("WEAVE_LIVE_E2E_RUN_URL", sys.argv[3]),
):
    value = urlparse(raw_value)
    if (
        value.scheme != "https"
        or not value.hostname
        or value.username is not None
        or value.password is not None
        or value.query
        or value.fragment
    ):
        raise SystemExit(f"{label} must be an uncredentialed HTTPS URL without query or fragment")
PY

for command in flutter xcodebuild xcrun codesign jq shasum; do
  command -v "${command}" >/dev/null || fail "${command} was not found"
done

mkdir -p "${EVIDENCE_DIR}"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/weave-ios-development-fallback.XXXXXX")"
trap 'rm -rf "${temporary_dir}"' EXIT

before_apps="${temporary_dir}/before-apps.json"
after_apps="${temporary_dir}/after-apps.json"
signed_entitlements="${temporary_dir}/signed-entitlements.plist"

xcrun devicectl device info apps \
  --device "${DEVICE_ID}" \
  --bundle-id "${BUNDLE_ID}" \
  --json-output "${before_apps}" >/dev/null
installed_before="$(jq -r --arg bundle "${BUNDLE_ID}" \
  '.result.apps | any(.bundleIdentifier == $bundle)' \
  "${before_apps}")"
[[ "${installed_before}" == true || "${installed_before}" == false ]] ||
  fail "the paired device returned an invalid installed-app inventory"

(
  cd "${CLIENT_DIR}"
  flutter build ios \
    --release \
    --no-codesign \
    --build-number="${BUILD_NUMBER}" \
    --dart-define="WEAVE_CANDIDATE_COMMIT=${CANDIDATE_COMMIT}" \
    --dart-define="WEAVE_CANDIDATE_EVIDENCE_REF=${CANDIDATE_EVIDENCE_REF}" \
    --dart-define=WEAVE_BUILD_CHANNEL=stable-signing-fallback

  xcodebuild \
    -quiet \
    -workspace ios/Runner.xcworkspace \
    -scheme Runner \
    -configuration Release \
    -sdk iphoneos \
    -destination generic/platform=iOS \
    -derivedDataPath "${DERIVED_DATA_DIR}" \
    "DEVELOPMENT_TEAM=${TEAM_ID}" \
    CODE_SIGN_STYLE=Automatic \
    "CODE_SIGN_ENTITLEMENTS=${FALLBACK_ENTITLEMENTS}" \
    -allowProvisioningUpdates \
    build
)

[[ -d "${APP_PATH}" ]] || fail "the signed Runner.app was not produced"
[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "${APP_PATH}/Info.plist")" == "${BUNDLE_ID}" ]] ||
  fail "the signed app bundle identifier changed"
[[ "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "${APP_PATH}/Info.plist")" == "${BUILD_NUMBER}" ]] ||
  fail "the signed app build number does not match the candidate"

codesign -d --entitlements :- "${APP_PATH}" >"${signed_entitlements}" 2>/dev/null
if /usr/libexec/PlistBuddy -c 'Print :com.apple.developer.associated-domains' "${signed_entitlements}" >/dev/null 2>&1; then
  fail "the Personal Team fallback must not retain the unsupported Associated Domains entitlement"
fi
expected_keychain_group="${TEAM_ID}.${BUNDLE_ID}"
[[ "$(/usr/libexec/PlistBuddy -c 'Print :keychain-access-groups:0' "${signed_entitlements}")" == "${expected_keychain_group}" ]] ||
  fail "the stable Keychain access group was not preserved"

xcrun devicectl device install app \
  --device "${DEVICE_ID}" \
  "${APP_PATH}" >/dev/null
xcrun devicectl device info apps \
  --device "${DEVICE_ID}" \
  --bundle-id "${BUNDLE_ID}" \
  --json-output "${after_apps}" >/dev/null
jq -e \
  --arg bundle "${BUNDLE_ID}" \
  --arg build "${BUILD_NUMBER}" \
  '.result.apps | any(.bundleIdentifier == $bundle and .bundleVersion == $build)' \
  "${after_apps}" >/dev/null ||
  fail "the physical iPhone does not report the expected in-place build"

xcrun devicectl device process launch \
  --device "${DEVICE_ID}" \
  --terminate-existing \
  "${BUNDLE_ID}" >/dev/null

version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "${APP_PATH}/Info.plist")"
device_ref_hash="$(printf '%s' "${DEVICE_ID}" | shasum -a 256 | awk '{print $1}')"
jq -n \
  --arg commit "${CANDIDATE_COMMIT}" \
  --arg laneCommit "${LANE_CANDIDATE_COMMIT}" \
  --arg evidenceReference "${CANDIDATE_EVIDENCE_REF}" \
  --arg runUrl "${DISTRIBUTION_RUN_URL}" \
  --arg liveE2eRunUrl "${LIVE_E2E_RUN_URL}" \
  --arg candidateManifestDigest "${CANDIDATE_MANIFEST_DIGEST}" \
  --arg githubRunId "${GITHUB_RUN_ID:-local}" \
  --arg deploymentRunId "${WEAVE_DEPLOYMENT_RUN_ID:-local}" \
  --arg version "${version}" \
  --arg buildNumber "${BUILD_NUMBER}" \
  --arg bundleId "${BUNDLE_ID}" \
  --arg deviceRefHash "${device_ref_hash}" \
  --argjson inPlaceUpdate "${installed_before}" \
  '{
    schemaVersion: "weave.ios-dogfood-distribution.v2",
    commit: $commit,
    candidateCommit: $laneCommit,
    laneCandidateCommit: $laneCommit,
    sourceCandidateCommit: $commit,
    candidateManifestDigest: $candidateManifestDigest,
    evidenceReference: $evidenceReference,
    evidenceRefs: [$evidenceReference, $runUrl] | unique,
    runUrl: $runUrl,
    githubRunId: $githubRunId,
    deploymentRunId: $deploymentRunId,
    deploymentRunUrl: $evidenceReference,
    liveE2eRunUrl: $liveE2eRunUrl,
    channel: "stable-signing-fallback",
    ref: "dogfood",
    version: $version,
    buildNumber: $buildNumber,
    bundleId: $bundleId,
    deviceRefHash: ("sha256:" + $deviceRefHash),
    result: "success",
    inPlaceUpdate: $inPlaceUpdate,
    keychainApplicationIdentifierPreserved: $inPlaceUpdate,
    stableKeychainApplicationIdentifier: true,
    associatedDomainsOmittedForPersonalTeam: true,
    sessionContinuityClaimed: false,
    credentialsIncluded: false,
    blockers: [],
    supportSafe: true
  }' >"${EVIDENCE_DIR}/ios-development-fallback.json"

echo "IOS_DEVELOPMENT_FALLBACK_RESULT status=passed lane=${LANE_CANDIDATE_COMMIT:0:12} source=${CANDIDATE_COMMIT:0:12} build=${BUILD_NUMBER} inPlace=${installed_before} keychainIdentity=true supportSafe=true"
echo "ios_development_fallback_evidence=${EVIDENCE_DIR}/ios-development-fallback.json"
