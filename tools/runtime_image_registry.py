#!/usr/bin/env python3
"""Publish hosted runtime candidates to GHCR and preserve digest-bound identities.

These are CI *candidate* images, never production releases. Actual visibility
follows the pinned publication policy, including its owner-approved fallback;
successful live Dogfood qualification remains a separate evidence boundary.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from build_artifacts import ArtifactError, RUNTIME_PLATFORMS, canonical, digest_bytes
from artifact_evidence import runtime_shape


IMAGE_ID = re.compile(r"sha256:[0-9a-f]{64}\Z")
SOURCE = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
# Keep one mapping for publishers and consumers. Historical package names are
# not privacy evidence; live checks record actual access under the pinned policy.
NAMES = {
    "backend": "ghcr.io/masssi164/weave-private-ci-backend",
    "mcp": "ghcr.io/masssi164/weave-private-ci-mcp",
    "keycloak": "ghcr.io/masssi164/weave-private-ci-keycloak",
    "runtimeState": "ghcr.io/masssi164/weave-private-ci-runtime-state",
}


def validate_receipt(value: dict, source: str, spec_digest: str, *, platform: str, runtime: dict) -> dict[str, str]:
    """Fail closed on missing or mutable registry references in the signed payload."""
    if (not isinstance(value, dict) or value.get("schemaVersion") != "weave.runtime-images/v2"
            or not isinstance(source, str) or not SOURCE.fullmatch(source)
            or value.get("sourceCommit") != source
            or not isinstance(spec_digest, str) or not DIGEST.fullmatch(spec_digest)
            or value.get("specDigest") != spec_digest
            or platform not in RUNTIME_PLATFORMS.values() or value.get("platform") != platform
            or not runtime_shape(runtime) or value.get("runtime") != runtime
            or not isinstance(value.get("images"), dict) or set(value["images"]) != set(NAMES)):
        raise ArtifactError("runtime-registry-receipt-invalid")
    result = {}
    for component, name in NAMES.items():
        entry = value["images"][component]
        if (not isinstance(entry, dict) or set(entry) != {"imageId", "reference"}
                or not isinstance(entry["imageId"], str) or not IMAGE_ID.fullmatch(entry["imageId"])
                or not isinstance(entry["reference"], str)
                or not entry["reference"].startswith(name + "@")
                or not DIGEST.fullmatch(entry["reference"].removeprefix(name + "@"))):
            raise ArtifactError("runtime-registry-reference-invalid")
        result[component] = entry["imageId"]
    return result


def _docker(arguments: list[str], *, input_text: str | None = None) -> str:
    try:
        completed = subprocess.run(["docker", *arguments], check=True, input=input_text,
                                   text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except (OSError, subprocess.CalledProcessError):
        # Docker diagnostics may contain registry endpoints or auth details.
        raise ArtifactError("runtime-registry-publish-unavailable") from None
    return completed.stdout


def publish(images: dict[str, str], source: str, spec_digest: str, *, platform: str, runtime: dict) -> dict:
    """Tag and push the exact hosted image IDs; never rebuild or retag a release."""
    if (not isinstance(images, dict) or set(images) != set(NAMES)
            or platform not in RUNTIME_PLATFORMS.values() or not runtime_shape(runtime)
            or not isinstance(source, str) or not SOURCE.fullmatch(source)
            or not isinstance(spec_digest, str) or not DIGEST.fullmatch(spec_digest)
            or any(not isinstance(item, str) or not IMAGE_ID.fullmatch(item)
                   for item in images.values())):
        raise ArtifactError("runtime-registry-publication-input-invalid")
    if (os.environ.get("GITHUB_REPOSITORY") != "masssi164/weave"
            or os.environ.get("GITHUB_REF") not in {"refs/heads/dev", "refs/heads/dogfood"}
            or os.environ.get("GITHUB_EVENT_NAME") not in {"push", "workflow_dispatch"}
            or os.environ.get("GITHUB_SHA") != source):
        raise ArtifactError("runtime-registry-publication-context-invalid")
    if platform == "linux/amd64" and os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
        raise ArtifactError("amd64-runtime-is-manual-only")
    actor, token = os.environ.get("GITHUB_ACTOR", ""), os.environ.get("GHCR_TOKEN", "")
    run_id, attempt = os.environ.get("GITHUB_RUN_ID", ""), os.environ.get("GITHUB_RUN_ATTEMPT", "")
    if (not actor or not re.fullmatch(r"[A-Za-z0-9-]+", actor)
            or not token or any(char in token for char in "\r\n\0")
            or not re.fullmatch(r"[1-9][0-9]*", run_id)
            or not re.fullmatch(r"[1-9][0-9]*", attempt)):
        raise ArtifactError("runtime-registry-publication-credentials-unavailable")

    # Verify every existing image before any registry mutation. A copied receipt
    # cannot turn an AMD64 image into the automatic ARM64 component.
    for image_id in images.values():
        try:
            records = json.loads(_docker(["image", "inspect", image_id]))
        except (ValueError, TypeError):
            raise ArtifactError("runtime-registry-image-platform-mismatch") from None
        if (not isinstance(records, list) or len(records) != 1 or records[0].get("Id") != image_id
                or records[0].get("Os") != "linux" or records[0].get("Architecture") != platform.split("/")[1]):
            raise ArtifactError("runtime-registry-image-platform-mismatch")
    result = {"schemaVersion": "weave.runtime-images/v2", "sourceCommit": source,
              "specDigest": spec_digest, "platform": platform, "runtime": runtime, "images": {}}
    variant = platform.split("/")[1] + "-" + digest_bytes(canonical(runtime))[7:19]
    with tempfile.TemporaryDirectory(prefix="weave-ghcr-authority-") as config:
        Path(config).chmod(0o700)
        _docker(["--config", config, "login", "ghcr.io", "-u", actor, "--password-stdin"],
                input_text=token)
        for component, registry in NAMES.items():
            image_id = images[component]
            tag = f"{registry}:candidate-{source[:12]}-{run_id}-a{attempt}-{variant}"
            _docker(["tag", image_id, tag])
            published = _docker(["--config", config, "push", tag])
            matches = re.findall(r"\bdigest:\s*(sha256:[0-9a-f]{64})\b", published)
            if len(matches) != 1:
                raise ArtifactError("runtime-registry-push-digest-unavailable")
            result["images"][component] = {"imageId": image_id,
                                          "reference": registry + "@" + matches[0]}

    validate_receipt(result, source, spec_digest, platform=platform, runtime=runtime)
    return result
