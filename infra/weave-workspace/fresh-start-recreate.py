#!/usr/bin/env python3
"""Recreate one removed Fresh Start generation from an exact candidate manifest."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, NoReturn

SCRIPT_ROOT = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_ROOT.parents[1]
LABEL_PREFIX = "com.massimotter.weave."
ROTATED_ENVIRONMENT_SECRETS = (
    "WEAVE_IDENTITY_ROTATION_EPOCH",
)


def fail(message: str) -> NoReturn:
    raise SystemExit(f"WEAVE_FRESH_START_RECREATE_ERROR {message}")


def load_fresh_start_module() -> Any:
    source = SCRIPT_ROOT / "fresh-start.py"
    spec = importlib.util.spec_from_file_location("weave_fresh_start", source)
    if spec is None or spec.loader is None:
        fail("Fresh Start implementation module is unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


FRESH = load_fresh_start_module()


def parse_args() -> argparse.Namespace:
    default_state_root = (
        Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state"))
        / "weave"
        / "fresh-start"
    )
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--apply-evidence", type=Path, required=True)
    parser.add_argument("--candidate-manifest", type=Path, required=True)
    parser.add_argument("--allowlist", type=Path, required=True)
    parser.add_argument("--lock-file", type=Path, required=True)
    parser.add_argument("--archive-root", type=Path, default=default_state_root)
    parser.add_argument("--evidence", type=Path, required=True)
    return parser.parse_args()


def exact_json(path: Path, schema: str) -> tuple[bytes, dict[str, Any]]:
    raw = path.read_bytes()
    payload = json.loads(raw)
    if payload.get("schemaVersion") != schema:
        fail(f"{path.name} has an unsupported schemaVersion")
    if payload.get("supportSafe") is not True:
        fail(f"{path.name} must declare supportSafe=true")
    if FRESH.canonical_json_bytes(payload) != raw:
        fail(f"{path.name} is not canonical JSON")
    adjacent = path.with_suffix(path.suffix + ".sha256")
    if not adjacent.is_file():
        fail(f"{path.name} is missing its adjacent digest")
    actual = hashlib.sha256(raw).hexdigest()
    if adjacent.read_text(encoding="ascii").split()[0] != actual:
        fail(f"{path.name} adjacent digest does not match")
    return raw, payload


def validate_inputs(args: argparse.Namespace) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], str]:
    plan_raw, plan = exact_json(args.plan, "weave.infra.fresh-start-plan.v1")
    _, apply_evidence = exact_json(
        args.apply_evidence, "weave.infra.fresh-start-apply-evidence.v1"
    )
    candidate_raw = args.candidate_manifest.read_bytes()
    candidate = json.loads(candidate_raw)
    subprocess.run(
        [
            sys.executable,
            str(REPOSITORY_ROOT / "gradle/tasks/candidate-manifest-check.py"),
            "--manifest",
            str(args.candidate_manifest),
        ],
        check=True,
    )
    candidate_digest = hashlib.sha256(candidate_raw).hexdigest()
    plan_digest = hashlib.sha256(plan_raw).hexdigest()

    expected_evidence = {
        "environment": plan["environment"],
        "stack": plan["stack"],
        "retiredGeneration": plan["retiredGeneration"],
        "targetGeneration": plan["targetGeneration"],
        "operationNonce": plan["operationNonce"],
        "planSha256": plan_digest,
        "status": "removed-pending-target-recreation",
        "exclusionsVerified": True,
    }
    for key, expected in expected_evidence.items():
        if apply_evidence.get(key) != expected:
            fail(f"apply evidence {key} does not match the exact plan")
    results = apply_evidence.get("results")
    if not isinstance(results, list) or not results:
        fail("apply evidence contains no removal results")
    if any(result.get("status") != "removed" for result in results):
        fail("apply evidence contains an incomplete removal")

    if candidate.get("commit") != plan["candidateCommit"]:
        fail("candidate commit does not match the Fresh Start plan")
    if candidate.get("specDigest") != plan["specDigest"]:
        fail("candidate spec digest does not match the Fresh Start plan")
    if f"sha256:{candidate_digest}" != plan["candidateManifestDigest"]:
        fail("candidate manifest digest does not match the Fresh Start plan")
    if plan["environment"] == "prod":
        fail("Fresh Start recreation is forbidden for prod")

    lock_payload = json.loads(
        (REPOSITORY_ROOT / "specs/weave-specs.lock.json").read_text(encoding="utf-8")
    )
    if lock_payload["specCorpus"]["gitCommit"] != plan["specCommit"]:
        fail("checked-out specification lock commit does not match the plan")
    current_spec_digest = "sha256:" + hashlib.sha256(
        (REPOSITORY_ROOT / "specs/weave-specs.lock.json").read_bytes()
    ).hexdigest()
    if current_spec_digest != plan["specDigest"]:
        fail("checked-out specification lock digest does not match the plan")

    return plan, apply_evidence, candidate, candidate_digest


def image_reference(candidate: dict[str, Any], component: str) -> str:
    matches = [
        image["reference"]
        for image in candidate["images"]
        if image["component"] == component
    ]
    if len(matches) != 1:
        fail(f"candidate has no exact {component} image")
    return matches[0]


def archive_previous_generation(archive: Path) -> list[str]:
    if archive.exists():
        fail("generation archive already exists")
    archive.mkdir(parents=True, mode=0o700)
    archived: list[str] = []
    exact_sources = (
        SCRIPT_ROOT / ".generated",
    )
    for source in exact_sources:
        if not source.exists():
            continue
        target = archive / source.relative_to(SCRIPT_ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
        archived.append(str(source.relative_to(SCRIPT_ROOT)))
    return archived


def recreate_environment(
    plan: dict[str, Any], candidate: dict[str, Any], candidate_digest: str
) -> None:
    environment = dict(os.environ)
    for variable in ROTATED_ENVIRONMENT_SECRETS:
        environment.pop(variable, None)
    environment.update(
        {
            "WEAVE_LOCAL_CREDENTIAL_STATE_FILE": "none",
            "WEAVE_LOCAL_TLS_STATE_DIR": "none",
            "WEAVE_RESOURCE_STACK": plan["stack"],
            "WEAVE_RESOURCE_GENERATION": plan["targetGeneration"],
            "WEAVE_CANDIDATE_COMMIT": plan["candidateCommit"],
            "WEAVE_CANDIDATE_MANIFEST_DIGEST": f"sha256:{candidate_digest}",
            "WEAVE_BACKEND_IMAGE": image_reference(candidate, "server"),
            "WEAVE_MCP_IMAGE": image_reference(
                candidate, "mcp-server"
            ),
            "WEAVE_KEYCLOAK_IMAGE": image_reference(
                candidate, "keycloak-runtime"
            ),
        }
    )
    profile = {
        "dev": "dev",
        "e2e": "e2e",
        "persistent-dogfood": "dogfood",
        "prod": "prod",
    }[plan["environment"]]
    subprocess.run(
        ["bash", str(SCRIPT_ROOT / "install.sh"), profile],
        cwd=SCRIPT_ROOT,
        env=environment,
        check=True,
    )


def verify_recreated(plan: dict[str, Any], allowlist: Path) -> int:
    targets, exclusions = FRESH.load_allowlist(
        allowlist, plan["environment"], plan["stack"]
    )
    if exclusions:
        fail("target recreation currently requires an exclusion-free Fresh Start allowlist")
    verified = 0
    for target in targets:
        inspected = FRESH.docker_inspect(target["kind"], target["name"])
        if inspected is None:
            fail(f"recreated target {target['name']} is unavailable")
        _, labels, _ = FRESH.resource_identity(target["kind"], inspected)
        expected = {
            f"{LABEL_PREFIX}managed": "true",
            f"{LABEL_PREFIX}environment": plan["environment"],
            f"{LABEL_PREFIX}scope": plan["scope"],
            f"{LABEL_PREFIX}stack": plan["stack"],
            f"{LABEL_PREFIX}generation": plan["targetGeneration"],
            f"{LABEL_PREFIX}namespace": plan["namespace"],
            f"{LABEL_PREFIX}component": target["component"],
            f"{LABEL_PREFIX}data-class": target["dataClass"],
            f"{LABEL_PREFIX}spec-commit": plan["specCommit"],
            f"{LABEL_PREFIX}spec-digest": plan["specDigest"],
            f"{LABEL_PREFIX}candidate-commit": plan["candidateCommit"],
            f"{LABEL_PREFIX}candidate-manifest-digest": plan[
                "candidateManifestDigest"
            ],
        }
        if any(labels.get(key) != value for key, value in expected.items()):
            fail(f"recreated target {target['name']} has incorrect ownership metadata")
        verified += 1
    return verified


def main() -> int:
    args = parse_args()
    plan, apply_evidence, candidate, candidate_digest = validate_inputs(args)
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{15,63}", plan["operationNonce"]):
        fail("operation nonce is invalid")
    archive = args.archive_root.expanduser().absolute() / plan["operationNonce"]
    with FRESH.exclusive_lock(args.lock_file):
        archived = archive_previous_generation(archive)
        evidence = {
            "schemaVersion": "weave.infra.fresh-start-recreation-evidence.v1",
            "supportSafe": True,
            "environment": plan["environment"],
            "stack": plan["stack"],
            "operationNonce": plan["operationNonce"],
            "planSha256": apply_evidence["planSha256"],
            "targetGeneration": plan["targetGeneration"],
            "candidateCommit": plan["candidateCommit"],
            "candidateManifestDigest": plan["candidateManifestDigest"],
            "archivedStateEntries": archived,
            "status": "recreating",
            "verifiedTargets": "0",
        }
        FRESH.write_manifest(args.evidence, evidence)
        try:
            recreate_environment(plan, candidate, candidate_digest)
            verified = verify_recreated(plan, args.allowlist)
        except (OSError, subprocess.CalledProcessError, SystemExit):
            evidence["status"] = "recreation-failed-state-archived"
            FRESH.write_manifest(args.evidence, evidence)
            raise
        evidence["status"] = "ready-owner-activation-pending"
        evidence["verifiedTargets"] = str(verified)
        FRESH.write_manifest(args.evidence, evidence)
    print(
        "WEAVE_FRESH_START_RECREATED "
        f"generation={plan['targetGeneration']} verifiedTargets={verified}"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as failure:
        fail(f"invalid manifest structure: {failure}")
