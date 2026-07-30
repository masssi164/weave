#!/usr/bin/env python3
"""Verify exact testApp runtime image identity and write support-safe evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_REFERENCE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
COMPONENTS = {"server", "mcp-server", "identity-ops", "keycloak-runtime"}
RUNNING_SERVICES = {
    "server": "backend",
    "mcp-server": "mcp",
    "keycloak-runtime": "keycloak",
}


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"WEAVE_RUNTIME_IMAGE_EVIDENCE_ERROR {message}")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--specification-commit", required=True)
    parser.add_argument("--spec-digest", required=True)
    parser.add_argument("--candidate-manifest-digest", required=True)
    parser.add_argument("--compose-project", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument(
        "--image",
        action="append",
        nargs=3,
        metavar=("COMPONENT", "REQUESTED_REFERENCE", "RESOLVED_IMAGE_ID"),
        required=True,
    )
    return parser.parse_args()


def container_image_id(compose_project: str, service: str) -> str:
    selected = subprocess.run(
        [
            "docker",
            "container",
            "ls",
            "--filter",
            f"label=com.docker.compose.project={compose_project}",
            "--filter",
            f"label=com.docker.compose.service={service}",
            "--format",
            "{{.ID}}",
            "--no-trunc",
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip().splitlines()
    if len(selected) != 1 or not re.fullmatch(r"[0-9a-f]{64}", selected[0]):
        fail(f"{service} does not resolve to one running candidate container")
    image_id = subprocess.run(
        [
            "docker",
            "container",
            "inspect",
            selected[0],
            "--format",
            "{{.Image}}",
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()
    if not DIGEST.fullmatch(image_id):
        fail(f"{service} container has an invalid local image ID")
    return image_id


def read_manifest(path: Path, expected_digest: str) -> dict[str, object]:
    if path.is_symlink() or not path.is_file():
        fail("candidate manifest must be a regular non-symlink file")
    raw = path.read_bytes()
    actual = "sha256:" + hashlib.sha256(raw).hexdigest()
    if actual != expected_digest:
        fail("candidate manifest bytes do not match the supplied digest")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        fail(f"candidate manifest is invalid JSON: {error.msg}")
    return payload


def private_json(path: Path, payload: dict[str, object]) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(
        temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(payload, stream, ensure_ascii=False, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    args = arguments()
    if not COMMIT.fullmatch(args.candidate_commit):
        fail("candidate commit is invalid")
    if not COMMIT.fullmatch(args.specification_commit):
        fail("specification commit is invalid")
    if not DIGEST.fullmatch(args.spec_digest):
        fail("specification digest is invalid")
    if not DIGEST.fullmatch(args.candidate_manifest_digest):
        fail("candidate manifest digest is invalid")
    if not re.fullmatch(r"weave-e2e-[0-9a-f]{16}", args.compose_project):
        fail("compose project is not one isolated testApp namespace")

    supplied = {
        component: (reference, image_id)
        for component, reference, image_id in args.image
    }
    if len(args.image) != len(COMPONENTS) or set(supplied) != COMPONENTS:
        fail("runtime image set must contain each candidate component exactly once")
    for component, (_, image_id) in supplied.items():
        if not DIGEST.fullmatch(image_id):
            fail(f"{component} resolved image ID is invalid")

    manifest_bound = args.manifest is not None
    manifest_references: dict[str, str] = {}
    if manifest_bound:
        manifest = read_manifest(args.manifest, args.candidate_manifest_digest)
        if (
            manifest.get("schemaVersion")
            != "weave.release.candidate-manifest.v1"
            or manifest.get("commit") != args.candidate_commit
            or manifest.get("specDigest") != args.spec_digest
            or manifest.get("supportSafe") is not True
        ):
            fail("candidate manifest identity does not match the exact testApp run")
        images = manifest.get("images")
        if not isinstance(images, list):
            fail("candidate manifest images are invalid")
        manifest_references = {
            image.get("component"): image.get("reference")
            for image in images
            if isinstance(image, dict)
        }
        if set(manifest_references) != COMPONENTS:
            fail("candidate manifest image set is incomplete")

    evidence_images: list[dict[str, object]] = []
    for component in sorted(COMPONENTS):
        reference, resolved_id = supplied[component]
        if manifest_bound:
            if (
                not IMAGE_REFERENCE.fullmatch(reference)
                or manifest_references.get(component) != reference
            ):
                fail(f"{component} does not match the exact candidate manifest")
        service = RUNNING_SERVICES.get(component)
        if service is not None:
            observed_id = container_image_id(args.compose_project, service)
            if observed_id != resolved_id:
                fail(f"{component} running container image differs from the resolved candidate")
            lifecycle = "running-container"
        else:
            observed_id = resolved_id
            lifecycle = "successful-one-shot-lifecycle"
        evidence_images.append(
            {
                "component": component,
                "immutableReference": reference if manifest_bound else None,
                "localImageId": resolved_id,
                "observedImageId": observed_id,
                "lifecycle": lifecycle,
                "matchesCandidate": True,
            }
        )

    private_json(
        args.output,
        {
            "schemaVersion": "weave.test-app-runtime-images/v1",
            "candidateCommit": args.candidate_commit,
            "specificationCommit": args.specification_commit,
            "specDigest": args.spec_digest,
            "candidateManifestDigest": args.candidate_manifest_digest,
            "composeProject": args.compose_project,
            "manifestBound": manifest_bound,
            "images": evidence_images,
            "credentialsIncluded": False,
            "containsSecretValues": False,
            "supportSafe": True,
        },
    )
    print(
        "WEAVE_RUNTIME_IMAGE_EVIDENCE_OK "
        f"manifestBound={str(manifest_bound).lower()} components=4 supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
