#!/usr/bin/env python3
"""Finish exact-run teardown when CI cancellation interrupts testApp's trap."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from pathlib import Path


def docker(*args: str) -> str:
    return subprocess.run(["docker", *args], check=True, capture_output=True,
                          text=True, timeout=30).stdout


def verify_empty_namespace(namespace: str) -> None:
    for kind in ("container", "network", "volume"):
        args = [kind, "ls"] + (["--all"] if kind == "container" else [])
        if docker(*args, "--filter", f"label=com.massimotter.weave.namespace={namespace}",
                  "--format", "{{.Name}}" if kind == "volume" else "{{.ID}}").strip():
            raise ValueError("pre-proof cleanup found unexpected namespace resources")


def cleanup_inputs(repository: Path, output: Path, namespace: str) -> None:
    subprocess.run([
        "python3", str(repository / "gradle/scripts/cleanup_test_app_runtime.py"),
        "--repository-root", str(repository), "--output-root", str(output),
        "--namespace", namespace,
    ], check=True)


def cleanup_images(namespace: str, candidate: str) -> None:
    available = set(docker("image", "ls", "--format", "{{.Repository}}:{{.Tag}}").splitlines())
    for name, title in (("weave-backend", "Weave Server"),
                        ("weave-mcp-server", "Weave MCP Server")):
        tag = f"{name}:test-app-{namespace}"
        if tag not in available:
            continue
        image = json.loads(docker("image", "inspect", tag))[0]
        labels = image["Config"].get("Labels", {})
        if any(labels.get(key) != value for key, value in {
            "org.opencontainers.image.revision": candidate,
            "org.opencontainers.image.source": "https://github.com/masssi164/weave",
            "org.opencontainers.image.title": title,
            "com.massimotter.weave.provenance-reference": "local-test-app-not-published",
        }.items()):
            raise ValueError("refusing to remove an unowned run-scoped image tag")
        if docker("ps", "--all", "--filter", f"ancestor={tag}", "--format", "{{.ID}}").strip():
            raise ValueError("refusing to remove an in-use run-scoped image tag")
        docker("image", "rm", tag)


def main() -> None:
    repository = Path(__file__).resolve().parents[2]
    run_id = os.environ["WEAVE_TEST_APP_RUN_ID"]
    candidate = os.environ["WEAVE_CANDIDATE_COMMIT"]
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{5,39}", run_id):
        raise ValueError("invalid disposable run ID")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate):
        raise ValueError("invalid exact source commit")
    namespace = "weave-e2e-" + hashlib.sha256(run_id.encode("ascii")).hexdigest()[:16]
    output = repository / "build/test-app"
    run = output / namespace
    for path in (repository / "build", output, run):
        if path.is_symlink():
            raise ValueError("symlinked test output is not a cleanup authority")
    if not run.exists():
        # The shell trap can remove inputs before it removes image tags.
        verify_empty_namespace(namespace)
        cleanup_images(namespace, candidate)
        print("TEST_APP_CI_FINALIZER status=not-created")
        return
    proof_file = run / "empty-namespace-proof.json"
    env_file = run / "e2e.env"
    teardown_file = run / "teardown-evidence.json"
    for path in (proof_file, env_file, teardown_file):
        if path.is_symlink():
            raise ValueError("symlinked run input is not a cleanup authority")
    if not proof_file.exists():
        # Compose creation follows the proof. Still verify absence rather than
        # deleting private inputs for an unexpectedly running namespace.
        verify_empty_namespace(namespace)
        cleanup_images(namespace, candidate)
        cleanup_inputs(repository, output, namespace)
        print("TEST_APP_CI_FINALIZER status=no-stack inputsRemoved=true")
        return
    proof = json.loads(proof_file.read_text())
    if any(proof.get(key) != value for key, value in {
        "schemaVersion": "weave.e2e-empty-namespace-proof/v1",
        "runId": run_id, "namespace": namespace,
        "composeProject": namespace, "candidateCommit": candidate,
        "deploymentContext": "disposable", "environment": "e2e",
        "verifiedBeforeResourceCreation": True,
    }.items()):
        raise ValueError("cleanup proof does not match the exact disposable run")
    digest = proof.get("candidateManifestDigest", "")
    if not isinstance(digest, str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("cleanup proof has no exact ownership digest")
    if env_file.exists():
        env = dict(os.environ, WEAVE_E2E_STACK_SCOPE="isolated",
                   WEAVE_E2E_RUN_ID=run_id, WEAVE_E2E_RUN_NAMESPACE=namespace,
                   WEAVE_E2E_NAMESPACE=namespace,
                   WEAVE_CANDIDATE_MANIFEST_DIGEST=digest)
        subprocess.run([
            "bash", str(repository / "infra/weave-workspace/teardown.sh"),
            "e2e", "--isolated", "--env-file", str(env_file),
            "--evidence-file", str(teardown_file),
        ], env=env, check=True)
    evidence = json.loads(teardown_file.read_text())
    if (evidence.get("schemaVersion") != "weave.compose-isolated-teardown.v1"
            or evidence.get("profile") != "e2e"
            or evidence.get("composeProject") != namespace
            or evidence.get("dryRun") is not False
            or evidence.get("ownershipLabelsVerified") is not True
            or evidence.get("candidateCommit") != candidate
            or evidence.get("candidateManifestDigest") != digest
            or evidence.get("namespace") != namespace
            or any(type(evidence.get(key)) is not int or evidence[key] != 0 for key in (
                "remainingOwnedResources", "remainingContainerCount",
                "remainingNetworkCount", "remainingVolumeCount"))):
        raise ValueError("exact-run teardown did not prove zero remaining resources")
    cleanup_images(namespace, candidate)
    cleanup_inputs(repository, output, namespace)
    print("TEST_APP_CI_FINALIZER status=passed remainingOwnedResources=0")


if __name__ == "__main__":
    main()
