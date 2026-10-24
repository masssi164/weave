#!/usr/bin/env python3
"""Bound Docker storage for already verified hosted runtime artifacts on the Mac.

This module is a storage owner, not an artifact authenticator. The consumer must
verify the manifest, composition and all loaded images before calling
``record_verified_runtime``. Only those exact image IDs enter this private ledger.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time
from typing import Callable

from runtime_image_registry import NAMES


DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")
CONTAINER = re.compile(r"[0-9a-f]{64}\Z")
COMPONENTS = {"backend", "mcp", "keycloak", "runtimeState"}
LEGACY_SCHEMA = "weave.runtime-image-ledger/v1"
SCHEMA = "weave.runtime-image-ledger/v2"
MAX_LEDGER_BYTES = 256 * 1024
MAX_PENDING_IMAGES = 2048
Docker = Callable[[list[str]], str]


class RuntimeStorageError(RuntimeError):
    """Support-safe storage error without Docker responses or private paths."""


def default_ledger() -> Path:
    return Path.home() / ".weave/artifact-runtime-images/ledger.json"


def _docker(arguments: list[str]) -> str:
    try:
        return subprocess.run(["docker", *arguments], check=True, capture_output=True,
                              text=True, timeout=30).stdout
    except (OSError, subprocess.SubprocessError):
        raise RuntimeStorageError("runtime-image-docker-command-failed") from None


def _safe_file(info: os.stat_result) -> None:
    if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid()
            or info.st_mode & 0o077 or info.st_nlink != 1):
        raise RuntimeStorageError("runtime-image-ledger-file-not-private")


def _safe_path(path: Path) -> None:
    if not path.is_absolute() or ".." in path.parts:
        raise RuntimeStorageError("runtime-image-ledger-path-invalid")
    if any(item.is_symlink() for item in (path, *path.parents)):
        raise RuntimeStorageError("runtime-image-ledger-symlink-refused")


@contextmanager
def _locked(path: Path):
    _safe_path(path)
    path.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
    _safe_path(path)
    parent = path.parent.stat()
    if (not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.getuid()
            or parent.st_mode & 0o077):
        raise RuntimeStorageError("runtime-image-ledger-directory-not-private")
    lock = path.with_suffix(path.suffix + ".lock")
    _safe_path(lock)
    descriptor = os.open(lock, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        _safe_file(os.fstat(descriptor))
        deadline = time.monotonic() + 30
        while True:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise RuntimeStorageError("runtime-image-ledger-lock-timeout") from None
                time.sleep(0.1)
        yield
    finally:
        os.close(descriptor)


def _verified_set(manifest: dict, images: dict) -> dict:
    if (not isinstance(manifest, dict)
            or manifest.get("schemaVersion") != "weave.build-artifact/v1"
            or manifest.get("component") != "runtime-linux-amd64"
            or not isinstance(images, dict) or set(images) != COMPONENTS
            or not all(isinstance(value, str) and DIGEST.fullmatch(value) for value in images.values())):
        raise RuntimeStorageError("runtime-image-verified-set-invalid")
    value = {"sourceCommit": manifest.get("sourceCommit"),
             "inputDigest": manifest.get("inputDigest"),
             "payloadSha256": manifest.get("payload", {}).get("sha256"),
             "images": dict(images)}
    _validate_set(value)
    return value


def _validate_set(value: object) -> None:
    if (not isinstance(value, dict)
            or set(value) != {"sourceCommit", "inputDigest", "payloadSha256", "images"}
            or not isinstance(value["sourceCommit"], str) or not SHA.fullmatch(value["sourceCommit"])
            or any(not isinstance(value[key], str) or not DIGEST.fullmatch(value[key])
                   for key in ("inputDigest", "payloadSha256"))
            or not isinstance(value["images"], dict) or set(value["images"]) != COMPONENTS
            or not all(isinstance(image, str) and DIGEST.fullmatch(image) for image in value["images"].values())):
        raise RuntimeStorageError("runtime-image-ledger-set-invalid")


def _valid_registry_reference(reference: object, component: str | None = None) -> bool:
    names = [NAMES[component]] if component else NAMES.values()
    return isinstance(reference, str) and any(
        reference.startswith(name + "@") and DIGEST.fullmatch(reference.removeprefix(name + "@"))
        for name in names)


def _verified_registry_references(images: dict, references: dict | None) -> dict[str, list[str]]:
    if references is None:
        return {}
    if (not isinstance(references, dict) or set(references) != COMPONENTS
            or not all(_valid_registry_reference(reference, component)
                       for component, reference in references.items())):
        raise RuntimeStorageError("runtime-image-registry-ownership-invalid")
    result: dict[str, list[str]] = {}
    for component, reference in references.items():
        result.setdefault(images[component], []).append(reference)
    return {image: sorted(set(owned)) for image, owned in result.items()}


def _read(path: Path) -> dict:
    if not path.exists():
        return {"schemaVersion": SCHEMA, "ownerUid": os.getuid(), "retainedSets": [],
                "pendingImageIds": [], "ownedRegistryReferences": {}}
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "r", encoding="utf-8") as stream:
        info = os.fstat(stream.fileno())
        _safe_file(info)
        if info.st_size > MAX_LEDGER_BYTES:
            raise RuntimeStorageError("runtime-image-ledger-size-limit")
        try:
            value = json.load(stream)
        except (ValueError, UnicodeError):
            raise RuntimeStorageError("runtime-image-ledger-invalid") from None
    # Legacy receipts grant only image-ID ownership. Never infer authority over
    # existing registry references merely from their names during migration.
    if (isinstance(value, dict) and value.get("schemaVersion") == LEGACY_SCHEMA
            and set(value) == {"schemaVersion", "ownerUid", "retainedSets", "pendingImageIds"}):
        value = {**value, "schemaVersion": SCHEMA, "ownedRegistryReferences": {}}
    if (not isinstance(value, dict)
            or set(value) != {"schemaVersion", "ownerUid", "retainedSets", "pendingImageIds", "ownedRegistryReferences"}
            or value["schemaVersion"] != SCHEMA or type(value["ownerUid"]) is not int
            or value["ownerUid"] != os.getuid()
            or not isinstance(value["retainedSets"], list) or len(value["retainedSets"]) > 2
            or not isinstance(value["pendingImageIds"], list)
            or len(value["pendingImageIds"]) > MAX_PENDING_IMAGES
            or not all(isinstance(image, str) and DIGEST.fullmatch(image) for image in value["pendingImageIds"])
            or len(set(value["pendingImageIds"])) != len(value["pendingImageIds"])):
        raise RuntimeStorageError("runtime-image-ledger-invalid")
    for item in value["retainedSets"]:
        _validate_set(item)
    tracked = set(value["pendingImageIds"]) | {
        image for item in value["retainedSets"] for image in item["images"].values()}
    references = value["ownedRegistryReferences"]
    if (not isinstance(references, dict) or not set(references).issubset(tracked)
            or any(not isinstance(owned, list) or not owned or len(owned) > 32
                   or not all(_valid_registry_reference(reference) for reference in owned)
                   or len(set(owned)) != len(owned) for owned in references.values())):
        raise RuntimeStorageError("runtime-image-registry-ownership-invalid")
    return value


def _write(path: Path, value: dict) -> None:
    encoded = (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()
    if len(encoded) > MAX_LEDGER_BYTES or len(value["pendingImageIds"]) > MAX_PENDING_IMAGES:
        raise RuntimeStorageError("runtime-image-ledger-size-limit")
    descriptor, temporary_name = tempfile.mkstemp(prefix=".ledger-", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        temporary.unlink(missing_ok=True)


def _receipt(value: dict) -> dict:
    return {"schemaVersion": "weave.runtime-image-storage/v1", "retainedSetCount": len(value["retainedSets"]),
            "pendingImageCount": len(value["pendingImageIds"]), "removedImageCount": 0,
            "inUseImageCount": 0, "taggedImageCount": 0, "deferredImageCount": 0,
            "supportSafe": True, "containsSecretValues": False}


def _prune(path: Path, value: dict, docker: Docker) -> dict:
    retained = {image for item in value["retainedSets"] for image in item["images"].values()}
    pending = set(value["pendingImageIds"]) - retained
    result = _receipt(value)
    if not pending:
        value["pendingImageIds"] = []
        _write(path, value)
        result["pendingImageCount"] = 0
        return result
    containers = docker(["container", "ls", "--all", "--quiet", "--no-trunc"]).split()
    if not all(CONTAINER.fullmatch(item) for item in containers):
        raise RuntimeStorageError("runtime-image-container-inventory-invalid")
    used = set()
    if containers:
        records = json.loads(docker(["container", "inspect", *containers]))
        if (not isinstance(records, list) or len(records) != len(containers)
                or {item.get("Id") for item in records} != set(containers)
                or not all(isinstance(item.get("Image"), str) and DIGEST.fullmatch(item["Image"]) for item in records)):
            raise RuntimeStorageError("runtime-image-container-inventory-invalid")
        used = {item["Image"] for item in records}
    installed = set(docker(["image", "ls", "--all", "--quiet", "--no-trunc"]).split())
    if not all(DIGEST.fullmatch(item) for item in installed):
        raise RuntimeStorageError("runtime-image-inventory-invalid")
    deferred = []
    for image in sorted(pending):
        if image not in installed:
            continue
        if image in used:
            deferred.append(image)
            result["inUseImageCount"] += 1
            continue
        records = json.loads(docker(["image", "inspect", image]))
        if (not isinstance(records, list) or len(records) != 1 or records[0].get("Id") != image
                or any(records[0].get(key) is not None and not isinstance(records[0][key], list)
                       for key in ("RepoTags", "RepoDigests"))):
            raise RuntimeStorageError("runtime-image-inventory-invalid")
        actual_references = records[0].get("RepoDigests") or []
        owned_references = value["ownedRegistryReferences"].get(image, [])
        if not all(isinstance(reference, str) for reference in actual_references):
            raise RuntimeStorageError("runtime-image-inventory-invalid")
        # Preserve every operator tag and unowned registry reference. Only
        # digest references passed by the authenticated consumer are ours.
        if records[0].get("RepoTags") or set(actual_references) - set(owned_references):
            deferred.append(image)
            result["taggedImageCount"] += 1
            continue
        try:
            # Docker protects concurrent container references. Do not force
            # removal or implicitly delete unrecorded untagged parent images.
            # Removing the last owned digest also removes its otherwise-unused
            # image. Removing by ID alone cannot clear multiple digest aliases.
            for target in sorted(actual_references) or [image]:
                docker(["image", "rm", "--no-prune", target])
        except RuntimeStorageError:
            deferred.append(image)
            result["deferredImageCount"] += 1
        else:
            result["removedImageCount"] += 1
    value["pendingImageIds"] = deferred
    tracked = retained | set(deferred)
    value["ownedRegistryReferences"] = {
        image: references for image, references in value["ownedRegistryReferences"].items() if image in tracked}
    _write(path, value)
    result["pendingImageCount"] = len(deferred)
    return result


def record_verified_runtime(manifest: dict, images: dict, *, registry_references: dict | None = None,
                            ledger: Path | None = None,
                            docker: Docker = _docker) -> dict:
    """Record a fully verified loaded set and retain this set plus its predecessor."""
    current = _verified_set(manifest, images)
    verified_references = _verified_registry_references(images, registry_references)
    path = ledger if ledger is not None else default_ledger()
    with _locked(path):
        value = _read(path)
        previous = value["retainedSets"]
        value["retainedSets"] = ([current] + [item for item in previous if item != current])[:2]
        retained = {image for item in value["retainedSets"] for image in item["images"].values()}
        old = {image for item in previous for image in item["images"].values()}
        value["pendingImageIds"] = sorted((set(value["pendingImageIds"]) | old) - retained)
        references = value["ownedRegistryReferences"]
        for image, verified in verified_references.items():
            references[image] = sorted(set(references.get(image, [])) | set(verified))
            if len(references[image]) > 32:
                raise RuntimeStorageError("runtime-image-registry-ownership-limit")
        # Persist ownership before destructive operations. A crash after rm is
        # harmless: the next pass sees that exact recorded ID is already absent.
        _write(path, value)
        return _prune(path, value, docker)


def prune_runtime_storage(*, ledger: Path | None = None, docker: Docker = _docker) -> dict:
    """Retry only this ledger's retired IDs, preserving all container references."""
    path = ledger if ledger is not None else default_ledger()
    with _locked(path):
        return _prune(path, _read(path), docker)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["prune"])
    args = parser.parse_args()
    print(json.dumps(prune_runtime_storage(), sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeStorageError, OSError, ValueError) as error:
        print("WEAVE_RUNTIME_IMAGE_STORAGE_ERROR " + (str(error) if isinstance(error, RuntimeStorageError) else type(error).__name__))
        raise SystemExit(2) from None
