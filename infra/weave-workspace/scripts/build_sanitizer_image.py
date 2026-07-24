#!/usr/bin/env python3
"""Build the exact-candidate protected Keycloak sanitizer image."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")


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
        raise SystemExit("WEAVE_SANITIZER_BUILD_ERROR candidate commit must be an exact lowercase SHA")
    tag = f"weave-keycloak-sanitizer:{candidate}"
    subprocess.run(
        [
            "docker", "build",
            "--file", str(root / "keycloak/Dockerfile.sanitizer"),
            "--label", f"org.opencontainers.image.revision={candidate}",
            "--label", "com.massimotter.weave.component=keycloak-admin-sanitizer",
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
        raise SystemExit("WEAVE_SANITIZER_BUILD_ERROR Docker returned an invalid immutable image ID")
    evidence = {
        "schemaVersion": "weave.keycloak-sanitizer-image.v1",
        "candidateCommit": candidate,
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
