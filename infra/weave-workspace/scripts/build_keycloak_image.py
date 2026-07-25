#!/usr/bin/env python3
"""Resolve the approved stock Keycloak distribution without rebuilding it."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
STOCK_KEYCLOAK_REFERENCE = (
    "quay.io/keycloak/keycloak@"
    "sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13"
)


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
    repository = args.root.resolve()
    candidate = args.candidate_commit or subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not COMMIT.fullmatch(candidate):
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR candidate commit must be an exact lowercase SHA")
    subprocess.run(
        ["docker", "pull", STOCK_KEYCLOAK_REFERENCE],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    inspected = json.loads(
        subprocess.run(
            [
                "docker",
                "image",
                "inspect",
                STOCK_KEYCLOAK_REFERENCE,
                "--format",
                "{{json .}}",
            ],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout
    )
    image_id = str(inspected.get("Id", ""))
    repo_digests = inspected.get("RepoDigests") or []
    if (
        not IMAGE_ID.fullmatch(image_id)
        or STOCK_KEYCLOAK_REFERENCE not in repo_digests
    ):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR stock upstream digest provenance is invalid"
        )
    evidence = {
        "schemaVersion": "weave.stock-keycloak-image.v1",
        "evidenceForCandidateCommit": candidate,
        "keycloakVersion": "26.7.0",
        "upstreamReference": STOCK_KEYCLOAK_REFERENCE,
        "imageId": image_id,
        "containsSecretValues": False,
        "supportSafe": True,
    }
    if args.output:
        atomic_write(args.output.resolve(), evidence)
    print(image_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
