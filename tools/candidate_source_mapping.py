#!/usr/bin/env python3
"""Resolve and attest the protected dev source for one promotion-lane commit."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


COMMIT = re.compile(r"^[0-9a-f]{40}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
IMAGE_NAMES = (
    "backend",
    "keycloak",
    "keycloak-sanitizer",
    "mcp",
)
IMAGE_ENVIRONMENT = {
    "backend": "WEAVE_BACKEND_IMAGE",
    "keycloak": "WEAVE_KEYCLOAK_IMAGE",
    "keycloak-sanitizer": "WEAVE_KEYCLOAK_SANITIZER_IMAGE",
    "mcp": "WEAVE_MCP_IMAGE",
}


class MappingError(ValueError):
    pass


def git(repository: Path, *arguments: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", "-C", str(repository), *arguments],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and result.returncode != 0:
        raise MappingError(f"Git cannot prove candidate ancestry for {' '.join(arguments)}")
    return result.stdout.strip()


def parse_images(values: list[str]) -> dict[str, str]:
    if not values:
        return {}
    parsed: dict[str, str] = {}
    for value in values:
        name, separator, image_id = value.partition("=")
        if not separator or name not in IMAGE_NAMES or not IMAGE_ID.fullmatch(image_id):
            raise MappingError("images must use one closed component=sha256:<64 hex> binding")
        if name in parsed:
            raise MappingError(f"duplicate image binding: {name}")
        parsed[name] = image_id
    if tuple(sorted(parsed)) != tuple(sorted(IMAGE_NAMES)):
        raise MappingError("a complete candidate image mapping requires all four components")
    return dict(sorted(parsed.items()))


def load_expected_mapping(path: Path) -> dict[str, Any]:
    path = path.expanduser().absolute()
    if path.is_symlink() or not path.is_file():
        raise MappingError("expected candidate source mapping must be a regular file")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise MappingError("expected candidate source mapping is not valid JSON") from error
    if (
        not isinstance(value, dict)
        or value.get("schemaVersion") != "weave.candidate-source-mapping.v1"
        or value.get("status") != "passed"
        or value.get("supportSafe") is not True
        or value.get("containsSecretValues") is not False
        or not isinstance(value.get("images"), dict)
        or any(
            not isinstance(value.get(name), str)
            or not COMMIT.fullmatch(value[name])
            for name in (
                "laneCandidateCommit",
                "sourceCandidateCommit",
                "protectedDevHead",
                "sourceTree",
                "laneTree",
            )
        )
        or value.get("sourceTree") != value.get("laneTree")
    ):
        raise MappingError("expected candidate source mapping is unsafe or malformed")
    return value


def expected_images(expected: dict[str, Any]) -> dict[str, str]:
    return parse_images(
        [f"{name}={image_id}" for name, image_id in expected["images"].items()]
    )


def assert_expected_authority(
    expected: dict[str, Any],
    observed: dict[str, Any],
) -> None:
    for name in (
        "laneCandidateCommit",
        "sourceCandidateCommit",
        "sourceTree",
        "laneTree",
    ):
        if expected.get(name) != observed.get(name):
            raise MappingError(
                "expected image mapping does not match the current source/lane authority"
            )


def assert_local_images(images: dict[str, str], source_candidate: str) -> None:
    for name, image_id in sorted(images.items()):
        result = subprocess.run(
            ["docker", "image", "inspect", image_id, "--format", "{{json .}}"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode != 0:
            raise MappingError(f"attested candidate image is unavailable locally: {name}")
        try:
            inspected = json.loads(result.stdout)
        except json.JSONDecodeError as error:
            raise MappingError(f"Docker returned malformed image metadata: {name}") from error
        if not isinstance(inspected, dict) or not isinstance(
            inspected.get("Config"), dict
        ):
            raise MappingError(f"Docker returned malformed image metadata: {name}")
        labels = inspected["Config"].get("Labels") or {}
        if not isinstance(labels, dict):
            raise MappingError(f"Docker returned malformed image labels: {name}")
        if (
            inspected.get("Id") != image_id
            or labels.get("org.opencontainers.image.revision") != source_candidate
        ):
            raise MappingError(
                f"attested image identity or source revision changed: {name}"
            )
        if (
            name == "keycloak"
            and labels.get("com.massimotter.weave.keycloak.version") != "26.7.0"
        ):
            raise MappingError("attested Keycloak image is not the pinned 26.7.0 distribution")
        if (
            name == "keycloak-sanitizer"
            and labels.get("com.massimotter.weave.component")
            != "keycloak-admin-sanitizer"
        ):
            raise MappingError("attested Keycloak sanitizer has no component provenance")


def resolve(
    repository: Path,
    lane_candidate: str,
    protected_dev_ref: str,
    images: dict[str, str] | None = None,
) -> dict[str, Any]:
    if (
        not repository.is_dir()
        or repository.is_symlink()
        or not COMMIT.fullmatch(lane_candidate)
        or protected_dev_ref != "refs/remotes/origin/dev"
    ):
        raise MappingError("candidate source inputs are malformed")
    repository = repository.resolve()
    observed_head = git(repository, "rev-parse", "HEAD")
    if observed_head != lane_candidate:
        raise MappingError("checked-out HEAD does not equal the lane candidate")
    protected_dev_head = git(repository, "rev-parse", protected_dev_ref)
    if not COMMIT.fullmatch(protected_dev_head):
        raise MappingError("protected dev ref does not resolve to one exact commit")
    source_candidate = git(
        repository,
        "merge-base",
        lane_candidate,
        protected_dev_ref,
    )
    if not COMMIT.fullmatch(source_candidate):
        raise MappingError("lane candidate has no exact protected dev source")
    for descendant in (lane_candidate, protected_dev_ref):
        result = subprocess.run(
            [
                "git",
                "-C",
                str(repository),
                "merge-base",
                "--is-ancestor",
                source_candidate,
                descendant,
            ],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode != 0:
            raise MappingError("resolved source is not an ancestor of both protected authorities")
    source_tree = git(repository, "rev-parse", f"{source_candidate}^{{tree}}")
    lane_tree = git(repository, "rev-parse", f"{lane_candidate}^{{tree}}")
    if not COMMIT.fullmatch(source_tree) or not COMMIT.fullmatch(lane_tree):
        raise MappingError("candidate tree identity is malformed")
    if source_tree != lane_tree:
        raise MappingError("lane tree differs from its protected dev source tree")
    return {
        "schemaVersion": "weave.candidate-source-mapping.v1",
        "status": "passed",
        "laneCandidateCommit": lane_candidate,
        "sourceCandidateCommit": source_candidate,
        "protectedDevHead": protected_dev_head,
        "sourceTree": source_tree,
        "laneTree": lane_tree,
        "images": dict(sorted((images or {}).items())),
        "supportSafe": True,
        "containsSecretValues": False,
    }


def write_json(path: Path, value: dict[str, Any]) -> None:
    path = path.expanduser().absolute()
    if path.is_symlink():
        raise MappingError("candidate source evidence path must not be a symlink")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o644)
    finally:
        if temporary.exists():
            temporary.unlink()


def append_github_env(path: Path, mapping: dict[str, Any]) -> None:
    path = path.expanduser().absolute()
    if path.is_symlink() or not path.is_file():
        raise MappingError("GITHUB_ENV must be an existing regular runner file")
    flags = os.O_WRONLY | os.O_APPEND | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    with os.fdopen(descriptor, "a", encoding="utf-8") as stream:
        stream.write(
            f"WEAVE_IMAGE_SOURCE_COMMIT={mapping['sourceCandidateCommit']}\n"
        )
        for name, image_id in mapping["images"].items():
            stream.write(f"{IMAGE_ENVIRONMENT[name]}={image_id}\n")
        stream.flush()
        os.fsync(stream.fileno())


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(description=__doc__)
    value.add_argument("--repository", type=Path, required=True)
    value.add_argument("--lane-candidate", required=True)
    value.add_argument(
        "--protected-dev-ref",
        default="refs/remotes/origin/dev",
    )
    value.add_argument("--image", action="append", default=[])
    value.add_argument("--expected-mapping", type=Path)
    value.add_argument("--verify-local-images", action="store_true")
    value.add_argument("--github-env", type=Path)
    value.add_argument("--output", type=Path, required=True)
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.image and args.expected_mapping:
            raise MappingError("--image and --expected-mapping are mutually exclusive")
        expected = (
            load_expected_mapping(args.expected_mapping)
            if args.expected_mapping
            else None
        )
        images = expected_images(expected) if expected else parse_images(args.image)
        mapping = resolve(
            args.repository,
            args.lane_candidate.lower(),
            args.protected_dev_ref,
            images,
        )
        if expected:
            assert_expected_authority(expected, mapping)
        if args.verify_local_images:
            if not expected or not images:
                raise MappingError(
                    "--verify-local-images requires one complete expected mapping"
                )
            assert_local_images(images, mapping["sourceCandidateCommit"])
        write_json(args.output, mapping)
        if args.github_env:
            append_github_env(args.github_env, mapping)
    except (MappingError, OSError) as error:
        print(f"CANDIDATE_SOURCE_MAPPING_ERROR {error}", file=sys.stderr)
        return 1
    print(
        "CANDIDATE_SOURCE_MAPPING_RESULT "
        f"status=passed lane={mapping['laneCandidateCommit']} "
        f"source={mapping['sourceCandidateCommit']} images={len(mapping['images'])} "
        "supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
