#!/usr/bin/env python3
"""Build the pinned Weave Keycloak distribution for an exact candidate."""

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
    repository = args.root.resolve()
    candidate = args.candidate_commit or subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not COMMIT.fullmatch(candidate):
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR candidate commit must be an exact lowercase SHA")
    tag = f"weave-keycloak:{candidate}"
    subprocess.run(
        [
            "docker", "build",
            "--file", str(repository / "infra/keycloak-event-listener/Dockerfile"),
            "--label", f"org.opencontainers.image.revision={candidate}",
            "--tag", tag,
            str(repository / "infra/keycloak-event-listener"),
        ],
        check=True,
    )
    inspected = json.loads(
        subprocess.run(
            ["docker", "image", "inspect", tag, "--format", "{{json .}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout
    )
    image_id = str(inspected.get("Id", ""))
    labels = (inspected.get("Config") or {}).get("Labels") or {}
    if not IMAGE_ID.fullmatch(image_id) or labels.get("com.massimotter.weave.keycloak.version") != "26.7.0":
        raise SystemExit("WEAVE_KEYCLOAK_BUILD_ERROR image provenance is invalid")
    evidence = {
        "schemaVersion": "weave.keycloak-image.v1",
        "candidateCommit": candidate,
        "keycloakVersion": "26.7.0",
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
