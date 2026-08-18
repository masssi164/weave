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
from typing import NoReturn


COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_REFERENCE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
COMPONENTS = {"server", "mcp-server", "keycloak-runtime"}
REALM_DEFINITION_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "containsSecrets",
}
REALM_EVIDENCE_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
    "candidateRealmDefinitionMatched",
    "environmentRealmRenderStable",
    "semanticReadbackVerified",
    "containsSecrets",
}
REALM_EVIDENCE_DIGEST_FIELDS = {
    "semanticRealmSourceDigest",
    "migrationDefinitionDigest",
    "overlayDigest",
    "renderedRealmDigest",
    "semanticReadbackDigest",
}
RUNNING_SERVICES = {
    "server": "backend",
    "mcp-server": "mcp",
    "keycloak-runtime": "keycloak",
}
KEYCLOAK_BUILD_EVIDENCE_LABEL = (
    "com.massimotter.weave.keycloak-build-evidence-digest"
)


def fail(message: str) -> NoReturn:
    raise SystemExit(f"WEAVE_RUNTIME_IMAGE_EVIDENCE_ERROR {message}")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--source-candidate-commit", required=True)
    parser.add_argument("--specification-commit", required=True)
    parser.add_argument("--spec-digest", required=True)
    parser.add_argument("--candidate-manifest-digest", required=True)
    parser.add_argument("--compose-project", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--realm-evidence", type=Path)
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
        ["docker", "container", "inspect", selected[0], "--format", "{{.Image}}"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()
    if not DIGEST.fullmatch(image_id):
        fail(f"{service} container has an invalid local image ID")
    return image_id


def image_label(image_id: str, label: str) -> str:
    return subprocess.run(
        [
            "docker",
            "image",
            "inspect",
            image_id,
            "--format",
            f'{{{{ index .Config.Labels "{label}" }}}}',
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout.strip()


def read_object(path: Path, label: str) -> dict[str, object]:
    if path.is_symlink() or not path.is_file():
        fail(f"{label} must be a regular non-symlink file")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f"{label} is invalid JSON: {error.msg}")
    if not isinstance(payload, dict):
        fail(f"{label} must contain one JSON object")
    return payload


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
    if not isinstance(payload, dict):
        fail("candidate manifest must contain one JSON object")
    return payload


def private_json(path: Path, payload: dict[str, object]) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
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
    if not COMMIT.fullmatch(args.source_candidate_commit):
        fail("source candidate commit is invalid")
    if not COMMIT.fullmatch(args.specification_commit):
        fail("specification commit is invalid")
    if not DIGEST.fullmatch(args.spec_digest):
        fail("specification digest is invalid")
    if not DIGEST.fullmatch(args.candidate_manifest_digest):
        fail("candidate manifest digest is invalid")
    if not re.fullmatch(r"weave-e2e-[0-9a-f]{16}", args.compose_project):
        fail("compose project is not one isolated testApp namespace")

    supplied = {component: (reference, image_id) for component, reference, image_id in args.image}
    if len(args.image) != len(COMPONENTS) or set(supplied) != COMPONENTS:
        fail("runtime image set must contain each candidate component exactly once")
    for component, (_, image_id) in supplied.items():
        if not DIGEST.fullmatch(image_id):
            fail(f"{component} resolved image ID is invalid")

    manifest_bound = args.manifest is not None
    manifest_references: dict[str, str] = {}
    manifest_keycloak_build_evidence: str | None = None
    realm_evidence: dict[str, object] | None = None
    realm_evidence_verified = False
    if manifest_bound:
        if args.realm_evidence is None:
            fail("manifest-bound evidence requires finalized realm evidence")
        manifest = read_manifest(args.manifest, args.candidate_manifest_digest)
        definition = manifest.get("realmDefinition")
        if (
            manifest.get("schemaVersion") != "weave.release.candidate-manifest.v4"
            or manifest.get("commit") != args.source_candidate_commit
            or manifest.get("specificationCommit") != args.specification_commit
            or manifest.get("specDigest") != args.spec_digest
            or manifest.get("supportSafe") is not True
            or not isinstance(definition, dict)
            or set(definition) != REALM_DEFINITION_FIELDS
            or not DIGEST.fullmatch(str(definition.get("semanticRealmSourceDigest", "")))
            or not DIGEST.fullmatch(str(definition.get("migrationDefinitionDigest", "")))
            or definition.get("containsSecrets") is not False
        ):
            fail("candidate manifest identity does not match the exact testApp run")
        images = manifest.get("images")
        if (
            not isinstance(images, list)
            or len(images) != len(COMPONENTS)
            or any(not isinstance(image, dict) for image in images)
        ):
            fail("candidate manifest images are invalid")
        manifest_references = {
            str(image.get("component")): str(image.get("reference")) for image in images
        }
        if set(manifest_references) != COMPONENTS:
            fail("candidate manifest image set is incomplete")
        observed = read_object(args.realm_evidence, "realm evidence")
        if (
            set(observed) != REALM_EVIDENCE_FIELDS
            or any(
                not DIGEST.fullmatch(str(observed.get(field, "")))
                for field in REALM_EVIDENCE_DIGEST_FIELDS
            )
            or observed.get("semanticRealmSourceDigest")
            != definition.get("semanticRealmSourceDigest")
            or observed.get("migrationDefinitionDigest")
            != definition.get("migrationDefinitionDigest")
            or observed.get("candidateRealmDefinitionMatched") is not True
            or observed.get("environmentRealmRenderStable") is not True
            or observed.get("semanticReadbackVerified") is not True
            or observed.get("containsSecrets") is not False
        ):
            fail("finalized realm evidence is invalid or not candidate-bound")
        realm_evidence = observed
        realm_evidence_verified = True
        keycloak_images = [image for image in images if image.get("component") == "keycloak-runtime"]
        if len(keycloak_images) != 1 or not DIGEST.fullmatch(
            str(keycloak_images[0].get("buildEvidenceDigest", ""))
        ):
            fail("candidate manifest Keycloak build evidence is invalid")
        manifest_keycloak_build_evidence = str(keycloak_images[0]["buildEvidenceDigest"])
    elif args.realm_evidence is not None:
        fail("finalized realm evidence requires an exact candidate manifest")

    evidence_images: list[dict[str, object]] = []
    for component in sorted(COMPONENTS):
        reference, resolved_id = supplied[component]
        if manifest_bound:
            if (
                not IMAGE_REFERENCE.fullmatch(reference)
                or manifest_references.get(component) != reference
            ):
                fail(f"{component} does not match the exact candidate manifest")
        service = RUNNING_SERVICES[component]
        observed_id = container_image_id(args.compose_project, service)
        if observed_id != resolved_id:
            fail(f"{component} running container image differs from the resolved candidate")
        evidence_image: dict[str, object] = {
            "component": component,
            "immutableReference": reference if manifest_bound else None,
            "localImageId": resolved_id,
            "observedImageId": observed_id,
            "lifecycle": "running-container",
            "matchesCandidate": True,
        }
        if component == "keycloak-runtime":
            build_evidence = image_label(resolved_id, KEYCLOAK_BUILD_EVIDENCE_LABEL)
            if not DIGEST.fullmatch(build_evidence):
                fail("Keycloak Runtime build evidence label is invalid")
            if (
                manifest_keycloak_build_evidence is not None
                and build_evidence != manifest_keycloak_build_evidence
            ):
                fail("Keycloak Runtime build evidence differs from the candidate manifest")
            evidence_image["buildEvidenceDigest"] = build_evidence
        evidence_images.append(evidence_image)

    private_json(
        args.output,
        {
            "schemaVersion": "weave.test-app-runtime-images/v2",
            "candidateCommit": args.candidate_commit,
            "sourceCandidateCommit": args.source_candidate_commit,
            "specificationCommit": args.specification_commit,
            "specDigest": args.spec_digest,
            "candidateManifestDigest": args.candidate_manifest_digest,
            "composeProject": args.compose_project,
            "manifestBound": manifest_bound,
            "realmEvidence": realm_evidence,
            "realmEvidenceVerified": realm_evidence_verified,
            "images": evidence_images,
            "credentialsIncluded": False,
            "containsSecretValues": False,
            "supportSafe": True,
        },
    )
    print(
        "WEAVE_RUNTIME_IMAGE_EVIDENCE_OK "
        f"manifestBound={str(manifest_bound).lower()} components=3 supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
