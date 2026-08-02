#!/usr/bin/env python3
"""Build the rootless Identity Ops runner from one exact Weave candidate."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
OCI_REFERENCE = re.compile(r"^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$")


def atomic_write(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def pinned_base(dockerfile: Path, name: str) -> str:
    matches = re.findall(
        rf"^ARG {re.escape(name)}=([^\s]+)$",
        dockerfile.read_text(encoding="utf-8"),
        flags=re.MULTILINE,
    )
    if len(matches) != 1 or not OCI_REFERENCE.fullmatch(matches[0]):
        raise SystemExit(
            f"WEAVE_IDENTITY_OPS_BUILD_ERROR {name} must declare one exact OCI digest"
        )
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate-commit")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    repository = root.parents[1]
    candidate = args.candidate_commit or subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not COMMIT.fullmatch(candidate):
        raise SystemExit(
            "WEAVE_IDENTITY_OPS_BUILD_ERROR candidate commit must be an exact lowercase SHA"
        )
    source_paths = (
        "infra/weave-workspace/keycloak/Dockerfile.identity-ops",
        "infra/weave-workspace/keycloak/identity_ops.py",
        "specs/weave-specs.lock.json",
    )
    source_match = subprocess.run(
        ["git", "-C", str(repository), "diff", "--quiet", candidate, "--", *source_paths],
        check=False,
    )
    if source_match.returncode != 0:
        raise SystemExit(
            "WEAVE_IDENTITY_OPS_BUILD_ERROR build inputs differ from the selected candidate commit"
        )
    tag = f"weave-keycloak-identity-ops:{candidate}"
    spec_lock = subprocess.run(
        ["git", "-C", str(repository), "show", f"{candidate}:specs/weave-specs.lock.json"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    spec_digest = "sha256:" + hashlib.sha256(spec_lock).hexdigest()
    dockerfile = root / "keycloak/Dockerfile.identity-ops"
    keycloak_base = pinned_base(dockerfile, "WEAVE_KEYCLOAK_BASE")
    ubi9_base = pinned_base(dockerfile, "WEAVE_UBI9_BASE")
    subprocess.run(
        [
            "docker",
            "build",
            "--file",
            str(dockerfile),
            "--label",
            f"org.opencontainers.image.revision={candidate}",
            "--build-arg",
            f"WEAVE_SPEC_DIGEST={spec_digest}",
            "--build-arg",
            f"WEAVE_KEYCLOAK_BASE={keycloak_base}",
            "--build-arg",
            f"WEAVE_UBI9_BASE={ubi9_base}",
            "--tag",
            tag,
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
        raise SystemExit(
            "WEAVE_IDENTITY_OPS_BUILD_ERROR Docker returned an invalid immutable image ID"
        )
    evidence = {
        "schemaVersion": "weave.identity-ops-image.v2",
        "candidateCommit": candidate,
        "specDigest": spec_digest,
        "keycloakBaseResolved": keycloak_base,
        "ubi9BaseResolved": ubi9_base,
        "imageId": image_id,
        "tag": tag,
        "containsSecretValues": False,
        "supportSafe": True,
    }
    if args.output:
        atomic_write(args.output.resolve(), evidence)
    print(image_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
