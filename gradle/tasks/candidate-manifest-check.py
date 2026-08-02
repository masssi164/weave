#!/usr/bin/env python3
"""Fail-closed validation for immutable dogfood candidate manifests."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import urlsplit

REQUIRED_COMPONENTS = {
    "server",
    "mcp-server",
    "identity-ops",
    "keycloak-runtime",
}
SHA256 = re.compile(r"sha256:[0-9a-f]{64}")
IMAGE = re.compile(r"[^\s@:]+(?:/[^\s@:]+)+@sha256:[0-9a-f]{64}")


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"WEAVE_CANDIDATE_MANIFEST_ERROR {message}")


def support_safe_https(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        parsed = urlsplit(value)
        parsed.port
    except (TypeError, ValueError):
        return False
    return (
        parsed.scheme == "https"
        and bool(parsed.hostname)
        and parsed.username is None
        and parsed.password is None
        and not parsed.query
        and not parsed.fragment
        and "@" not in value
        and "%" not in value
        and "\\" not in value
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()

    raw = args.manifest.read_bytes()
    payload = json.loads(raw)
    if payload.get("schemaVersion") != "weave.release.candidate-manifest.v2":
        fail("unsupported schemaVersion")
    if payload.get("supportSafe") is not True:
        fail("manifest must declare supportSafe=true")
    if not re.fullmatch(r"[0-9a-f]{40}", str(payload.get("commit", ""))):
        fail("commit must be an exact lowercase revision")
    if not re.fullmatch(
        r"[0-9a-f]{40}", str(payload.get("specificationCommit", ""))
    ):
        fail("specificationCommit must be an exact lowercase revision")
    if not SHA256.fullmatch(str(payload.get("specDigest", ""))):
        fail("specDigest must be exact")
    if not support_safe_https(payload.get("buildEvidenceRef")):
        fail("buildEvidenceRef must be a support-safe HTTPS URL")

    images = payload.get("images")
    if not isinstance(images, list):
        fail("images must be a list")
    components: set[str] = set()
    for image in images:
        expected_fields = {
            "component",
            "reference",
            "sbomDigest",
            "provenanceDigest",
        }
        if isinstance(image, dict) and image.get("component") == "keycloak-runtime":
            expected_fields.add("buildEvidenceDigest")
        if not isinstance(image, dict) or set(image) != expected_fields:
            fail("each image must contain only the reviewed identity fields")
        component = image["component"]
        if component in components:
            fail(f"duplicate image component {component}")
        components.add(component)
        if not IMAGE.fullmatch(str(image["reference"])):
            fail(f"{component} image is not pinned by digest")
        if ":latest" in image["reference"]:
            fail(f"{component} image uses latest")
        for field in ("sbomDigest", "provenanceDigest"):
            if not SHA256.fullmatch(str(image[field])):
                fail(f"{component} {field} must be exact")
        if component == "keycloak-runtime" and not SHA256.fullmatch(
            str(image["buildEvidenceDigest"])
        ):
            fail("keycloak-runtime buildEvidenceDigest must be exact")
    if components != REQUIRED_COMPONENTS:
        fail(
            "image set must be exactly " + ", ".join(sorted(REQUIRED_COMPONENTS))
        )

    digest_file = args.manifest.with_suffix(args.manifest.suffix + ".sha256")
    if not digest_file.is_file():
        fail(f"adjacent digest file is missing: {digest_file}")
    expected = digest_file.read_text(encoding="ascii").split()[0]
    actual = hashlib.sha256(raw).hexdigest()
    if expected != actual:
        fail("adjacent manifest digest does not match exact bytes")
    print(f"WEAVE_CANDIDATE_MANIFEST_OK sha256={actual} commit={payload['commit']}")


if __name__ == "__main__":
    main()
