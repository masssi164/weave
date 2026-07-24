#!/usr/bin/env python3
"""Build the rootless one-shot runner from the pinned official Keycloak image."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
OCI_REFERENCE = re.compile(r"^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$")
KEYCLOAK_DEV_REFERENCE = "quay.io/keycloak/keycloak:26.7.0"
UBI9_DEV_REFERENCE = "registry.access.redhat.com/ubi9:latest"


def atomic_write(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, sort_keys=True)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def resolve_oci_reference(reference: str, *, allowed_tag: str, component: str) -> str:
    if OCI_REFERENCE.fullmatch(reference):
        return reference
    if reference != allowed_tag:
        raise SystemExit(
            f"WEAVE_IDENTITY_OPS_BUILD_ERROR {component} must be an OCI digest or the reviewed dev reference {allowed_tag}"
        )
    subprocess.run(["docker", "pull", reference], check=True, stdout=subprocess.DEVNULL)
    digests = json.loads(
        subprocess.run(
            ["docker", "image", "inspect", reference, "--format", "{{json .RepoDigests}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout
    )
    repository = reference.rsplit(":", 1)[0]
    matches = sorted(
        value for value in (digests or [])
        if isinstance(value, str) and value.startswith(repository + "@sha256:")
    )
    if len(matches) != 1 or not OCI_REFERENCE.fullmatch(matches[0]):
        raise SystemExit(f"WEAVE_IDENTITY_OPS_BUILD_ERROR {component} tag did not resolve to one OCI digest")
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--keycloak-base", default=KEYCLOAK_DEV_REFERENCE)
    parser.add_argument("--ubi9-base", default=UBI9_DEV_REFERENCE)
    args = parser.parse_args()
    if not COMMIT.fullmatch(args.candidate_commit):
        raise SystemExit("WEAVE_IDENTITY_OPS_BUILD_ERROR candidate commit must be an exact lowercase SHA")
    root = args.root.resolve()
    tag = f"weave-keycloak-identity-ops:{args.candidate_commit}"
    keycloak_base = resolve_oci_reference(
        args.keycloak_base,
        allowed_tag=KEYCLOAK_DEV_REFERENCE,
        component="Keycloak base",
    )
    ubi9_base = resolve_oci_reference(
        args.ubi9_base,
        allowed_tag=UBI9_DEV_REFERENCE,
        component="UBI9 base",
    )
    subprocess.run(
        [
            "docker", "build",
            "--file", str(root / "keycloak/Dockerfile.identity-ops"),
            "--label", f"org.opencontainers.image.revision={args.candidate_commit}",
            "--label", "com.massimotter.weave.component=keycloak-identity-ops",
            "--build-arg", f"WEAVE_KEYCLOAK_BASE={keycloak_base}",
            "--build-arg", f"WEAVE_UBI9_BASE={ubi9_base}",
            "--tag", tag,
            str(root / "keycloak"),
        ],
        check=True,
    )
    image_id = subprocess.run(
        ["docker", "image", "inspect", tag, "--format", "{{.Id}}"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not IMAGE_ID.fullmatch(image_id):
        raise SystemExit("WEAVE_IDENTITY_OPS_BUILD_ERROR Docker returned an invalid immutable image ID")
    if args.output:
        atomic_write(
            args.output.resolve(),
            {
                "schemaVersion": "weave.identity-ops-image.v2",
                "candidateCommit": args.candidate_commit,
                "keycloakBaseDeclared": args.keycloak_base,
                "keycloakBaseResolved": keycloak_base,
                "ubi9BaseDeclared": args.ubi9_base,
                "ubi9BaseResolved": ubi9_base,
                "imageId": image_id,
                "containsSecretValues": False,
                "supportSafe": True,
            },
        )
    print(image_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
