#!/usr/bin/env python3
"""Create a deterministic, support-safe immutable candidate manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--commit", required=True)
    parser.add_argument("--spec-digest", required=True)
    parser.add_argument("--build-evidence-ref", required=True)
    parser.add_argument(
        "--image",
        action="append",
        nargs=4,
        metavar=("COMPONENT", "REFERENCE", "SBOM_DIGEST", "PROVENANCE_DIGEST"),
        required=True,
    )
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    images = [
        {
            "component": component,
            "reference": reference,
            "sbomDigest": sbom_digest,
            "provenanceDigest": provenance_digest,
        }
        for component, reference, sbom_digest, provenance_digest in args.image
    ]
    payload = {
        "schemaVersion": "weave.release.candidate-manifest.v1",
        "supportSafe": True,
        "commit": args.commit,
        "specDigest": args.spec_digest,
        "buildEvidenceRef": args.build_evidence_ref,
        "images": sorted(images, key=lambda image: image["component"]),
    }
    serialized = json.dumps(
        payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_bytes(serialized)
    os.chmod(temporary, 0o600)
    temporary.replace(args.output)
    digest = hashlib.sha256(serialized).hexdigest()
    digest_path = args.output.with_suffix(args.output.suffix + ".sha256")
    digest_path.write_text(f"{digest}  {args.output.name}\n", encoding="ascii")
    os.chmod(digest_path, 0o600)
    subprocess.run(
        [
            sys.executable,
            str(Path(__file__).with_name("candidate-manifest-check.py")),
            "--manifest",
            str(args.output),
        ],
        check=True,
    )
    print(
        f"WEAVE_CANDIDATE_MANIFEST_CREATED manifest={args.output} sha256={digest}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
