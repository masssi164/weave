#!/usr/bin/env python3
"""Verify the canonical downstream Keycloak build-evidence binding."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

import build_keycloak_image as build_contract


SHA256 = re.compile(r"sha256:[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}")
CANONICAL_FIELDS = {
    "schemaVersion",
    "candidateCommit",
    "specificationCommit",
    "specificationLockDigest",
    "keycloakVersion",
    "upstreamCommit",
    "upstreamArchiveSha256",
    "stockReference",
    "stockPlatform",
    "stockPlatformManifestDigest",
    "stockServicesJarSha256",
    "patchSha256",
    "patchedPaths",
    "patchedServicesJarSha256",
    "downstreamTestClasses",
    "downstreamTestCount",
    "buildToolchain",
    "providerId",
}


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"WEAVE_KEYCLOAK_BUILD_EVIDENCE_ERROR {message}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--specification-commit", required=True)
    args = parser.parse_args()

    if not COMMIT.fullmatch(args.candidate_commit):
        fail("candidate commit is invalid")
    if not COMMIT.fullmatch(args.specification_commit):
        fail("specification commit is invalid")
    if args.evidence.is_symlink() or not args.evidence.is_file():
        fail("evidence must be a regular non-symlink file")
    try:
        evidence = json.loads(args.evidence.read_bytes())
    except (OSError, json.JSONDecodeError):
        fail("evidence is invalid")
    projection = evidence.get("canonicalBuildEvidence")
    expected = evidence.get("canonicalBuildEvidenceDigest")
    toolchain = (
        projection.get("buildToolchain")
        if isinstance(projection, dict)
        else None
    )
    if (
        evidence.get("schemaVersion") != "weave.downstream-keycloak-image.v1"
        or evidence.get("supportSafe") is not True
        or evidence.get("containsSecretValues") is not False
        or evidence.get("evidenceForCandidateCommit") != args.candidate_commit
        or evidence.get("specificationCommit") != args.specification_commit
        or evidence.get("keycloakVersion") != build_contract.UPSTREAM_TAG
        or evidence.get("upstreamTag") != build_contract.UPSTREAM_TAG
        or evidence.get("upstreamTagCommit") != build_contract.UPSTREAM_COMMIT
        or not isinstance(projection, dict)
        or projection.get("schemaVersion")
        != "weave.downstream-keycloak-build-evidence.v1"
        or projection.get("candidateCommit") != args.candidate_commit
        or projection.get("specificationCommit") != args.specification_commit
        or set(projection) != CANONICAL_FIELDS
        or projection.get("keycloakVersion") != build_contract.UPSTREAM_TAG
        or projection.get("upstreamCommit") != build_contract.UPSTREAM_COMMIT
        or projection.get("upstreamArchiveSha256")
        != build_contract.ARCHIVE_SHA256
        or projection.get("stockReference")
        != build_contract.STOCK_KEYCLOAK_REFERENCE
        or projection.get("stockPlatform")
        != build_contract.STOCK_KEYCLOAK_PLATFORM
        or projection.get("stockPlatformManifestDigest")
        != build_contract.STOCK_KEYCLOAK_PLATFORM_MANIFEST_DIGEST
        or projection.get("stockServicesJarSha256")
        != build_contract.STOCK_SERVICES_SHA256
        or projection.get("patchSha256") != build_contract.PATCH_SHA256
        or projection.get("patchedServicesJarSha256")
        != build_contract.PATCHED_SERVICES_SHA256
        or projection.get("patchedPaths")
        != list(build_contract.PATCHED_PATHS)
        or projection.get("downstreamTestClasses")
        != list(build_contract.DOWNSTREAM_TEST_CLASSES)
        or not isinstance(projection.get("downstreamTestCount"), int)
        or projection["downstreamTestCount"] < 12
        or projection.get("providerId")
        != "weave-workload-client-registration-enforcer"
        or not SHA256.fullmatch(
            str(projection.get("specificationLockDigest", ""))
        )
        or any(
            not HEX_SHA256.fullmatch(str(projection.get(field, "")))
            for field in (
                "patchSha256",
                "patchedServicesJarSha256",
            )
        )
        or not isinstance(toolchain, dict)
        or set(toolchain)
        != {
            "javaVersion",
            "javaVendor",
            "mavenVersion",
            "mavenWrapperPropertiesSha256",
        }
        or not str(toolchain.get("javaVersion", "")).startswith("21")
        or not re.fullmatch(
            r"[0-9]+(?:\.[0-9]+)+", str(toolchain.get("mavenVersion", ""))
        )
        or not isinstance(toolchain.get("javaVendor"), str)
        or not toolchain["javaVendor"]
        or not HEX_SHA256.fullmatch(
            str(toolchain.get("mavenWrapperPropertiesSha256", ""))
        )
        or any(
            evidence.get(outer) != projection.get(inner)
            for outer, inner in (
                ("upstreamCommit", "upstreamCommit"),
                ("upstreamArchiveSha256", "upstreamArchiveSha256"),
                ("stockReference", "stockReference"),
                ("stockPlatform", "stockPlatform"),
                (
                    "stockPlatformManifestDigest",
                    "stockPlatformManifestDigest",
                ),
                ("stockServicesJarSha256", "stockServicesJarSha256"),
                ("patchSha256", "patchSha256"),
                ("patchedPaths", "patchedPaths"),
                ("patchedServicesJarSha256", "patchedServicesJarSha256"),
                ("downstreamPolicyTestClasses", "downstreamTestClasses"),
                ("downstreamPolicyTestCount", "downstreamTestCount"),
                ("buildToolchain", "buildToolchain"),
                ("providerId", "providerId"),
            )
        )
        or not SHA256.fullmatch(str(expected))
    ):
        fail("evidence identity is inconsistent")
    canonical = json.dumps(
        projection,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    actual = "sha256:" + hashlib.sha256(canonical).hexdigest()
    if actual != expected:
        fail("canonical projection digest does not match")
    print(f"WEAVE_KEYCLOAK_BUILD_EVIDENCE_OK digest={actual}")


if __name__ == "__main__":
    main()
