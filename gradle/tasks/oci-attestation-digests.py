#!/usr/bin/env python3
"""Resolve exact SBOM and provenance layer digests from a BuildKit image index."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any, NoReturn

SHA256 = re.compile(r"sha256:[0-9a-f]{64}")
ATTESTATION_TYPE = "attestation-manifest"
REFERENCE_TYPE = "vnd.docker.reference.type"
REFERENCE_DIGEST = "vnd.docker.reference.digest"
PREDICATE_TYPE = "in-toto.io/predicate-type"
SBOM_PREDICATE = "https://spdx.dev/Document"
PROVENANCE_PREFIX = "https://slsa.dev/provenance/"


def fail(message: str) -> NoReturn:
    raise SystemExit(f"WEAVE_OCI_ATTESTATION_ERROR {message}")


def inspect_raw(reference: str) -> dict[str, Any]:
    completed = subprocess.run(
        ["docker", "buildx", "imagetools", "inspect", reference, "--raw"],
        check=True,
        capture_output=True,
        text=True,
    )
    payload = json.loads(completed.stdout)
    if not isinstance(payload, dict):
        fail("registry returned a non-object manifest")
    return payload


def require_digest(value: object, field: str) -> str:
    digest = str(value or "")
    if not SHA256.fullmatch(digest):
        fail(f"{field} is not an exact SHA-256 digest")
    return digest


def resolve_attestations(
    image: str, root: dict[str, Any]
) -> tuple[str, str, str]:
    manifests = root.get("manifests")
    if not isinstance(manifests, list):
        fail("image reference does not resolve to an attested OCI image index")

    subjects: list[str] = []
    attestation_descriptors: list[dict[str, Any]] = []
    for descriptor in manifests:
        if not isinstance(descriptor, dict):
            fail("image index contains an invalid descriptor")
        annotations = descriptor.get("annotations") or {}
        platform = descriptor.get("platform") or {}
        if annotations.get(REFERENCE_TYPE) == ATTESTATION_TYPE:
            attestation_descriptors.append(descriptor)
        elif platform.get("os") != "unknown" and platform.get("architecture") != "unknown":
            subjects.append(require_digest(descriptor.get("digest"), "subject digest"))

    if len(subjects) != 1:
        fail("candidate images must contain exactly one runnable platform")
    subject_digest = subjects[0]
    matching = [
        descriptor
        for descriptor in attestation_descriptors
        if (descriptor.get("annotations") or {}).get(REFERENCE_DIGEST) == subject_digest
    ]
    if len(matching) != 1:
        fail("image must contain exactly one attestation manifest for its platform")

    repository = image.rsplit("@", 1)[0]
    attestation_digest = require_digest(
        matching[0].get("digest"), "attestation manifest digest"
    )
    attestation = inspect_raw(f"{repository}@{attestation_digest}")
    layers = attestation.get("layers")
    if not isinstance(layers, list):
        fail("attestation manifest has no layers")

    sbom_digests: list[str] = []
    provenance_digests: list[str] = []
    for layer in layers:
        if not isinstance(layer, dict):
            fail("attestation manifest contains an invalid layer")
        predicate = (layer.get("annotations") or {}).get(PREDICATE_TYPE)
        digest = require_digest(layer.get("digest"), "attestation layer digest")
        if predicate == SBOM_PREDICATE:
            sbom_digests.append(digest)
        elif isinstance(predicate, str) and predicate.startswith(PROVENANCE_PREFIX):
            provenance_digests.append(digest)

    if len(sbom_digests) != 1 or len(provenance_digests) != 1:
        fail("image must contain exactly one SPDX SBOM and one SLSA provenance layer")
    return subject_digest, sbom_digests[0], provenance_digests[0]


def write_canonical(path: Path, payload: dict[str, Any]) -> None:
    serialized = json.dumps(
        payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(serialized)
    os.chmod(temporary, 0o600)
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.image.count("@") != 1:
        fail("image must be an exact repository@sha256 reference")
    root_digest = require_digest(args.image.rsplit("@", 1)[1], "image index digest")
    root = inspect_raw(args.image)
    subject_digest, sbom_digest, provenance_digest = resolve_attestations(
        args.image, root
    )
    write_canonical(
        args.output,
        {
            "schemaVersion": "weave.release.oci-attestations.v1",
            "supportSafe": True,
            "image": args.image,
            "imageIndexDigest": root_digest,
            "subjectDigest": subject_digest,
            "sbomDigest": sbom_digest,
            "provenanceDigest": provenance_digest,
        },
    )
    print(
        "WEAVE_OCI_ATTESTATIONS_OK "
        f"imageIndexDigest={root_digest} sbomDigest={sbom_digest} "
        f"provenanceDigest={provenance_digest}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
