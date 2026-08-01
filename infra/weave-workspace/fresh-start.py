#!/usr/bin/env python3
"""Two-phase exact-target Fresh Start operator workflow."""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import re
import subprocess
import sys
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator, NoReturn
from urllib.parse import urlsplit

sys.path.insert(
    0, str(Path(__file__).resolve().parent / "scripts")
)
from recovery_receipt import (  # noqa: E402
    ReceiptContractError,
    load_fresh_start_recovery,
)

LABEL_PREFIX = "com.massimotter.weave."
REQUIRED_CURRENT_LABELS = (
    "managed",
    "environment",
    "scope",
    "stack",
    "generation",
    "namespace",
    "component",
    "data-class",
    "fresh-start-eligible",
    "spec-commit",
    "spec-digest",
    "candidate-commit",
    "candidate-manifest-digest",
)


RESOURCE_KINDS = ("container", "volume", "network")
DELETION_PHASE = {
    "container": "01-applications",
    "volume": "02-persistent-data",
    "network": "03-connectivity",
}


def fail(message: str) -> NoReturn:
    raise SystemExit(f"WEAVE_FRESH_START_ERROR {message}")


def docker_inspect(kind: str, name: str) -> dict[str, Any] | None:
    command = ["docker", kind, "inspect", name]
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    if result.returncode != 0:
        return None
    payload = json.loads(result.stdout)
    if not isinstance(payload, list) or len(payload) != 1:
        fail(f"unexpected inspect result for exact {kind} target {name}")
    return payload[0]


def resource_identity(kind: str, inspected: dict[str, Any]) -> tuple[str, dict[str, str], str | None]:
    if kind == "container":
        return (
            str(inspected["Id"]),
            dict(inspected.get("Config", {}).get("Labels") or {}),
            str(inspected.get("Image") or ""),
        )
    if kind == "volume":
        return str(inspected["Name"]), dict(inspected.get("Labels") or {}), None
    if kind == "network":
        return str(inspected["Id"]), dict(inspected.get("Labels") or {}), None
    fail(f"unsupported target kind {kind}")


def expected_labels(args: argparse.Namespace, target: dict[str, str]) -> dict[str, str]:
    return {
        f"{LABEL_PREFIX}managed": "true",
        f"{LABEL_PREFIX}environment": args.environment,
        f"{LABEL_PREFIX}scope": args.scope,
        f"{LABEL_PREFIX}stack": args.stack,
        f"{LABEL_PREFIX}generation": args.retired_generation,
        f"{LABEL_PREFIX}namespace": args.namespace,
        f"{LABEL_PREFIX}component": target["component"],
        f"{LABEL_PREFIX}data-class": target["dataClass"],
        f"{LABEL_PREFIX}fresh-start-eligible": "true",
        f"{LABEL_PREFIX}spec-commit": args.spec_commit,
        f"{LABEL_PREFIX}spec-digest": args.spec_digest,
        f"{LABEL_PREFIX}candidate-commit": args.candidate_commit,
        f"{LABEL_PREFIX}candidate-manifest-digest": args.candidate_manifest_digest,
    }


def classify_labels(
    labels: dict[str, str], expected: dict[str, str], name: str
) -> tuple[str, dict[str, str]]:
    weave_labels = {key: value for key, value in labels.items() if key.startswith(LABEL_PREFIX)}
    ownership_label_keys = {
        f"{LABEL_PREFIX}{key}" for key in REQUIRED_CURRENT_LABELS
    }
    if not ownership_label_keys.intersection(labels):
        return "legacy-exact-allowlist", {}
    missing = [f"{LABEL_PREFIX}{key}" for key in REQUIRED_CURRENT_LABELS if f"{LABEL_PREFIX}{key}" not in labels]
    mismatched = {
        key: {"expected": value, "actual": labels.get(key)}
        for key, value in expected.items()
        if labels.get(key) != value
    }
    if missing or mismatched:
        fail(f"target {name} has partial or mismatched ownership metadata")
    return "current-exact-labels", dict(sorted(weave_labels.items()))


def validate_resource_entry(entry: Any, entry_type: str) -> dict[str, str]:
    if not isinstance(entry, dict):
        fail(f"{entry_type} entry must be an object")
    required = {"kind", "name", "component", "dataClass"}
    if entry_type == "exclusion":
        required.add("reason")
    if set(entry) != required:
        fail(f"{entry_type} entry has an unsupported shape")
    if entry["kind"] not in RESOURCE_KINDS:
        fail(f"{entry_type} has unsupported resource kind")
    for key, value in entry.items():
        if not isinstance(value, str) or not value or any(ord(char) < 0x20 for char in value):
            fail(f"{entry_type} {key} must be a non-empty support-safe string")
    return entry


def load_allowlist(
    path: Path, environment: str, stack: str
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != "weave.infra.fresh-start-targets.v1":
        fail("unsupported target allowlist schema")
    if payload.get("environment") != environment or payload.get("stack") != stack:
        fail("target allowlist does not match the exact environment and stack")
    targets = payload.get("targets")
    if not isinstance(targets, list) or not targets:
        fail("target allowlist is empty")
    targets = [validate_resource_entry(target, "target") for target in targets]
    exclusions_payload = payload.get("exclusions", [])
    if not isinstance(exclusions_payload, list):
        fail("target allowlist exclusions must be a list")
    exclusions = [
        validate_resource_entry(exclusion, "exclusion")
        for exclusion in exclusions_payload
    ]
    identities = [(target.get("kind"), target.get("name")) for target in targets]
    exclusion_identities = [
        (exclusion.get("kind"), exclusion.get("name")) for exclusion in exclusions
    ]
    all_identities = identities + exclusion_identities
    if len(all_identities) != len(set(all_identities)):
        fail("target allowlist contains duplicate or overlapping resources")
    return (
        sorted(targets, key=lambda target: (target["kind"], target["name"])),
        sorted(exclusions, key=lambda target: (target["kind"], target["name"])),
    )


def canonical_json_bytes(value: Any) -> bytes:
    """Serialize the constrained manifest model as RFC 8785 canonical JSON.

    Fresh Start manifests deliberately contain no JSON numbers, so the
    ECMAScript number-normalization branch of RFC 8785 is rejected rather than
    reimplemented differently by operator hosts.
    """

    def serialize(item: Any) -> str:
        if item is None:
            return "null"
        if item is True:
            return "true"
        if item is False:
            return "false"
        if isinstance(item, str):
            return json.dumps(item, ensure_ascii=False, separators=(",", ":"))
        if isinstance(item, list):
            return "[" + ",".join(serialize(member) for member in item) + "]"
        if isinstance(item, dict):
            if not all(isinstance(key, str) for key in item):
                fail("canonical manifest object keys must be strings")
            keys = sorted(
                item,
                key=lambda key: key.encode("utf-16be", errors="surrogatepass"),
            )
            return (
                "{"
                + ",".join(
                    f"{serialize(key)}:{serialize(item[key])}" for key in keys
                )
                + "}"
            )
        fail("canonical Fresh Start manifests must not contain JSON numbers")

    return serialize(value).encode("utf-8")


def write_manifest(path: Path, payload: dict[str, Any]) -> str:
    serialized = canonical_json_bytes(payload)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(serialized)
    digest = hashlib.sha256(serialized).hexdigest()
    path.with_suffix(path.suffix + ".sha256").write_text(f"{digest}  {path.name}\n", encoding="ascii")
    return digest


def validate_reference(value: str, label: str) -> None:
    try:
        parsed = urlsplit(value)
        parsed.port
    except (TypeError, ValueError):
        fail(f"{label} must be a support-safe HTTPS reference")
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or any(marker in value for marker in ("@", "\\", "\n", "\r", "%"))
        or any(ord(char) < 0x20 or ord(char) > 0x7E for char in value)
    ):
        fail(f"{label} must not contain credentials or control characters")


@contextmanager
def exclusive_lock(path: Path) -> Iterator[None]:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(path, os.O_CREAT | os.O_RDWR, 0o600)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            fail("another Fresh Start or deployment operation holds the environment lock")
        yield
    finally:
        fcntl.flock(descriptor, fcntl.LOCK_UN)
        os.close(descriptor)


def listed_resource_names(
    kind: str, environment: str, stack: str, scope: str, namespace: str
) -> set[str]:
    command = ["docker", kind, "ls"]
    if kind == "container":
        command.append("--all")
    command.extend(
        [
            "--quiet",
            "--filter",
            f"label={LABEL_PREFIX}managed=true",
            "--filter",
            f"label={LABEL_PREFIX}environment={environment}",
            "--filter",
            f"label={LABEL_PREFIX}stack={stack}",
            "--filter",
            f"label={LABEL_PREFIX}scope={scope}",
            "--filter",
            f"label={LABEL_PREFIX}namespace={namespace}",
        ]
    )
    result = subprocess.run(command, text=True, capture_output=True, check=False)
    if result.returncode != 0:
        fail(f"could not inventory managed {kind} resources")
    names: set[str] = set()
    for identifier in result.stdout.splitlines():
        inspected = docker_inspect(kind, identifier)
        if inspected is None:
            fail(f"managed {kind} {identifier} disappeared during inventory")
        if kind == "container":
            name = str(inspected.get("Name") or "").lstrip("/")
        else:
            name = str(inspected.get("Name") or "")
        if not name or name in names:
            fail(f"managed {kind} inventory contains an ambiguous resource identity")
        names.add(name)
    return names


def scoped_inventory(
    environment: str, stack: str, scope: str, namespace: str
) -> set[tuple[str, str]]:
    return {
        (kind, name)
        for kind in RESOURCE_KINDS
        for name in listed_resource_names(kind, environment, stack, scope, namespace)
    }


def exclusion_snapshot(
    exclusions: list[dict[str, str]],
) -> list[dict[str, Any]]:
    snapshots: list[dict[str, Any]] = []
    for exclusion in exclusions:
        inspected = docker_inspect(exclusion["kind"], exclusion["name"])
        if inspected is None:
            fail(f"declared exclusion {exclusion['name']} is missing")
        resource_id, labels, image_id = resource_identity(exclusion["kind"], inspected)
        snapshots.append(
            {
                "kind": exclusion["kind"],
                "name": exclusion["name"],
                "resourceId": resource_id,
                "component": exclusion["component"],
                "dataClass": exclusion["dataClass"],
                "reason": exclusion["reason"],
                "ownershipLabels": dict(
                    sorted(
                        (key, value)
                        for key, value in labels.items()
                        if key.startswith(LABEL_PREFIX)
                    )
                ),
                **({"imageId": image_id} if image_id else {}),
            }
        )
    return snapshots


def validate_plan_arguments(args: argparse.Namespace) -> str | None:
    if args.environment == "prod":
        fail("Fresh Start is forbidden for prod")
    for value, label in (
        (args.retired_generation, "retired generation"),
        (args.target_generation, "target generation"),
    ):
        if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,47}", value):
            fail(f"{label} is invalid")
    if args.retired_generation == args.target_generation:
        fail("retired and target generations must differ")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,47}", args.stack):
        fail("stack is invalid")
    if args.scope not in ("persistent", "isolated"):
        fail("scope is invalid")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{1,63}", args.namespace):
        fail("namespace is invalid")
    if not re.fullmatch(r"[0-9a-f]{40}", args.spec_commit):
        fail("spec commit must be exact")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", args.spec_digest):
        fail("spec digest must be exact")
    if not re.fullmatch(r"[0-9a-f]{40}", args.candidate_commit):
        fail("candidate commit must be exact")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", args.candidate_manifest_digest):
        fail("candidate manifest digest must be exact")
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{15,63}", args.operation_nonce):
        fail("operation nonce is invalid")
    if args.recovery_decision not in ("verified-backup", "approved-no-recovery"):
        fail("unsupported recovery decision")
    validate_reference(args.recovery_evidence_ref, "recovery evidence")
    if args.recovery_decision == "verified-backup":
        if args.recovery_receipt is None:
            fail("verified-backup requires one private recovery receipt")
        try:
            return load_fresh_start_recovery(
                args.recovery_receipt,
                candidate=args.candidate_commit,
                candidate_manifest_digest=args.candidate_manifest_digest,
            )
        except ReceiptContractError as error:
            fail(str(error))
    if args.recovery_receipt is not None:
        fail("approved-no-recovery must not consume a backup receipt")
    return None


def plan(args: argparse.Namespace) -> None:
    recovery_receipt_digest = validate_plan_arguments(args)
    with exclusive_lock(args.lock_file):
        targets, exclusions = load_allowlist(
            args.allowlist, args.environment, args.stack
        )
        inventory: list[dict[str, Any]] = []
        for target in targets:
            inspected = docker_inspect(target["kind"], target["name"])
            if inspected is None:
                continue
            resource_id, labels, image_id = resource_identity(target["kind"], inspected)
            classification, recorded_labels = classify_labels(
                labels, expected_labels(args, target), target["name"]
            )
            inventory.append(
                {
                    "kind": target["kind"],
                    "name": target["name"],
                    "resourceId": resource_id,
                    "component": target["component"],
                    "dataClass": target["dataClass"],
                    "consequence": f"retire-{target['dataClass']}",
                    "deletionPhase": DELETION_PHASE[target["kind"]],
                    "ownershipClassification": classification,
                    "ownershipLabels": recorded_labels,
                    **({"imageId": image_id} if image_id else {}),
                }
            )
        exclusion_inventory = exclusion_snapshot(exclusions)
        accounted = {
            (item["kind"], item["name"])
            for item in inventory + exclusion_inventory
        }
        unaccounted = scoped_inventory(
            args.environment, args.stack, args.scope, args.namespace
        ) - accounted
        if unaccounted:
            fail(
                "managed resources are neither exact targets nor reviewed exclusions: "
                + ", ".join(f"{kind}:{name}" for kind, name in sorted(unaccounted))
            )
        manifest = {
            "schemaVersion": "weave.infra.fresh-start-plan.v1",
            "supportSafe": True,
            "environment": args.environment,
            "scope": args.scope,
            "stack": args.stack,
            "namespace": args.namespace,
            "retiredGeneration": args.retired_generation,
            "targetGeneration": args.target_generation,
            "specCommit": args.spec_commit,
            "specDigest": args.spec_digest,
            "candidateCommit": args.candidate_commit,
            "candidateManifestDigest": args.candidate_manifest_digest,
            "operationNonce": args.operation_nonce,
            "recoveryDecision": {
                "decision": args.recovery_decision,
                "evidenceRef": args.recovery_evidence_ref,
                **(
                    {"receiptSha256": recovery_receipt_digest}
                    if recovery_receipt_digest is not None
                    else {}
                ),
            },
            "allowlistSha256": hashlib.sha256(args.allowlist.read_bytes()).hexdigest(),
            "targets": inventory,
            "exclusions": exclusion_inventory,
        }
        digest = write_manifest(args.output, manifest)
        print(f"WEAVE_FRESH_START_PLAN manifest={args.output} sha256={digest}")
        print(f"Confirmation: DELETE_OLD_WEAVE:{digest}")


def validate_approval(path: Path, manifest: dict[str, Any], digest: str) -> str:
    approval_bytes = path.read_bytes()
    approval = json.loads(approval_bytes)
    if canonical_json_bytes(approval) != approval_bytes:
        fail("approval evidence is not RFC 8785 canonical JSON")
    expected = {
        "schemaVersion": "weave.infra.fresh-start-approval.v1",
        "supportSafe": True,
        "decision": "approved",
        "environment": manifest["environment"],
        "planSha256": digest,
        "operationNonce": manifest["operationNonce"],
    }
    for key, value in expected.items():
        if approval.get(key) != value:
            fail(f"approval evidence {key} does not match the exact plan")
    if set(approval) != set(expected) | {"approverRole", "evidenceRef"}:
        fail("approval evidence contains unsupported fields")
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,63}", approval.get("approverRole", "")):
        fail("approval evidence approverRole is invalid")
    validate_reference(approval.get("evidenceRef", ""), "approval evidence")
    return hashlib.sha256(approval_bytes).hexdigest()


def assert_snapshot_unchanged(target: dict[str, Any], label: str) -> None:
    inspected = docker_inspect(target["kind"], target["name"])
    if inspected is None:
        fail(f"{label} {target['name']} disappeared")
    current_id, current_labels, image_id = resource_identity(target["kind"], inspected)
    current_weave_labels = {
        key: value for key, value in current_labels.items() if key.startswith(LABEL_PREFIX)
    }
    if current_id != target["resourceId"] or current_weave_labels != target["ownershipLabels"]:
        if target.get("ownershipClassification") == "legacy-exact-allowlist":
            if current_id != target["resourceId"] or any(
                key.startswith(LABEL_PREFIX) for key in current_labels
            ):
                fail(f"{label} {target['name']} changed after planning")
        else:
            fail(f"{label} {target['name']} changed after planning")
    if target.get("imageId") and image_id != target["imageId"]:
        fail(f"{label} container image changed for {target['name']}")


def apply(args: argparse.Namespace) -> None:
    manifest_bytes = args.manifest.read_bytes()
    digest = hashlib.sha256(manifest_bytes).hexdigest()
    manifest = json.loads(manifest_bytes)
    if manifest.get("schemaVersion") != "weave.infra.fresh-start-plan.v1":
        fail("unsupported plan manifest schema")
    if canonical_json_bytes(manifest) != manifest_bytes:
        fail("plan manifest is not RFC 8785 canonical JSON")
    with exclusive_lock(args.lock_file):
        if manifest.get("environment") == "prod":
            fail("Fresh Start is forbidden for prod")
        if args.confirm != f"DELETE_OLD_WEAVE:{digest}":
            fail("confirmation does not match the exact environment and manifest SHA-256")
        approval_digest = validate_approval(args.approval_evidence, manifest, digest)
        allowlist, declared_exclusions = load_allowlist(
            args.allowlist, manifest["environment"], manifest["stack"]
        )
        if hashlib.sha256(args.allowlist.read_bytes()).hexdigest() != manifest.get("allowlistSha256"):
            fail("target allowlist changed after planning")
        allowed = {(target["kind"], target["name"]) for target in allowlist}
        excluded = {
            (target["kind"], target["name"]) for target in declared_exclusions
        }
        targets = manifest.get("targets", [])
        exclusions = manifest.get("exclusions", [])
        if not targets:
            fail("plan contains no targets")
        planned = {(target["kind"], target["name"]) for target in targets}
        planned_exclusions = {
            (target["kind"], target["name"]) for target in exclusions
        }
        if planned_exclusions != excluded:
            fail("plan exclusions do not match the reviewed allowlist")
        for target in allowlist:
            identity = (target["kind"], target["name"])
            if identity not in planned and docker_inspect(*identity) is not None:
                fail(f"allowlisted target {target['name']} appeared after planning")
        for target in targets:
            if (target["kind"], target["name"]) not in allowed:
                fail(f"plan target {target['name']} is not in the exact allowlist")
            assert_snapshot_unchanged(target, "planned target")
        for exclusion in exclusions:
            assert_snapshot_unchanged(exclusion, "planned exclusion")
        expected_scoped = {
            (target["kind"], target["name"])
            for target in targets
            if target.get("ownershipClassification") != "legacy-exact-allowlist"
        } | {
            (target["kind"], target["name"])
            for target in exclusions
            if target.get("ownershipLabels", {}).get(f"{LABEL_PREFIX}managed") == "true"
        }
        if scoped_inventory(
            manifest["environment"],
            manifest["stack"],
            manifest["scope"],
            manifest["namespace"],
        ) != expected_scoped:
            fail("managed runtime inventory changed after planning")

        evidence_path = args.evidence or args.manifest.with_suffix(".apply-evidence.json")
        evidence = {
            "schemaVersion": "weave.infra.fresh-start-apply-evidence.v1",
            "supportSafe": True,
            "environment": manifest["environment"],
            "stack": manifest["stack"],
            "retiredGeneration": manifest["retiredGeneration"],
            "targetGeneration": manifest["targetGeneration"],
            "operationNonce": manifest["operationNonce"],
            "planSha256": digest,
            "approvalEvidenceSha256": approval_digest,
            "status": "applying",
            "results": [],
            "exclusionsVerified": False,
        }
        write_manifest(evidence_path, evidence)

        for target in sorted(
            targets, key=lambda item: (item["deletionPhase"], item["name"])
        ):
            result = {
                "kind": target["kind"],
                "name": target["name"],
                "resourceId": target["resourceId"],
                "status": "attempting",
            }
            evidence["results"].append(result)
            write_manifest(evidence_path, evidence)
            command = ["docker", target["kind"], "rm"]
            if target["kind"] == "container":
                command.append("--force")
            command.append(target["resourceId"])
            try:
                subprocess.run(command, check=True)
                result["status"] = "removed"
                write_manifest(evidence_path, evidence)
            except subprocess.CalledProcessError:
                result["status"] = "failed"
                evidence["status"] = "partial-failure"
                write_manifest(evidence_path, evidence)
                fail(f"exact target removal failed for {target['name']}")
        for target in targets:
            if docker_inspect(target["kind"], target["name"]) is not None:
                evidence["status"] = "post-cut-verification-failed"
                write_manifest(evidence_path, evidence)
                fail(f"removed target {target['name']} still exists")
        for exclusion in exclusions:
            assert_snapshot_unchanged(exclusion, "post-cut exclusion")
        evidence["exclusionsVerified"] = True
        evidence["status"] = "removed-pending-target-recreation"
        write_manifest(evidence_path, evidence)
        print(f"WEAVE_FRESH_START_APPLIED sha256={digest} targets={len(targets)}")


def parser() -> argparse.ArgumentParser:
    root = Path(__file__).resolve().parent
    default_lock = (
        Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state"))
        / "weave"
        / "fresh-start.lock"
    )
    result = argparse.ArgumentParser()
    subparsers = result.add_subparsers(dest="operation", required=True)
    plan_parser = subparsers.add_parser("plan")
    plan_parser.add_argument("--environment", required=True, choices=("dev", "test", "persistent-dogfood", "prod"))
    plan_parser.add_argument("--scope", required=True, choices=("persistent", "isolated"))
    plan_parser.add_argument("--stack", required=True)
    plan_parser.add_argument("--namespace", required=True)
    plan_parser.add_argument(
        "--retired-generation", "--generation", dest="retired_generation", required=True
    )
    plan_parser.add_argument("--target-generation", required=True)
    plan_parser.add_argument("--spec-commit", required=True)
    plan_parser.add_argument("--spec-digest", required=True)
    plan_parser.add_argument("--candidate-commit", required=True)
    plan_parser.add_argument("--candidate-manifest-digest", required=True)
    plan_parser.add_argument("--operation-nonce", required=True)
    plan_parser.add_argument(
        "--recovery-decision",
        choices=("verified-backup", "approved-no-recovery"),
        required=True,
    )
    plan_parser.add_argument("--recovery-evidence-ref", required=True)
    plan_parser.add_argument("--recovery-receipt", type=Path)
    plan_parser.add_argument("--allowlist", type=Path, default=root / "fresh-start-targets.json")
    plan_parser.add_argument("--output", type=Path, required=True)
    plan_parser.add_argument(
        "--lock-file", type=Path, default=default_lock
    )
    plan_parser.set_defaults(handler=plan)
    apply_parser = subparsers.add_parser("apply")
    apply_parser.add_argument("--manifest", type=Path, required=True)
    apply_parser.add_argument("--confirm", required=True)
    apply_parser.add_argument("--approval-evidence", type=Path, required=True)
    apply_parser.add_argument("--allowlist", type=Path, default=root / "fresh-start-targets.json")
    apply_parser.add_argument("--evidence", type=Path)
    apply_parser.add_argument(
        "--lock-file", type=Path, default=default_lock
    )
    apply_parser.set_defaults(handler=apply)
    return result


def main() -> None:
    args = parser().parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
