#!/usr/bin/env python3
"""Validate support-safe dogfood trust stability evidence."""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CERT_DIR = ROOT / "infra/weave-workspace/01-infrastructure/.generated/caddy/certs"
SECRET_PATTERN = re.compile(
    r"(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization|bearer\s+|"
    r"client[_-]?secret|password|credential|secretref://)",
    re.IGNORECASE,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Check dogfood local TLS and iOS signing/provisioning stability without "
            "printing secrets or raw profiles."
        )
    )
    parser.add_argument("--handoff-json", type=Path, default=ROOT / "build/dogfood/handoff.json")
    parser.add_argument("--cert-dir", type=Path, default=DEFAULT_CERT_DIR)
    parser.add_argument("--app", type=Path, default=ROOT / "client/build/ios/iphoneos/Runner.app")
    parser.add_argument("--installed-app-json", type=Path, default=ROOT / "build/dogfood/ios-installed-app.json")
    parser.add_argument("--expected-bundle-id", default="com.massimotter.weave")
    parser.add_argument("--expected-team-id", default="KNDHGC2KV6")
    parser.add_argument("--install-transport", choices=["wifi", "usb", "unknown"], default="unknown")
    parser.add_argument(
        "--developer-trust-status",
        choices=["trusted", "blocked_by_device_policy", "not_verified"],
        default="not_verified",
    )
    parser.add_argument(
        "--allow-blocked-device-policy",
        action="store_true",
        help="Emit a blocked result instead of failing when iOS developer trust cannot be automated.",
    )
    args = parser.parse_args()

    handoff = read_json(args.handoff_json)
    cert_result = check_certs(args.cert_dir, handoff)
    signing_result = check_signing(args.app, args.expected_bundle_id, args.expected_team_id)
    install_result = check_installed_app(args.installed_app_json, args.expected_bundle_id)

    result: dict[str, Any] = {
        "schemaVersion": "weave.dogfood.trust-stability.v1",
        "supportSafe": True,
        "bundleId": args.expected_bundle_id,
        "teamId": args.expected_team_id,
        "installTransport": args.install_transport,
        "wifiPreferred": True,
        "usbFallbackOnly": args.install_transport != "usb",
        "localTlsStable": cert_result["stable"],
        "signingStable": signing_result["stable"],
        "installedBundleStable": install_result["stable"],
        "developerTrustStatus": args.developer_trust_status,
        "repeatedDeveloperTrustAllowed": False,
        "trustDomains": {
            "localTls": {
                "purpose": "Trust weave.test and dogfood service HTTPS.",
                "status": "stable" if cert_result["stable"] else "blocked",
            },
            "iosAppSigning": {
                "purpose": "Trust the installed Weave app binary on the iPhone.",
                "status": args.developer_trust_status,
            },
            "appAuthOidc": {
                "purpose": "Trust the AppAuth browser session and OIDC token lifecycle.",
                "status": "separate_session_evidence_required",
            },
        },
        "checks": {
            "certificates": cert_result["summary"],
            "signing": signing_result["summary"],
            "installedApp": install_result["summary"],
        },
    }

    blocked_reasons: list[str] = []
    if not cert_result["stable"]:
        blocked_reasons.append("local TLS CA/leaf fingerprints changed or are unavailable")
    if not signing_result["stable"]:
        blocked_reasons.append("iOS signing identity/profile does not match the expected stable team/bundle")
    if not install_result["stable"]:
        blocked_reasons.append("installed iOS app metadata does not match the expected stable bundle")
    if args.developer_trust_status != "trusted":
        blocked_reasons.append(
            "iOS developer profile trust is not verified; repeated trust prompts remain possible",
        )

    result["blockedReasons"] = blocked_reasons
    assert_support_safe(result)
    print(json.dumps(result, indent=2, sort_keys=True))
    if blocked_reasons:
        print(
            "DOGFOOD_TRUST_STABILITY_BLOCKED "
            f"localTlsStable={result['localTlsStable']} "
            f"signingStable={result['signingStable']} "
            f"developerTrustStatus={args.developer_trust_status}"
        )
        return 0 if args.allow_blocked_device_policy else 2

    print(
        "DOGFOOD_TRUST_STABILITY_RESULT "
        f"localTlsStable={result['localTlsStable']} "
        f"signingStable={result['signingStable']} "
        f"developerTrustStatus={args.developer_trust_status}"
    )
    return 0


def read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise SystemExit(f"{path} must contain a JSON object")
    return data


def check_certs(cert_dir: Path, handoff: dict[str, Any]) -> dict[str, Any]:
    ca_current = cert_fingerprint(cert_dir / "weave-local-ca.pem")
    leaf_current = cert_fingerprint(cert_dir / "weave.test.pem")
    certificates = handoff.get("certificates", {})
    if not isinstance(certificates, dict):
        certificates = {}
    ca_expected = certificates.get("caSha256Fingerprint")
    leaf_expected = certificates.get("leafSha256Fingerprint")
    stable = bool(
        ca_current
        and leaf_current
        and (not ca_expected or ca_current == ca_expected)
        and (not leaf_expected or leaf_current == leaf_expected)
    )
    return {
        "stable": stable,
        "summary": {
            "caPresent": bool(ca_current),
            "leafPresent": bool(leaf_current),
            "matchesHandoffBaseline": bool(stable),
            "fingerprintSource": "host-files",
        },
    }


def cert_fingerprint(path: Path) -> str | None:
    if not path.exists():
        return None
    output = subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-fingerprint", "-sha256"],
        text=True,
    ).strip()
    return output.split("=", 1)[1]


def check_signing(app: Path, expected_bundle_id: str, expected_team_id: str) -> dict[str, Any]:
    provision = app / "embedded.mobileprovision"
    info_plist = app / "Info.plist"
    if not app.exists() or not provision.exists() or not info_plist.exists():
        return {
            "stable": False,
            "summary": {
                "appPresent": app.exists(),
                "embeddedProvisionPresent": provision.exists(),
                "infoPlistPresent": info_plist.exists(),
            },
        }

    info = plistlib.loads(info_plist.read_bytes())
    profile = decode_mobileprovision(provision)
    teams = profile.get("TeamIdentifier", [])
    entitlements = profile.get("Entitlements", {})
    application_identifier = ""
    if isinstance(entitlements, dict):
        application_identifier = str(entitlements.get("application-identifier", ""))
    bundle_id = str(info.get("CFBundleIdentifier", ""))
    stable = (
        bundle_id == expected_bundle_id
        and expected_team_id in teams
        and application_identifier.endswith(f".{expected_bundle_id}")
    )
    return {
        "stable": stable,
        "summary": {
            "appPresent": True,
            "bundleIdMatches": bundle_id == expected_bundle_id,
            "teamIdMatches": expected_team_id in teams,
            "profileApplicationIdentifierMatches": application_identifier.endswith(
                f".{expected_bundle_id}",
            ),
        },
    }


def decode_mobileprovision(path: Path) -> dict[str, Any]:
    output = subprocess.check_output(["security", "cms", "-D", "-i", str(path)])
    data = plistlib.loads(output)
    if not isinstance(data, dict):
        raise SystemExit(f"{path} did not decode to a plist dictionary")
    return data


def check_installed_app(path: Path, expected_bundle_id: str) -> dict[str, Any]:
    data = read_json(path)
    result = data.get("result", {})
    apps = result.get("apps", []) if isinstance(result, dict) else []
    if not isinstance(apps, list) or not apps:
        return {"stable": False, "summary": {"installedAppPresent": False}}
    app = apps[0] if isinstance(apps[0], dict) else {}
    bundle_id = app.get("bundleIdentifier")
    return {
        "stable": bundle_id == expected_bundle_id,
        "summary": {
            "installedAppPresent": True,
            "bundleIdMatches": bundle_id == expected_bundle_id,
            "builtByDeveloper": app.get("builtByDeveloper") is True,
        },
    }


def assert_support_safe(payload: dict[str, Any]) -> None:
    serialized = json.dumps(payload, sort_keys=True)
    if SECRET_PATTERN.search(serialized):
        raise SystemExit("trust stability evidence contains secret-like text")


if __name__ == "__main__":
    sys.exit(main())
