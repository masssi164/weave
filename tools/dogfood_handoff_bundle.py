#!/usr/bin/env python3
"""Generate support-safe dogfood handoff artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import tempfile
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CERT_DIR = ROOT / "infra/weave-workspace/01-infrastructure/.generated/caddy/certs"


def openssl_fingerprint(path: Path) -> str | None:
    if not path.exists():
        return None
    output = subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-fingerprint", "-sha256"],
        text=True,
    ).strip()
    return output.split("=", 1)[1]


def openssl_fingerprint_pem(pem: bytes) -> str | None:
    if not pem:
        return None
    with tempfile.NamedTemporaryFile() as tmp:
        tmp.write(pem)
        tmp.flush()
        return openssl_fingerprint(Path(tmp.name))


def fetch_pem(url: str) -> bytes | None:
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return response.read()
    except Exception:
        return None


def live_leaf_pem(host: str, connect_host: str, port: int) -> bytes | None:
    try:
        output = subprocess.check_output(
            [
                "openssl",
                "s_client",
                "-connect",
                f"{connect_host}:{port}",
                "-servername",
                host,
                "-showcerts",
            ],
            input=b"",
            stderr=subprocess.DEVNULL,
            timeout=10,
        )
    except Exception:
        return None

    start = output.find(b"-----BEGIN CERTIFICATE-----")
    end = output.find(b"-----END CERTIFICATE-----", start)
    if start == -1 or end == -1:
        return None
    end += len(b"-----END CERTIFICATE-----")
    return output[start:end] + b"\n"


def openssl_subject(path: Path) -> str | None:
    if not path.exists():
        return None
    return subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-subject"],
        text=True,
    ).strip()


def openssl_subject_pem(pem: bytes | None) -> str | None:
    if not pem:
        return None
    with tempfile.NamedTemporaryFile() as tmp:
        tmp.write(pem)
        tmp.flush()
        return openssl_subject(Path(tmp.name))


def git_commit() -> str:
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_markdown(path: Path, payload: dict[str, object]) -> None:
    tester = payload["testerVisible"]  # type: ignore[index]
    certs = payload["certificates"]  # type: ignore[index]
    ios = payload["iosClient"]  # type: ignore[index]
    lines = [
        "# Weave Dogfood Handoff",
        "",
        f"- Run ID: `{payload['runId']}`",
        f"- Commit: `{payload['stackCommit']}`",
        f"- Web join URL: `{tester['webJoinUrl']}`",
        f"- iOS deeplink: `{tester['deepLink']}`",
        f"- CA URL: `{certs['caUrl']}`",
        f"- CA LAN fallback: `{certs['caLanFallbackUrl']}`",
        f"- CA SHA256: `{certs['caSha256Fingerprint'] or 'unavailable until certs exist locally'}`",
        f"- Leaf SHA256: `{certs['leafSha256Fingerprint'] or 'unavailable until certs exist locally'}`",
        "",
        "## iPhone Checklist",
        "",
        "1. Install/trust the Weave Local Development CA.",
        "2. Install the current Weave iOS profile or release binary.",
        "3. Open the deeplink from Safari or Matrix.",
        "4. Confirm Weave shows the handoff-aware workspace sign-in state.",
        "",
        "## Build Requirement",
        "",
        f"- Installed-client smoke mode: `{ios['requiredBuildMode']}`",
        f"- Debug builds valid for installed-client deeplink smoke: `{ios['debugBuildsAllowed']}`",
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", default="s32-massimo-dogfood")
    parser.add_argument("--handoff-ref", default="handoff-s32-massimo-dogfood-home")
    parser.add_argument("--org", default="massimo-dogfood")
    parser.add_argument("--workspace", default="home")
    parser.add_argument("--profile", default="local-lan-dogfood")
    parser.add_argument("--product-base-url", default="https://weave.test:44443")
    parser.add_argument("--platform-config-url", default="https://api.weave.test:44443/api/platform/config")
    parser.add_argument("--ca-url", default="http://weave.test:44080/weave-local-ca.pem")
    parser.add_argument("--ca-lan-fallback-url", default="http://192.168.178.88:44080/weave-local-ca.pem")
    parser.add_argument("--cert-dir", type=Path, default=DEFAULT_CERT_DIR)
    parser.add_argument("--leaf-host", default="weave.test")
    parser.add_argument("--leaf-connect-host", default="127.0.0.1")
    parser.add_argument("--leaf-port", type=int, default=44443)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/dogfood")
    args = parser.parse_args()

    query = {
        "handoff_ref": args.handoff_ref,
        "org": args.org,
        "workspace": args.workspace,
        "profile": args.profile,
        "run_id": args.run_id,
    }
    deep_link_query = dict(query)
    deep_link_query["product_base_url"] = args.product_base_url
    deep_link_query["platform_config_url"] = args.platform_config_url
    web_join_url = f"{args.product_base_url}/join?{urlencode(query)}"
    deep_link = f"weave://join?{urlencode(deep_link_query)}"

    ca_file = args.cert_dir / "weave-local-ca.pem"
    leaf_file = args.cert_dir / "weave.test.pem"
    live_ca_pem = None if ca_file.exists() else fetch_pem(args.ca_url)
    live_leaf = None if leaf_file.exists() else live_leaf_pem(
        args.leaf_host,
        args.leaf_connect_host,
        args.leaf_port,
    )
    ca_fingerprint = openssl_fingerprint(ca_file) or openssl_fingerprint_pem(
        live_ca_pem or b"",
    )
    leaf_fingerprint = openssl_fingerprint(leaf_file) or openssl_fingerprint_pem(
        live_leaf or b"",
    )
    leaf_subject = openssl_subject(leaf_file) or openssl_subject_pem(live_leaf)
    payload: dict[str, object] = {
        "schemaVersion": "weave.dogfood.handoff-bundle.v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "runId": args.run_id,
        "stackCommit": git_commit(),
        "testerVisible": {
            "webJoinUrl": web_join_url,
            "deepLink": deep_link,
            "platformConfigUrl": args.platform_config_url,
        },
        "certificates": {
            "caUrl": args.ca_url,
            "caLanFallbackUrl": args.ca_lan_fallback_url,
            "caSha256Fingerprint": ca_fingerprint,
            "leafSha256Fingerprint": leaf_fingerprint,
            "leafSubject": leaf_subject,
            "persistedHostDirectory": str(args.cert_dir),
            "fingerprintSource": "host-files"
            if ca_file.exists() and leaf_file.exists()
            else "live-endpoints",
        },
        "iosClient": {
            "bundleId": "com.massimotter.weave",
            "requiredBuildMode": "profile-or-release",
            "debugBuildsAllowed": False,
            "installEvidenceRequired": True,
            "handoffConsumedEvidenceKey": "last_handoff_consumed_v1",
        },
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
