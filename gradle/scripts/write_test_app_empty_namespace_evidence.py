#!/usr/bin/env python3
"""Prove a run-unique E2E Docker namespace is empty before any resource creation."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


RUN_ID = re.compile(r"^[a-z0-9][a-z0-9-]{5,39}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")


def inspect_absent(kind: str, name: str) -> bool:
    return subprocess.run(
        ["docker", kind, "inspect", name],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode != 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--candidate-manifest-digest", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if (
        not RUN_ID.fullmatch(args.run_id)
        or not COMMIT.fullmatch(args.candidate_commit)
        or not DIGEST.fullmatch(args.candidate_manifest_digest)
    ):
        print("E2E_EMPTY_NAMESPACE_ERROR malformed immutable identity", file=sys.stderr)
        return 2
    root = args.repository_root.resolve() / "infra/weave-workspace"
    sys.path.insert(0, str(root / "scripts"))
    from compose_env import load_context  # noqa: PLC0415

    context = load_context("e2e", root, str(args.env_file.resolve()))
    if context.environment != "e2e" or context.isolated_namespace is None:
        print("E2E_EMPTY_NAMESPACE_ERROR context is not isolated E2E", file=sys.stderr)
        return 2
    if context.env["WEAVE_CANDIDATE_COMMIT"] != args.candidate_commit:
        print("E2E_EMPTY_NAMESPACE_ERROR candidate context mismatch", file=sys.stderr)
        return 2
    if context.env["WEAVE_CANDIDATE_MANIFEST_DIGEST"] != args.candidate_manifest_digest:
        print("E2E_EMPTY_NAMESPACE_ERROR candidate manifest context mismatch", file=sys.stderr)
        return 2
    volume_keys = [
        "WEAVE_CADDY_DATA_VOLUME",
        "WEAVE_CADDY_CONFIG_VOLUME",
        "WEAVE_DB_DATA_VOLUME",
        "WEAVE_KEYCLOAK_DATA_VOLUME",
        "WEAVE_NATIVE_FILES_DATA_VOLUME",
        "WEAVE_MAILPIT_DATA_VOLUME",
    ]
    profiles = set(context.active_profiles)
    if "provider-nextcloud" in profiles:
        volume_keys.append("WEAVE_NEXTCLOUD_DATA_VOLUME")
    if "provider-matrix" in profiles:
        volume_keys.extend(("WEAVE_SYNAPSE_DATA_VOLUME", "WEAVE_MATRIX_APPSERVICE_VOLUME"))
    if "storage-s3" in profiles:
        volume_keys.append("WEAVE_RUNTIME_STATE_VOLUME")
    resources = [("network", context.env["WEAVE_DOCKER_NETWORK"])] + [
        ("volume", context.env[key]) for key in volume_keys
    ]
    observations = []
    for kind, name in sorted(resources):
        absent = inspect_absent(kind, name)
        observations.append({"kind": kind, "name": name, "absent": absent})
        if not absent:
            print(
                f"E2E_EMPTY_NAMESPACE_ERROR pre-existing Docker {kind} {name}",
                file=sys.stderr,
            )
            return 1
    containers = subprocess.run(
        [
            "docker",
            "ps",
            "-a",
            "--filter",
            f"label=com.docker.compose.project={context.env['WEAVE_COMPOSE_PROJECT']}",
            "--format",
            "{{.ID}}",
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if containers:
        print("E2E_EMPTY_NAMESPACE_ERROR compose containers already exist", file=sys.stderr)
        return 1
    evidence = {
        "schemaVersion": "weave.e2e-empty-namespace-proof/v1",
        "supportSafe": True,
        "containsSecretValues": False,
        "environment": "e2e",
        "deploymentContext": "disposable",
        "runId": args.run_id,
        "namespace": context.isolated_namespace,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "candidateCommit": args.candidate_commit,
        "candidateManifestDigest": args.candidate_manifest_digest,
        "resources": observations,
        "composeContainersAbsent": True,
        "verifiedBeforeResourceCreation": True,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(args.output, 0o600)
    print(
        "E2E_EMPTY_NAMESPACE_RESULT status=passed resources="
        f"{len(observations)} supportSafe=true"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
