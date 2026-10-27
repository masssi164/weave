#!/usr/bin/env python3
"""Generate support-safe dogfood handoff artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode, urlparse


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "infra/weave-workspace/scripts"))
from compose_env import load_context


def canonical_origin(value: str) -> str:
    if not isinstance(value, str) or any(character.isspace() for character in value):
        raise ValueError("organization origin must not contain whitespace")
    parsed = urlparse(value)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("invalid organization origin port") from exc
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username is not None
            or parsed.password is not None or parsed.path not in ("", "/")
            or parsed.params or parsed.query or parsed.fragment
            or (port is not None and not 1 <= port <= 65535)):
        raise ValueError("organization origin must be credential-free HTTPS with no path, query or fragment")
    return f"https://{parsed.netloc}/"


def bootstrap_context(public_url: str | None = None) -> dict:
    if public_url is not None:
        return {"organizationOrigin": canonical_origin(public_url), "tlsRoot": None,
                "caFile": None, "localCaRequired": False}
    context = load_context("dogfood", ROOT / "infra/weave-workspace")
    ca = context.tls_root / "ca.pem"
    return {"organizationOrigin": canonical_origin(context.env["WEAVE_PUBLIC_URL"]),
            "tlsRoot": str(context.tls_root), "caFile": str(ca), "localCaRequired": True}



def openssl_fingerprint(path: Path) -> str | None:
    if not path.exists():
        return None
    output = subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-fingerprint", "-sha256"],
        text=True,
    ).strip()
    return output.split("=", 1)[1]


def openssl_subject(path: Path) -> str | None:
    if not path.exists():
        return None
    return subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-subject"],
        text=True,
    ).strip()


def git_commit() -> str:
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_markdown(path: Path, payload: dict[str, object]) -> None:
    tester = payload["testerVisible"]  # type: ignore[index]
    certs = payload["certificates"]  # type: ignore[index]
    ios = payload["iosClient"]  # type: ignore[index]
    route = payload["dogfoodRoute"]  # type: ignore[index]
    lines = [
        "# Weave Dogfood Handoff",
        "",
        f"- Run ID: `{payload['runId']}`",
        f"- Commit: `{payload['stackCommit']}`",
        f"- Route mode: `{route['mode']}`",
        f"- Public route recommended: `{route['publicRouteRecommended']}`",
        f"- Web join URL: `{tester['webJoinUrl']}`",
        f"- iOS deeplink: `{tester['deepLink']}`",
        f"- Activation invite ref: `{tester['activationInviteRef']}`",
        f"- Activation secret location: `{tester['activationSecretLocation']}`",
        f"- Configured CA certificate file: `{certs['caFile']}`",
        f"- CA SHA256: `{certs['caSha256Fingerprint'] or 'unavailable until certs exist locally'}`",
        f"- Leaf SHA256: `{certs['leafSha256Fingerprint'] or 'unavailable until certs exist locally'}`",
        "",
        "## iPhone Checklist",
        "",
        "1. Prefer a public HTTPS dogfood route with a publicly trusted certificate for Massimo-facing tests.",
        "2. If using local HTTPS, reuse the same persisted Weave Local Development CA and leaf certificate; do not rotate unless explicitly requested.",
        "3. Use Prepare Human Test to update the qualified Weave iOS binary in place and observe ordinary startup.",
        "4. Open the deeplink from Safari or Matrix.",
        "5. Confirm the normal sign-in screen shows the intended organization; complete ordinary SSO when needed.",
        "",
        "This URL and certificate bundle is informational. It is not qualification or physical-iPhone human acceptance evidence.",
        "",
        "## Public Route Acceptance",
        "",
        f"- Stable base URL required: `{route['stableBaseUrlRequired']}`",
        f"- Publicly trusted HTTPS required for public route: `{route['publiclyTrustedHttpsRequired']}`",
        f"- Support-safe invite links required: `{route['supportSafeInviteLinksRequired']}`",
        f"- Secrets in links allowed: `{route['secretsInLinksAllowed']}`",
        f"- Mailpit/local mail safety still required: `{route['mailpitLocalSafetyRequired']}`",
        "",
        "## Build Requirement",
        "",
        f"- Installed-client smoke mode: `{ios['requiredBuildMode']}`",
        f"- Debug builds valid for installed-client deeplink smoke: `{ios['debugBuildsAllowed']}`",
        f"- Bundle ID: `{ios['bundleId']}`",
        f"- Provisioning Team ID: `{ios['teamId']}`",
        f"- Developer certificate Team ID: `{ios['developerCertificateTeamId']}`",
        f"- Installation mode: `{ios['installMode']}`",
        f"- Destructive uninstall requires explicit opt-in: `{ios['destructiveUninstallRequiresOptIn']}`",
        f"- Wi-Fi install preferred: `{ios['wifiInstallPreferred']}`",
        f"- USB fallback only: `{ios['usbFallbackOnly']}`",
        f"- Repeated developer trust allowed: `{ios['repeatedDeveloperTrustAllowed']}`",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", default="s32-massimo-dogfood")
    parser.add_argument("--handoff-ref", default="handoff-s32-massimo-dogfood-home")
    parser.add_argument("--product-base-url", help="Offline bundle override: one canonical HTTPS organization origin")
    parser.add_argument("--print-context-json", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/dogfood")
    args = parser.parse_args()

    context = bootstrap_context(args.product_base_url)
    if args.print_context_json:
        print(json.dumps(context))
        return 0
    origin = context["organizationOrigin"]
    web_join_url = f"{origin}join"
    deep_link = "weave://join?" + urlencode({"organization_origin": origin})
    route_mode = "local-stable-ca" if context["localCaRequired"] else "public-trusted-https"
    cert_dir = Path(context["tlsRoot"]) if context["tlsRoot"] else None
    ca_file = cert_dir / "ca.pem" if cert_dir else None
    leaf_file = cert_dir / "cert.pem" if cert_dir else None
    ca_fingerprint = openssl_fingerprint(ca_file) if ca_file else None
    leaf_fingerprint = openssl_fingerprint(leaf_file) if leaf_file else None
    leaf_subject = openssl_subject(leaf_file) if leaf_file else None

    payload: dict[str, object] = {
        "schemaVersion": "weave.dogfood.handoff-bundle.v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "runId": args.run_id,
        "stackCommit": git_commit(),
        "specRevision": json.loads((ROOT / "specs/weave-specs.lock.json").read_text())["specCorpus"]["gitCommit"],
        "organizationOrigin": origin,
        "testerVisible": {
            "webJoinUrl": web_join_url,
            "deepLink": deep_link,
            "platformConfigUrl": origin + "api/platform/config",
            "activationInviteRef": args.handoff_ref,
            "activationSecretLocation": "identity-provider-required-action-mail-only",
        },
        "dogfoodRoute": {
            "mode": route_mode,
            "simulatorCertInjectionIsPhysicalProof": False,
            "physicalE2eGate": "physical-or-public-route-e2e",
            "publicRouteRecommended": True,
            "stableBaseUrlRequired": True,
            "publiclyTrustedHttpsRequired": route_mode == "public-trusted-https",
            "localStableCaAllowed": route_mode == "local-stable-ca",
            "localCaRotationRequiresExplicitRequest": True,
            "supportSafeInviteLinksRequired": True,
            "secretsInLinksAllowed": False,
            "rawProviderPayloadsAllowed": False,
            "mailpitLocalSafetyRequired": True,
        },
        "certificates": {
            "caFile": context["caFile"],
            "caSha256Fingerprint": ca_fingerprint,
            "leafSha256Fingerprint": leaf_fingerprint,
            "leafSubject": leaf_subject,
            "persistedHostDirectory": context["tlsRoot"],
            "rotationPolicy": "never_rotate_unless_explicit",
            "physicalIphoneMustAlreadyTrustStableCa": route_mode == "local-stable-ca",
            "fingerprintSource": "host-files" if ca_file and ca_file.exists() and leaf_file.exists() else "unavailable",
        },
        "iosClient": {
            "bundleId": "com.massimotter.weave",
            "teamId": "KNDHGC2KV6",
            "developerCertificateTeamId": "6RUS2Z848X",
            "requiredBuildMode": "profile-or-release",
            "debugBuildsAllowed": False,
            "installEvidenceRequired": True,
            "installMode": "update_in_place",
            "destructiveUninstallRequiresOptIn": True,
            "wifiInstallPreferred": True,
            "usbFallbackOnly": True,
            "repeatedDeveloperTrustAllowed": False,
        },
        "qualificationEvidence": False,
        "supportSafe": True,
    }
    output_json = args.output_dir / "handoff.json"
    output_md = args.output_dir / "handoff.md"
    write_json(output_json, payload)
    write_markdown(output_md, payload)
    print(f"handoff_json={output_json}")
    print(f"handoff_md={output_md}")
    print(f"deep_link_sha256={hashlib.sha256(deep_link.encode()).hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
