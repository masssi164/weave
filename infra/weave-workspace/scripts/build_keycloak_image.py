#!/usr/bin/env python3
"""Build and verify the version-pinned Weave Keycloak runtime image."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_ID = DIGEST
OCI_REFERENCE = re.compile(r"^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$")
BASE_ARGUMENT = "WEAVE_KEYCLOAK_BASE"
PROVIDER_ID = "weave-workload-client-registration-enforcer"
PROVIDER_JAR = (
    "keycloak-workload-registration-provider/build/libs/"
    "weave-workload-client-registration-provider-1.0.0.jar"
)
SOURCE_PATHS = (
    ".dockerignore",
    "settings.gradle",
    "gradle/libs.versions.toml",
    "keycloak-workload-registration-provider",
    "infra/weave-workspace/keycloak/Dockerfile.runtime",
    "infra/weave-workspace/scripts/build_keycloak_image.py",
)
REQUIRED_LABELS = {
    "org.opencontainers.image.title": "Weave Keycloak Runtime",
    "org.opencontainers.image.source": "https://github.com/masssi164/weave",
    "org.opencontainers.image.licenses": "Apache-2.0",
    "org.opencontainers.image.vendor": "Weave",
    "com.massimotter.weave.module": "keycloak-runtime",
    "com.massimotter.weave.runtime-user": "1000:1000",
    "com.massimotter.weave.dependency-platform": "keycloak-26.7-client-policy-spi",
    "com.massimotter.weave.provider-id": PROVIDER_ID,
}


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


def pinned_base(dockerfile: Path) -> str:
    matches = re.findall(
        rf"^ARG {BASE_ARGUMENT}=([^\s]+)$",
        dockerfile.read_text(encoding="utf-8"),
        flags=re.MULTILINE,
    )
    if len(matches) != 1 or not OCI_REFERENCE.fullmatch(matches[0]):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR runtime Dockerfile must pin one exact Keycloak OCI digest"
        )
    return matches[0]


def default_spec_digest(repository: Path) -> str:
    lock = repository / "specs/weave-specs.lock.json"
    return "sha256:" + hashlib.sha256(lock.read_bytes()).hexdigest()


def exact_source(repository: Path, candidate: str) -> None:
    head = subprocess.run(
        ["git", "-C", str(repository), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if head != candidate:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR selected candidate is not the checked-out source commit"
        )
    changed = subprocess.run(
        [
            "git",
            "-C",
            str(repository),
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
            "--",
            *SOURCE_PATHS,
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout
    if changed:
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR runtime build inputs differ from the selected candidate commit"
        )


def inspect_image(reference: str) -> dict[str, object]:
    return json.loads(
        subprocess.run(
            ["docker", "image", "inspect", reference, "--format", "{{json .}}"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate-commit")
    parser.add_argument("--spec-digest")
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
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR candidate commit must be an exact lowercase SHA"
        )
    spec_digest = args.spec_digest or default_spec_digest(repository)
    if not DIGEST.fullmatch(spec_digest):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR specification digest must be an exact sha256 digest"
        )
    exact_source(repository, candidate)

    dockerfile = repository / "infra/weave-workspace/keycloak/Dockerfile.runtime"
    keycloak_base = pinned_base(dockerfile)
    subprocess.run(
        [
            str(repository / "gradlew"),
            "--no-daemon",
            ":keycloak-workload-registration-provider:clean",
            ":keycloak-workload-registration-provider:jar",
            "--console=plain",
        ],
        cwd=repository,
        check=True,
        stdout=subprocess.DEVNULL,
    )
    provider_jar = repository / PROVIDER_JAR
    if not provider_jar.is_file() or provider_jar.is_symlink():
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR provider artifact is missing or is not a regular file"
        )
    provider_digest = "sha256:" + hashlib.sha256(provider_jar.read_bytes()).hexdigest()
    created = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    tag = f"weave-keycloak-runtime:{candidate}"
    build_arguments = {
        "WEAVE_KEYCLOAK_BASE": keycloak_base,
        "WEAVE_IMAGE_CREATED": created,
        "WEAVE_IMAGE_REVISION": candidate,
        "WEAVE_IMAGE_VERSION": f"candidate-{candidate[:12]}",
        "WEAVE_SPEC_DIGEST": spec_digest,
        "WEAVE_SBOM_REFERENCE": "local-build-pending-candidate-publication",
        "WEAVE_PROVENANCE_REFERENCE": "local-build-pending-candidate-publication",
    }
    command = [
        "docker",
        "build",
        "--file",
        str(dockerfile),
        "--tag",
        tag,
    ]
    for key, value in build_arguments.items():
        command.extend(["--build-arg", f"{key}={value}"])
    command.append(str(repository))
    subprocess.run(command, check=True)

    inspected = inspect_image(tag)
    image_id = str(inspected.get("Id", ""))
    labels = ((inspected.get("Config") or {}).get("Labels") or {})
    if not IMAGE_ID.fullmatch(image_id):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR Docker returned an invalid immutable image ID"
        )
    expected_labels = {
        **REQUIRED_LABELS,
        "org.opencontainers.image.revision": candidate,
        "com.massimotter.weave.spec-digest": spec_digest,
    }
    if any(labels.get(key) != value for key, value in expected_labels.items()):
        raise SystemExit(
            "WEAVE_KEYCLOAK_BUILD_ERROR runtime image labels do not bind the exact candidate"
        )
    subprocess.run(
        [
            "docker",
            "run",
            "--rm",
            "--entrypoint",
            "/bin/sh",
            image_id,
            "-ec",
            "test -r /opt/keycloak/providers/weave-workload-client-registration-provider.jar",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )

    evidence = {
        "schemaVersion": "weave.keycloak-runtime-image.v3",
        "candidateCommit": candidate,
        "specDigest": spec_digest,
        "keycloakVersion": "26.7.0",
        "keycloakBaseResolved": keycloak_base,
        "providerId": PROVIDER_ID,
        "providerJarDigest": provider_digest,
        "imageId": image_id,
        "tag": tag,
        "labelsVerified": True,
        "providerArtifactVerified": True,
        "containsSecretValues": False,
        "supportSafe": True,
    }
    if args.output:
        atomic_write(args.output.resolve(), evidence)
    print(image_id)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
