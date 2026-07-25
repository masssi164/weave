#!/usr/bin/env python3
"""Verify local dogfood CA/leaf fingerprints do not rotate unexpectedly."""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CERT_DIR = ROOT / "infra/weave-workspace/.generated/dogfood/tls"


def fingerprint(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"missing certificate: {path}")
    output = subprocess.check_output(
        ["openssl", "x509", "-in", str(path), "-noout", "-fingerprint", "-sha256"],
        text=True,
    ).strip()
    return output.split("=", 1)[1]


def capture(cert_dir: Path) -> dict[str, str]:
    return {
        "caSha256Fingerprint": fingerprint(cert_dir / "ca.pem"),
        "leafSha256Fingerprint": fingerprint(cert_dir / "cert.pem"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cert-dir", type=Path, default=DEFAULT_CERT_DIR)
    parser.add_argument(
        "--recreate-command",
        help="Shell command that recreates/restarts the stack between fingerprint captures.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "build/dogfood/cert-persistence-smoke.json",
    )
    args = parser.parse_args()

    before = capture(args.cert_dir)
    if args.recreate_command:
        subprocess.check_call(args.recreate_command, shell=True, cwd=ROOT)
    after = capture(args.cert_dir)
    ca_stable = before["caSha256Fingerprint"] == after["caSha256Fingerprint"]
    payload = {
        "schemaVersion": "weave.dogfood.cert-persistence-smoke.v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "certDirectory": str(args.cert_dir),
        "recreateCommand": args.recreate_command,
        "before": before,
        "after": after,
        "caStable": ca_stable,
        "leafStable": before["leafSha256Fingerprint"] == after["leafSha256Fingerprint"],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"cert_persistence_evidence={args.output}")
    if not ca_stable:
        raise SystemExit("local dogfood CA rotated unexpectedly")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
