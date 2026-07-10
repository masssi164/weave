#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EVIDENCE_DIR="${WEAVE_DOGFOOD_EVIDENCE_DIR:-${ROOT_DIR}/build/dogfood/iphone-entry}"
DEVICE_ID="${WEAVE_IOS_DEVICE_ID:-}"
BUILD_MODE="${WEAVE_IOS_BUILD_MODE:-profile}"
RESET_MODE="${WEAVE_IOS_RESET_MODE:-update_in_place}"
INSTALL_TRANSPORT="${WEAVE_IOS_INSTALL_TRANSPORT:-wifi}"
LOCAL_CA_TRUST_STATUS="${WEAVE_IOS_LOCAL_CA_TRUST_STATUS:-not_verified}"
RUN_ID="${WEAVE_DOGFOOD_RUN_ID:-s32-massimo-dogfood}"
HANDOFF_REF="${WEAVE_DOGFOOD_HANDOFF_REF:-handoff-s32-massimo-dogfood-home}"
PRODUCT_BASE_URL="${WEAVE_DOGFOOD_PRODUCT_BASE_URL:-https://weave.test:44443}"
API_BASE_URL="${WEAVE_DOGFOOD_API_BASE_URL:-https://api.weave.test:44443}"
PLATFORM_CONFIG_URL="${WEAVE_DOGFOOD_PLATFORM_CONFIG_URL:-${API_BASE_URL}/api/platform/config}"
ACTION="run"

usage() {
  cat <<'USAGE'
Usage: tools/dogfood_iphone_entry.sh [--check|--dry-run|--run] [options]

Prepare and, in --run mode, install/launch the current dogfood Weave build on a
paired iPhone using the support-safe dogfood handoff.

Options:
  --check                       Verify local tools, handoff generation, and device reachability only.
  --dry-run                     Print the exact physical smoke command without installing or launching.
  --run                         Generate handoff, build profile/release iOS app, install, and launch.
  --device-id ID                Paired iPhone identifier. Defaults to WEAVE_IOS_DEVICE_ID.
  --build-mode profile|release  iOS build mode. Debug is rejected. Default: profile.
  --reset-mode update_in_place|app_state|destructive_uninstall
                                Default: update_in_place.
  --transport wifi|usb|unknown  Evidence label for the intended install transport. Default: wifi.
  --local-ca-trust STATUS       trusted, manual_pending, publicly_trusted, not_required, or not_verified.
  --run-id ID                   Dogfood evidence run id.
  --handoff-ref REF             Support-safe handoff/invite reference.
  --product-base-url URL        Dogfood base URL. Default: https://weave.test:44443.
  --api-base-url URL            Dogfood API URL. Default: https://api.weave.test:44443.
  --platform-config-url URL     Platform config URL. Default: API_BASE_URL/api/platform/config.
  --evidence-dir DIR            Evidence output directory.
  -h, --help                    Show this help.

Common failures:
  DEVICE_ID_REQUIRED            Set --device-id or WEAVE_IOS_DEVICE_ID.
  DEVICE_UNAVAILABLE_OR_LOCKED  Plug in/unlock/trust the iPhone or enable Wi-Fi pairing.
  PHYSICAL_DEVICE_TLS_PENDING   Fully trust the Weave local CA or use a publicly trusted endpoint.
USAGE
}

fail() {
  echo "dogfood iPhone entry failed: $*" >&2
  exit 1
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --check)
      ACTION="check"
      shift
      ;;
    --dry-run)
      ACTION="dry-run"
      shift
      ;;
    --run)
      ACTION="run"
      shift
      ;;
    --device-id)
      DEVICE_ID="${2:-}"
      shift 2
      ;;
    --build-mode)
      BUILD_MODE="${2:-}"
      shift 2
      ;;
    --reset-mode)
      RESET_MODE="${2:-}"
      shift 2
      ;;
    --transport)
      INSTALL_TRANSPORT="${2:-}"
      shift 2
      ;;
    --local-ca-trust)
      LOCAL_CA_TRUST_STATUS="${2:-}"
      shift 2
      ;;
    --run-id)
      RUN_ID="${2:-}"
      shift 2
      ;;
    --handoff-ref)
      HANDOFF_REF="${2:-}"
      shift 2
      ;;
    --product-base-url)
      PRODUCT_BASE_URL="${2:-}"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="${2:-}"
      PLATFORM_CONFIG_URL="${API_BASE_URL}/api/platform/config"
      shift 2
      ;;
    --platform-config-url)
      PLATFORM_CONFIG_URL="${2:-}"
      shift 2
      ;;
    --evidence-dir)
      EVIDENCE_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ "${BUILD_MODE}" == "profile" || "${BUILD_MODE}" == "release" ]] \
  || fail "WEAVE_IOS_BUILD_MODE must be profile or release; debug is not valid dogfood evidence"
[[ "${RESET_MODE}" == "update_in_place" || "${RESET_MODE}" == "app_state" || "${RESET_MODE}" == "destructive_uninstall" ]] \
  || fail "WEAVE_IOS_RESET_MODE must be update_in_place, app_state, or destructive_uninstall"
[[ "${INSTALL_TRANSPORT}" == "wifi" || "${INSTALL_TRANSPORT}" == "usb" || "${INSTALL_TRANSPORT}" == "unknown" ]] \
  || fail "WEAVE_IOS_INSTALL_TRANSPORT must be wifi, usb, or unknown"
[[ "${LOCAL_CA_TRUST_STATUS}" == "trusted" || "${LOCAL_CA_TRUST_STATUS}" == "manual_pending" || "${LOCAL_CA_TRUST_STATUS}" == "publicly_trusted" || "${LOCAL_CA_TRUST_STATUS}" == "not_required" || "${LOCAL_CA_TRUST_STATUS}" == "not_verified" ]] \
  || fail "WEAVE_IOS_LOCAL_CA_TRUST_STATUS must be trusted, manual_pending, publicly_trusted, not_required, or not_verified"
[[ -n "${DEVICE_ID}" ]] || fail "DEVICE_ID_REQUIRED: set --device-id or WEAVE_IOS_DEVICE_ID to the paired iPhone identifier"

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
if [[ "${LOCAL_DOGFOOD_TLS_REQUIRED}" == "1" ]]; then
  case "${LOCAL_CA_TRUST_STATUS}" in
    trusted|publicly_trusted) ;;
    manual_pending)
      fail "PHYSICAL_DEVICE_TLS_PENDING: install the Weave Local Development CA profile on the iPhone, enable full trust, then rerun with --local-ca-trust trusted"
      ;;
    *)
      fail "PHYSICAL_DEVICE_TLS_PENDING: local dogfood TLS requires confirmed iPhone CA trust; rerun with --local-ca-trust trusted or use a publicly trusted endpoint"
      ;;
  esac
fi

command -v python3 >/dev/null || fail "python3 was not found"
command -v xcrun >/dev/null || fail "xcrun was not found; install Xcode command line tools"
FLUTTER_BIN="${FLUTTER_BIN:-${HOME}/flutter/bin/flutter}"
[[ -x "${FLUTTER_BIN}" ]] || FLUTTER_BIN="$(command -v flutter || true)"
[[ -n "${FLUTTER_BIN}" && -x "${FLUTTER_BIN}" ]] || fail "flutter was not found; set FLUTTER_BIN"

mkdir -p "${EVIDENCE_DIR}"
EVIDENCE_DIR="$(cd "${EVIDENCE_DIR}" && pwd)"

python3 "${ROOT_DIR}/tools/dogfood_handoff_bundle.py" \
  --run-id "${RUN_ID}" \
  --handoff-ref "${HANDOFF_REF}" \
  --product-base-url "${PRODUCT_BASE_URL}" \
  --platform-config-url "${PLATFORM_CONFIG_URL}" \
  --output-dir "${EVIDENCE_DIR}" >/dev/null

DEEPLINK="$(python3 - "${EVIDENCE_DIR}/handoff.json" <<'PY'
import json
import sys
from pathlib import Path

payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
print(payload["testerVisible"]["deepLink"])
PY
)"
[[ -n "${DEEPLINK}" ]] || fail "handoff generation did not produce a deeplink"

xcrun devicectl device info details \
  --device "${DEVICE_ID}" \
  --timeout "${WEAVE_IOS_DEVICE_REACHABILITY_TIMEOUT_SECONDS:-30}" \
  --json-output "${EVIDENCE_DIR}/iphone-device-details.json" >/dev/null \
  || fail "DEVICE_UNAVAILABLE_OR_LOCKED: iPhone ${DEVICE_ID} is unreachable, locked, not trusted, or not paired for this transport"

export FLUTTER_BIN
export WEAVE_IOS_DEVICE_ID="${DEVICE_ID}"
export WEAVE_IOS_BUILD_MODE="${BUILD_MODE}"
export WEAVE_IOS_RESET_MODE="${RESET_MODE}"
export WEAVE_IOS_INSTALL_TRANSPORT="${INSTALL_TRANSPORT}"
export WEAVE_IOS_LOCAL_CA_TRUST_STATUS="${LOCAL_CA_TRUST_STATUS}"
export WEAVE_DOGFOOD_DEEPLINK="${DEEPLINK}"
export WEAVE_DOGFOOD_PLATFORM_CONFIG_URL="${PLATFORM_CONFIG_URL}"
export WEAVE_DOGFOOD_EVIDENCE_DIR="${EVIDENCE_DIR}"

case "${ACTION}" in
  check)
    echo "DOGFOOD_IPHONE_ENTRY_CHECK_READY deviceId=${DEVICE_ID} buildMode=${BUILD_MODE} resetMode=${RESET_MODE} evidence=${EVIDENCE_DIR}/handoff.json"
    ;;
  dry-run)
    echo "WEAVE_IOS_DEVICE_ID='${DEVICE_ID}' WEAVE_IOS_BUILD_MODE='${BUILD_MODE}' WEAVE_IOS_RESET_MODE='${RESET_MODE}' WEAVE_IOS_INSTALL_TRANSPORT='${INSTALL_TRANSPORT}' WEAVE_IOS_LOCAL_CA_TRUST_STATUS='${LOCAL_CA_TRUST_STATUS}' WEAVE_DOGFOOD_EVIDENCE_DIR='${EVIDENCE_DIR}' WEAVE_DOGFOOD_PLATFORM_CONFIG_URL='${PLATFORM_CONFIG_URL}' WEAVE_DOGFOOD_DEEPLINK='<support-safe handoff from ${EVIDENCE_DIR}/handoff.json>' tools/dogfood_ios_deeplink_smoke.sh"
    ;;
  run)
    "${ROOT_DIR}/tools/dogfood_ios_deeplink_smoke.sh"
    echo "DOGFOOD_IPHONE_ENTRY_LAUNCHED evidence=${EVIDENCE_DIR}/ios-deeplink-smoke.json"
    ;;
esac
