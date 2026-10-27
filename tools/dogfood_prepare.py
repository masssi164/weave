#!/usr/bin/env python3
"""Explicit Dogfood preparation: public local-trust preflight, never qualification."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import hashlib
import html
import re
import secrets
import signal
import stat
import subprocess
import sys
import tempfile
import time
from urllib.parse import urlsplit, parse_qs, quote
import urllib.request
import unicodedata
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "infra/weave-workspace/scripts"))
from compose_env import ContractError, load_context
from compose_runtime import _dogfood_session_lock, _private_json
from dogfood_lifecycle import effective_reviewed_env_file
from dogfood_public_setup import _no_symlink, https_origin, public_setup, write_public_setup

HEX = re.compile(r"^[0-9a-f]{32}$")
HASH = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
OPERATOR_BOUNDARIES = frozenset({"invalid-request", "dogfood-posture-required", "candidate-mismatch", "configuration-mismatch",
    "schema-authority-unavailable", "owner-unavailable", "owner-policy-denied", "owner-space-access-conflict", "plan-conflict",
    "operator-reference-conflict", "invitation-conflict", "invitation-expired", "invitation-revoked", "invitation-outcome-uncertain",
    "identity-unavailable", "test-member-state-conflict", "test-member-not-activated", "member-proof-required", "member-proof-conflict",
    "space-access-conflict", "space-version-conflict", "identity-provider-unavailable", "persistence-unavailable", "audit-unavailable",
    "operation-incomplete", "managed-test-identity-conflict", "managed-test-credential-conflict"})


def strict_json(raw: str) -> dict:
    def unique(pairs):
        value = {}
        for key, child in pairs:
            if key in value: raise ValueError("duplicate JSON key")
            value[key] = child
        return value
    value = json.loads(raw, object_pairs_hook=unique)
    if not isinstance(value, dict): raise ValueError("JSON object required")
    return value


def private_directory(path: Path) -> Path:
    _no_symlink(path)
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    info = path.stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise ContractError("private-preparation-record-unsafe")
    return path


def private_read(path: Path, maximum: int = 16384) -> dict:
    try:
        _no_symlink(path)
        info = path.stat()
        parent = path.parent.stat()
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_nlink != 1
                or stat.S_IMODE(info.st_mode) != 0o600 or parent.st_uid != os.getuid()
                or parent.st_mode & 0o077 or not 0 < info.st_size <= maximum):
            raise ValueError()
        value = strict_json(path.read_text())
        if not isinstance(value, dict):
            raise ValueError()
        return value
    except (OSError, ValueError, ContractError):
        raise ContractError("private-preparation-record-unsafe") from None


def private_write(path: Path, value: dict) -> None:
    private_directory(path.parent)
    _no_symlink(path)
    if path.exists():
        private_read(path, maximum=65536)
    encoded = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()
    if len(encoded) > 65536:
        raise ContractError("private-preparation-record-too-large")
    descriptor, name = tempfile.mkstemp(prefix=".preparation-", dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            os.fchmod(stream.fileno(), 0o600)
            stream.write(encoded); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def identity_digest(kind: str, issuer: str, reference: str) -> str:
    return hashlib.sha256(json.dumps([kind, issuer, reference], ensure_ascii=False,
                                    separators=(",", ":")).encode()).hexdigest()


def text(value, maximum=256) -> bool:
    return isinstance(value, str) and 0 < len(value) <= maximum and not any(ord(c) < 32 or ord(c) == 127 for c in value)


def validate_plan(value: dict, context) -> dict:
    try:
        if (set(value) != {"schemaVersion", "planId", "nonce", "organizationOrigin", "apiOrigin", "issuerUrl", "spaceRef", "members"}
                or value["schemaVersion"] != "weave.dogfood-admin-preparation-plan/v1"
                or not HEX.fullmatch(value["planId"]) or not HEX.fullmatch(value["nonce"])
                or value["organizationOrigin"] != https_origin(context.env["WEAVE_PUBLIC_URL"])
                or value["apiOrigin"] != https_origin(context.env["WEAVE_API_ORIGIN"])
                or value["issuerUrl"] != https_origin(context.env["WEAVE_AUTH_URL"]) + "/realms/weave"
                or value["spaceRef"] != "workspace-default" or len(value["members"]) != 2):
            raise ValueError()
        for member in value["members"]:
            if (set(member) != {"slot", "email", "displayName", "role", "spaceRole", "invitationIdempotencyKey"}
                    or member["slot"] not in {"A", "B"} or member["role"] != "member" or member["spaceRole"] != "EDIT"
                    or not text(member["email"]) or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", member["email"])
                    or not text(member["displayName"], 160) or member["displayName"] != member["displayName"].strip()
                    or not text(member["invitationIdempotencyKey"], 128)):
                raise ValueError()
        for key in ("slot", "email", "invitationIdempotencyKey"):
            if len({member[key].casefold() for member in value["members"]}) != 2:
                raise ValueError()
        return value
    except (KeyError, TypeError, ValueError):
        raise ContractError("reviewed-test-member-plan-conflict") from None


def new_plan(context) -> dict:
    identifier = secrets.token_hex(16)
    domain = urlsplit(https_origin(context.env["WEAVE_PUBLIC_URL"])).hostname
    return validate_plan({"schemaVersion": "weave.dogfood-admin-preparation-plan/v1", "planId": identifier,
        "nonce": secrets.token_hex(16), "organizationOrigin": https_origin(context.env["WEAVE_PUBLIC_URL"]),
        "apiOrigin": https_origin(context.env["WEAVE_API_ORIGIN"]),
        "issuerUrl": https_origin(context.env["WEAVE_AUTH_URL"]) + "/realms/weave", "spaceRef": "workspace-default",
        "members": [{"slot": slot, "email": f"weave-smoke-{identifier}-{slot.lower()}@{domain}",
                     "displayName": f"Weave test member {slot} {identifier[:8]}", "role": "member", "spaceRole": "EDIT",
                     "invitationIdempotencyKey": "dogfood-" + identifier + "-" + slot} for slot in ("A", "B")]}, context)


def operator_plan(plan: dict, context) -> dict:
    validate_plan(plan, context)
    identifier = plan["planId"]
    domain = urlsplit(plan["organizationOrigin"]).hostname
    expected = [{"slot": slot, "email": f"weave-smoke-{identifier}-{slot.lower()}@{domain}",
                 "displayName": f"Weave test member {slot} {identifier[:8]}", "role": "member", "spaceRole": "EDIT",
                 "invitationIdempotencyKey": "dogfood-" + identifier + "-" + slot} for slot in ("A", "B")]
    if plan["members"] != expected:
        raise ContractError("dogfood-operator-plan-conflict")
    return plan


def operator_request(context, plan: dict, candidate: dict, operation: str, previous: dict | None, proof: dict | None,
                     initial_credentials: dict | None = None) -> dict:
    managed = operation == "provision-members"
    persistent = context.environment == "dogfood" and context.isolated_namespace is None
    isolated = managed and context.environment == "e2e" and context.isolated_namespace is not None
    if not (persistent or isolated):
        raise ContractError("dogfood-operator-posture-required")
    operator_plan(plan, context)
    if (operation not in {"invitations", "grants", "provision-members", "verify-members"} or set(candidate) != {"candidateCommit", "sourceTree", "specificationCommit"}
            or any(not isinstance(value, str) or not COMMIT.fullmatch(value) for value in candidate.values())
            or (operation in {"invitations", "verify-members"} and proof is not None)
            or (operation == "verify-members" and (not isinstance(previous, dict)
                or previous.get("status") != "passed" or previous.get("stage") != "provision-members-ready"))
            or (operation == "grants" and (previous is None or not isinstance(proof, dict) or proof.get("nonce") != plan["nonce"]))):
        raise ContractError("dogfood-operator-request-invalid")
    if previous is not None and (not isinstance(previous, dict) or previous.get("schemaVersion") != "weave.dogfood-operator-preparation-receipt/v1"
            or previous.get("authorizationKind") != "dogfood-operator" or previous.get("actorKind") != "dogfood-operator"
            or previous.get("planId") != plan["planId"] or previous.get("actorRef") != "operator:dogfood-prepare:" + plan["planId"]):
        raise ContractError("dogfood-preparation-authority-conflict")
    if operation == "grants":
        if (set(proof) != {"schemaVersion", "planId", "nonce", "members"} or proof["schemaVersion"] != "weave.dogfood-admin-preparation-proof/v1"
                or proof["planId"] != plan["planId"] or not isinstance(proof["members"], list)
                or len(proof["members"]) != 2 or previous.get("status") != "passed"
                or previous.get("stage") not in {"invitations-ready", "grants-ready"}):
            raise ContractError("dogfood-operator-request-invalid")
        for slot, member in zip(("A", "B"), proof["members"]):
            if (not isinstance(member, dict) or set(member) != {"slot", "accountDigest", "organizationDigest"} or member["slot"] != slot
                    or any(not isinstance(member[k], str) or not HASH.fullmatch(member[k]) for k in ("accountDigest", "organizationDigest"))):
                raise ContractError("dogfood-operator-request-invalid")
    api = context.env["WEAVE_API_URL"]
    parsed = urlsplit(api)
    if (https_origin(parsed.scheme + "://" + parsed.netloc) != plan["apiOrigin"]
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise ContractError("dogfood-operator-configuration-mismatch")
    version = "v3" if operation == "verify-members" else "v2" if managed else "v1"
    request = {"schemaVersion": "weave.dogfood-operator-preparation-request/" + version,
            "operation": operation, "environment": "dogfood", "nonce": plan["nonce"], **candidate, "apiBaseUrl": api,
            "plan": plan, "previousReceipt": previous, "proof": proof}
    if managed:
        if (proof is not None or not isinstance(initial_credentials, dict) or set(initial_credentials) != {"A", "B"}
                or any(not text(password, 128) or len(password) < 24 for password in initial_credentials.values())
                or initial_credentials["A"] == initial_credentials["B"]):
            raise ContractError("dogfood-operator-request-invalid")
        request["initialCredentials"] = [{"slot": slot, "password": initial_credentials[slot]} for slot in ("A", "B")]
    elif initial_credentials is not None:
        raise ContractError("dogfood-operator-request-invalid")
    return request


def validate_operator_envelope(value: dict, request: dict | None = None, previous: dict | None = None) -> dict:
    """Validate distinct operator authority without inventing a human Admin receipt."""
    try:
        if (not isinstance(value, dict) or set(value) != {"schemaVersion", "receipt", "privateData"}
                or value["schemaVersion"] != "weave.dogfood-operator-preparation-envelope/v1"
                or not isinstance(value["privateData"], dict) or set(value["privateData"]) not in ({"ownerDisplayName"}, {"ownerDisplayName", "members"})):
            raise ValueError()
        receipt = value["receipt"]
        keys = {"schemaVersion", "authorizationKind", "actorKind", "actorRef", "operation", "status", "stage", "boundary", "outcome",
                "nonce", "planId", "environment", "candidateCommit", "sourceTree", "specificationCommit", "organizationOrigin", "apiBaseUrl",
                "issuerUrl", "organizationId", "spaceRef", "intendedOwnerAccountRef", "intendedOwnerMemberHandle", "members", "supportSafe", "containsSecretValues"}
        if (not isinstance(receipt, dict) or set(receipt) != keys
                or receipt["schemaVersion"] != "weave.dogfood-operator-preparation-receipt/v1"
                or receipt["authorizationKind"] != "dogfood-operator" or receipt["actorKind"] != "dogfood-operator"
                or receipt["supportSafe"] is not True or receipt["containsSecretValues"] is not False
                or receipt["status"] not in {"passed", "blocked", "incomplete"}):
            raise ValueError()
        passed = receipt["status"] == "passed"
        plan_id = receipt["planId"]
        for key, pattern in (("planId", HEX), ("nonce", HEX), ("candidateCommit", COMMIT), ("sourceTree", COMMIT), ("specificationCommit", COMMIT),
                             ("intendedOwnerAccountRef", re.compile(r"acct_[0-9a-f]{32}")),
                             ("intendedOwnerMemberHandle", re.compile(r"mem_[A-Za-z0-9_-]{32}"))):
            if receipt[key] is None and not passed: continue
            if not isinstance(receipt[key], str) or not pattern.fullmatch(receipt[key]): raise ValueError()
        if receipt["actorRef"] != ("operator:dogfood-prepare:" + plan_id if plan_id else None): raise ValueError()
        for key, literal in (("environment", "dogfood"), ("spaceRef", "workspace-default")):
            if receipt[key] != literal and (passed or receipt[key] is not None): raise ValueError()
        if receipt["operation"] not in {"invitations", "grants", "provision-members", "verify-members"} and (passed or receipt["operation"] is not None): raise ValueError()
        for key in ("organizationOrigin", "apiBaseUrl", "issuerUrl", "organizationId"):
            if receipt[key] is not None and not text(receipt[key], 2048): raise ValueError()
            if passed and receipt[key] is None: raise ValueError()
        for key in ("organizationOrigin", "apiBaseUrl", "issuerUrl"):
            if receipt[key] is None: continue
            parsed = urlsplit(receipt[key])
            https_origin(parsed.scheme + "://" + parsed.netloc)
            if parsed.username or parsed.password or parsed.query or parsed.fragment or any(c.isspace() for c in receipt[key]): raise ValueError()
            if key == "organizationOrigin" and https_origin(receipt[key]) != receipt[key]: raise ValueError()
        if passed:
            if (receipt["boundary"] != "none" or receipt["outcome"] not in {"CREATED", "VERIFIED"}
                    or receipt["stage"] != receipt["operation"] + "-ready"):
                raise ValueError()
        elif (receipt["stage"] != "incomplete" or receipt["outcome"] not in {"BLOCKED", "PARTIAL"}
              or receipt["boundary"] not in OPERATOR_BOUNDARIES): raise ValueError()
        name = value["privateData"]["ownerDisplayName"]
        if name is not None and (not isinstance(name, str) or not name.strip() or len(name) > 200
                                 or any(unicodedata.category(c) == "Cc" for c in name)): raise ValueError()
        if passed and name is None: raise ValueError()
        members = receipt["members"]
        if not isinstance(members, list) or ([m.get("slot") for m in members] != ["A", "B"] if members else passed or plan_id is not None):
            raise ValueError()
        member_keys = {"slot", "intentRef", "invitationHandle", "memberHandle", "accountRef", "grantVersion", "intentState", "grantOutcome"}
        for member in members:
            if not isinstance(member, dict) or set(member) != member_keys: raise ValueError()
            for key, pattern in (("invitationHandle", r"inv_[A-Za-z0-9_-]{32}"), ("memberHandle", r"mem_[A-Za-z0-9_-]{32}"),
                                 ("accountRef", r"acct_[0-9a-f]{32}"), ("grantVersion", r'"sm-(?:0|[1-9][0-9]*)"')):
                if member[key] is not None and (not isinstance(member[key], str) or not re.fullmatch(pattern, member[key])): raise ValueError()
            if member["intentRef"] is not None and str(uuid.UUID(member["intentRef"])) != member["intentRef"]: raise ValueError()
            if (member["intentState"] not in {"NOT_ISSUED", "PENDING", "APPLIED", "FAILED", "EXPIRED", "UNAVAILABLE"}
                    or member["grantOutcome"] not in {"NOT_REQUESTED", "CREATED", "VERIFIED", "BLOCKED"}): raise ValueError()
            if passed:
                if not member["intentRef"] or (receipt["operation"] not in {"provision-members", "verify-members"} and not member["invitationHandle"]): raise ValueError()
                if receipt["operation"] == "invitations":
                    if (member["intentState"] not in {"PENDING", "APPLIED"} or member["grantVersion"] is not None
                            or member["grantOutcome"] != "NOT_REQUESTED"): raise ValueError()
                elif (member["intentState"] != "APPLIED" or member["grantOutcome"] not in {"CREATED", "VERIFIED"}
                      or not all(member[k] for k in ("accountRef", "memberHandle", "grantVersion"))): raise ValueError()
                if member["intentState"] == "APPLIED" and not all(member[k] for k in ("accountRef", "memberHandle")): raise ValueError()
                if member["intentState"] == "PENDING" and any(member[k] is not None for k in ("accountRef", "memberHandle")): raise ValueError()
        subjects = value["privateData"].get("members")
        if passed and receipt["operation"] == "provision-members":
            if (not isinstance(subjects, list) or len(subjects) != 2
                    or any(not isinstance(item, dict) or set(item) != {"slot", "identityRef"} or item["slot"] != slot
                           or not text(item["identityRef"], 512) for slot, item in zip(("A", "B"), subjects))
                    or subjects[0]["identityRef"] == subjects[1]["identityRef"]): raise ValueError()
        elif subjects is not None:
            raise ValueError()
        accounts = [m["accountRef"] for m in members if m["accountRef"]]
        if len(set(accounts + ([receipt["intendedOwnerAccountRef"]] if receipt["intendedOwnerAccountRef"] else []))) != len(accounts) + bool(receipt["intendedOwnerAccountRef"]): raise ValueError()
        for key in ("intentRef", "invitationHandle", "memberHandle"):
            references = [m[key] for m in members if m[key] is not None]
            if len(references) != len(set(references)): raise ValueError()
        if passed and receipt["operation"] == "grants" and receipt["outcome"] != ("CREATED" if any(m["grantOutcome"] == "CREATED" for m in members) else "VERIFIED"):
            raise ValueError()
        if passed and receipt["operation"] == "verify-members" and (receipt["outcome"] != "VERIFIED"
                or any(m["grantOutcome"] != "VERIFIED" for m in members)):
            raise ValueError()
        if request:
            expected = {key: request[key] for key in ("operation", "nonce", "environment", "candidateCommit", "sourceTree", "specificationCommit", "apiBaseUrl")}
            expected.update({key: request["plan"][key] for key in ("planId", "organizationOrigin", "issuerUrl", "spaceRef")})
            if any(receipt[key] != item for key, item in expected.items() if passed or receipt[key] is not None): raise ValueError()
        if previous:
            for key in ("actorRef", "planId", "organizationId", "intendedOwnerAccountRef", "intendedOwnerMemberHandle", "organizationOrigin", "apiBaseUrl", "issuerUrl", "spaceRef"):
                if previous.get(key) is not None and receipt[key] != previous[key]: raise ValueError()
            for before, after in zip(previous["members"], members):
                if any(before[k] is not None and after[k] != before[k] for k in ("intentRef", "invitationHandle", "memberHandle", "accountRef")): raise ValueError()
                if passed and receipt["operation"] == "verify-members" and after["grantVersion"] != before["grantVersion"]: raise ValueError()
        return value
    except (KeyError, TypeError, ValueError, AttributeError, ContractError):
        raise ContractError("dogfood-operator-receipt-invalid") from None


def validate_receipt(value: dict, plan: dict, stage: str, nonce: str, previous: dict | None = None) -> dict:
    try:
        if (set(value) != {"schemaVersion", "planId", "nonce", "stage", "organizationId", "administratorAccountRef",
                           "intendedOwnerAccountRef", "ownerDisplayName", "members", "supportSafe", "containsSecretValues"}
                or value["schemaVersion"] != "weave.dogfood-admin-preparation-receipt/v1"
                or value["planId"] != plan["planId"] or value["nonce"] != nonce or not isinstance(nonce, str)
                or not HEX.fullmatch(nonce) or value["stage"] != stage
                or value["supportSafe"] is not True or value["containsSecretValues"] is not False
                or any(not text(value[key]) for key in ("organizationId", "administratorAccountRef", "intendedOwnerAccountRef"))
                or not text(value["ownerDisplayName"], 160) or len(value["members"]) != 2):
            raise ValueError()
        if previous and any(value[key] != previous[key] for key in
                            ("organizationId", "administratorAccountRef", "intendedOwnerAccountRef", "ownerDisplayName")):
            raise ValueError()
        slots = set()
        for member in value["members"]:
            if (not isinstance(member, dict) or set(member) - {"slot", "email", "displayName", "memberHandle", "invitationHandle", "accountRef", "grantVersion"}
                    or not {"slot", "email", "displayName"} <= set(member)
                    or any(not text(item) for item in member.values())):
                raise ValueError()
            planned, = [entry for entry in plan["members"] if entry["slot"] == member["slot"]]
            if any(member[key] != planned[key] for key in ("slot", "email", "displayName")) or member["slot"] in slots:
                raise ValueError()
            slots.add(member["slot"])
            if stage == "invitations-ready" and not (member.get("invitationHandle") or member.get("accountRef")):
                raise ValueError()
            if stage == "grants-ready" and not all(member.get(key) for key in ("accountRef", "memberHandle", "grantVersion")):
                raise ValueError()
            if stage == "grants-ready" and not re.fullmatch(r'"[^"\r\n]+"', member["grantVersion"]):
                raise ValueError()
            if previous:
                before, = [entry for entry in previous["members"] if entry["slot"] == member["slot"]]
                if any(before.get(key) and member.get(key) != before[key] for key in ("accountRef", "memberHandle", "invitationHandle")):
                    raise ValueError()
        if slots != {"A", "B"}:
            raise ValueError()
        if stage == "grants-ready" and len({member["accountRef"] for member in value["members"]} | {value["intendedOwnerAccountRef"]}) != 3:
            raise ValueError()
        return value
    except (KeyError, TypeError, ValueError):
        raise ContractError("current-administrator-preparation-readback-conflict") from None


def admin_driver(plan: dict, directory: Path, operation: str, proof: dict | None = None) -> dict:
    """Transport private intent through stdin; never approve the visible plan."""
    path = directory / ("admin-" + secrets.token_hex(8) + ".json")
    private_write(path, {})
    invocation = {"schemaVersion": "weave.dogfood-admin-driver-input/v1", "operation": operation,
                  "organizationOrigin": plan["organizationOrigin"], "issuerUrl": plan["issuerUrl"],
                  "plan": plan, "receiptOutputPath": str(path), "timeoutSeconds": 180}
    if proof is not None:
        invocation["proof"] = proof
    try:
        response = subprocess.run(["/usr/bin/swift", str(ROOT / "client/tool/native_macos_appauth_driver.swift"),
                                   "--dogfood-admin-preparation"], input=json.dumps(invocation),
                                  capture_output=True, text=True, timeout=195)
        # No driver output (including AX content) enters public logs.
        markers = [line.strip() for line in response.stdout.splitlines() if line.startswith("DOGFOOD_ADMIN_PREPARATION_RESULT ")]
        if response.returncode:
            categories = {"reviewed-test-member-plan-approval-required", "normal-administrator-authentication-required",
                          "administrator-session-unavailable", "preparation-ui-unavailable", "preparation-state-unverified"}
            found = [category for category in categories if markers == ["DOGFOOD_ADMIN_PREPARATION_RESULT status="
                     + ("incomplete" if response.returncode == 2 else "failed") + " boundary=" + category + " supportSafe=true"]]
            raise ContractError(found[0] if len(found) == 1 else "preparation-ui-unavailable")
        if markers != ["DOGFOOD_ADMIN_PREPARATION_RESULT status=passed boundary=none supportSafe=true"]:
            raise ContractError("preparation-state-unverified")
        return private_read(path)
    except (OSError, subprocess.SubprocessError):
        raise ContractError("preparation-ui-unavailable") from None
    finally:
        path.unlink(missing_ok=True)


def operator_backend(context, candidate: dict, backend: dict, runner, *, allow_isolated: bool = False) -> None:
    from compose_runtime import _bootstrap_boundary_present
    if not ((context.environment == "dogfood" and context.isolated_namespace is None)
            or (allow_isolated and context.environment == "e2e" and context.isolated_namespace is not None)):
        raise ContractError("dogfood-operator-posture-required")
    if (not isinstance(backend, dict) or not isinstance(backend.get("Config"), dict)
            or not isinstance(backend["Config"].get("Labels"), dict) or not isinstance(backend["Config"].get("Env"), list)
            or any(not isinstance(item, str) for item in backend["Config"]["Env"]) or not isinstance(backend.get("State"), dict)):
        raise ContractError("dogfood-operator-candidate-mismatch")
    labels = backend.get("Config", {}).get("Labels", {})
    expected = {"com.massimotter.weave.module": "server",
                "com.massimotter.weave.candidate-commit": candidate["candidateCommit"],
                "com.massimotter.weave.spec-commit": candidate["specificationCommit"],
                "com.massimotter.weave.spec-digest": "sha256:" + hashlib.sha256((ROOT / "specs/weave-specs.lock.json").read_bytes()).hexdigest(),
                "com.docker.compose.project": context.env["WEAVE_COMPOSE_PROJECT"], "com.docker.compose.service": "backend",
                "com.massimotter.weave.namespace": context.env["WEAVE_RESOURCE_PREFIX"]}
    posture = [value.split("=", 1)[1] for value in backend.get("Config", {}).get("Env", []) if value.startswith("WEAVE_RELEASE_POSTURE=")]
    if (not isinstance(backend.get("Id"), str) or not re.fullmatch(r"[0-9a-f]{64}", backend["Id"])
            or not isinstance(backend.get("Image"), str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", backend["Image"])
            or backend.get("State", {}).get("Running") is not True or posture != ["dogfood"]
            or any(labels.get(key) != value for key, value in expected.items()) or _bootstrap_boundary_present(backend)):
        raise ContractError("dogfood-operator-candidate-mismatch")
    from artifact_consumers import ArtifactError, verify_runtime_container
    try:
        verify_runtime_container(backend, candidate["candidateCommit"])
    except ArtifactError:
        raise ContractError("dogfood-operator-candidate-mismatch") from None


def operator_driver(context, backend: dict, plan: dict, candidate: dict, directory: Path,
                    operation: str, previous: dict | None = None, proof: dict | None = None,
                    initial_credentials: dict | None = None) -> dict:
    request = operator_request(context, plan, candidate, operation, previous, proof, initial_credentials)
    encoded = json.dumps(request, ensure_ascii=False, separators=(",", ":")).encode()
    if len(encoded) > 65536:
        raise ContractError("dogfood-operator-request-invalid")
    command = ["docker", "exec", "-i", backend["Id"], "java", "-jar", "/app/app.jar", "dogfood-prepare-members"]
    # The fixed Server command has its own 180s hard deadline. Wait beyond it;
    # killing docker-exec alone would not prove the container JVM was reaped.
    path = directory / ("operator-command-" + secrets.token_hex(8) + ".log")
    try:
        with path.open("x+b") as stream:
            os.fchmod(stream.fileno(), 0o600)
            response = subprocess.run(command, input=encoded, stdout=stream, stderr=subprocess.STDOUT, timeout=210)
            size = stream.tell()
            if not 0 < size <= 2 * 1024 * 1024:
                raise ContractError("dogfood-operator-result-unavailable")
            stream.seek(0)
            output = stream.read().decode("utf-8")
        marker = "WEAVE_DOGFOOD_OPERATOR_PREPARATION_RESULT "
        lines = [line[len(marker):] for line in output.splitlines() if line.startswith(marker)]
        if len(lines) != 1 or len(lines[0].encode()) > 16384:
            raise ContractError("dogfood-operator-result-unavailable")
        envelope = validate_operator_envelope(strict_json(lines[0]), request, previous)
        if (response.returncode == 0) != (envelope["receipt"]["status"] == "passed"):
            raise ContractError("dogfood-operator-result-unavailable")
        return envelope
    except (OSError, ValueError, subprocess.SubprocessError):
        raise ContractError("dogfood-operator-outcome-uncertain") from None
    finally:
        path.unlink(missing_ok=True)


def operator_ledger(context, path: Path, fingerprint: str, fixture_path: Path) -> dict:
    if not path.exists() and not path.is_symlink():
        if fixture_path.exists() or fixture_path.is_symlink():
            raise ContractError("dogfood-preparation-authority-conflict")
        value = {"schemaVersion": "weave.dogfood-operator-preparation-ledger/v1", "authorizationKind": "dogfood-operator",
                 "plan": new_plan(context), "passwords": {slot: secrets.token_urlsafe(32) + "aA1!" for slot in ("A", "B")},
                 "caSha256Fingerprint": fingerprint, "registrationState": {}}
    else:
        value = private_read(path, 65536)
        if value.get("schemaVersion") == "weave.dogfood-admin-preparation-ledger/v1":
            # A never-enacted plan may retain names and test passwords. Issued
            # human authority, activation or an existing fixture cannot convert.
            if (set(value) != {"schemaVersion", "plan", "passwords", "caSha256Fingerprint", "registrationState"}
                    or value.get("registrationState") != {} or fixture_path.exists() or fixture_path.is_symlink()):
                raise ContractError("dogfood-preparation-authority-conflict")
            preparation_ledger(context, path, fingerprint)
            value = {**value, "schemaVersion": "weave.dogfood-operator-preparation-ledger/v1", "authorizationKind": "dogfood-operator"}
        required = {"schemaVersion", "authorizationKind", "plan", "passwords", "caSha256Fingerprint", "registrationState"}
        if (set(value) - (required | {"operatorEnvelope", "lastGrantsEnvelope", "ownerBinding", "fixtureSha256", "candidate"}) or not required <= set(value)
                or value["schemaVersion"] != "weave.dogfood-operator-preparation-ledger/v1"
                or value["authorizationKind"] != "dogfood-operator" or value["caSha256Fingerprint"] != fingerprint
                or not isinstance(value["passwords"], dict) or set(value["passwords"]) != {"A", "B"}
                or any(not text(password, 128) or len(password) < 24 for password in value["passwords"].values())
                or not isinstance(value["registrationState"], dict) or set(value["registrationState"]) - {"A", "B"}
                or any(stage not in {"activation-started", "email-verification-required", "registration-complete"} for stage in value["registrationState"].values())):
            raise ContractError("dogfood-preparation-authority-conflict")
    operator_plan(value["plan"], context)
    if "operatorEnvelope" in value:
        receipt = validate_operator_envelope(value["operatorEnvelope"])["receipt"]
        expected = {key: value["plan"][key] for key in ("planId", "organizationOrigin", "issuerUrl", "spaceRef")}
        expected["apiBaseUrl"] = context.env["WEAVE_API_URL"]
        if any(receipt[key] != item for key, item in expected.items() if receipt[key] is not None):
            raise ContractError("dogfood-preparation-authority-conflict")
    evidence = operator_grant_evidence(value)
    if evidence is not None:
        value["lastGrantsEnvelope"] = evidence
    elif value.get("fixtureSha256") is not None:
        # An older interrupted ledger must not replace lost strong versions
        # with a fresh invitation's intentionally null phase-local fields.
        raise ContractError("dogfood-operator-grant-evidence-required")
    current = value.get("operatorEnvelope", {}).get("receipt", {})
    if (evidence is None and current.get("operation") == "grants"
            and any(member.get("grantVersion") is not None for member in current.get("members", []))):
        raise ContractError("dogfood-operator-grant-evidence-incomplete")
    private_write(path, value)
    return value


def operator_grant_evidence(ledger: dict) -> dict | None:
    current = ledger.get("operatorEnvelope")
    evidence = ledger.get("lastGrantsEnvelope")
    if evidence is not None and current is None:
        raise ContractError("dogfood-preparation-authority-conflict")
    if evidence is None and current:
        receipt = current["receipt"]
        if receipt["status"] == "passed" and receipt["stage"] in {"grants-ready", "provision-members-ready"}:
            evidence = current  # Recover only the exact existing typed proof.
    if evidence is None:
        return None
    receipt = validate_operator_envelope(evidence)["receipt"]
    expected = {key: ledger["plan"][key] for key in ("planId", "organizationOrigin", "issuerUrl", "spaceRef")}
    api = urlsplit(receipt["apiBaseUrl"])
    if (receipt["status"] != "passed" or receipt["stage"] not in {"grants-ready", "provision-members-ready"}
            or any(receipt[key] != item for key, item in expected.items())
            or https_origin(api.scheme + "://" + api.netloc) != ledger["plan"]["apiOrigin"]):
        raise ContractError("dogfood-preparation-authority-conflict")
    if current:
        observed = validate_operator_envelope(current, previous=receipt)["receipt"]
        if (current["privateData"]["ownerDisplayName"] is not None
                and current["privateData"]["ownerDisplayName"] != evidence["privateData"]["ownerDisplayName"]):
            raise ContractError("dogfood-operator-owner-binding-conflict")
        if observed["status"] == "passed" and observed["stage"] in {"grants-ready", "provision-members-ready"}:
            if any(old["grantVersion"] != new["grantVersion"] for old, new in zip(receipt["members"], observed["members"])):
                raise ContractError("dogfood-operator-space-version-conflict")
    return evidence


def preparation_ledger(context, path: Path, fingerprint: str) -> dict:
    if path.exists() or path.is_symlink():
        value = private_read(path, 65536)
        required = {"schemaVersion", "plan", "passwords", "caSha256Fingerprint", "registrationState"}
        if (set(value) - (required | {"invitationReceipt", "grantReceipt", "ownerBinding", "fixtureSha256", "candidate"})
                or not required <= set(value) or value["schemaVersion"] != "weave.dogfood-admin-preparation-ledger/v1"
                or value["caSha256Fingerprint"] != fingerprint):
            raise ContractError("reviewed-test-member-plan-conflict")
        validate_plan(value["plan"], context)
        if (not isinstance(value["passwords"], dict) or set(value["passwords"]) != {"A", "B"}
                or any(not text(password, 128) or len(password) < 24 for password in value["passwords"].values())):
            raise ContractError("private-preparation-record-unsafe")
        if (not isinstance(value["registrationState"], dict) or set(value["registrationState"]) - {"A", "B"}
                or any(stage not in {"activation-started", "email-verification-required", "registration-complete"} for stage in value["registrationState"].values())):
            raise ContractError("private-preparation-record-unsafe")
        if "invitationReceipt" in value:
            validate_receipt(value["invitationReceipt"], value["plan"], "invitations-ready", value["invitationReceipt"]["nonce"])
        return value
    value = {"schemaVersion": "weave.dogfood-admin-preparation-ledger/v1", "plan": new_plan(context),
             "passwords": {slot: secrets.token_urlsafe(32) + "aA1!" for slot in ("A", "B")},
             "caSha256Fingerprint": fingerprint, "registrationState": {}}
    private_write(path, value)
    return value


def ledger_for(fixture_path: Path) -> Path:
    return fixture_path.with_name(fixture_path.stem + ".preparation-ledger.json")


def fixture_binding(context, fixture_path: Path, fixture: dict) -> dict:
    """Private sidecar binds normal observed identities, never an owner session."""
    ledger = private_read(ledger_for(fixture_path), 65536)
    operator = ledger.get("schemaVersion") == "weave.dogfood-operator-preparation-ledger/v1"
    if operator:
        required = {"schemaVersion", "authorizationKind", "plan", "passwords", "caSha256Fingerprint", "registrationState",
                    "operatorEnvelope", "ownerBinding", "candidate", "fixtureSha256"}
        if (set(ledger) - (required | {"lastGrantsEnvelope"}) or not required <= set(ledger) or ledger["authorizationKind"] != "dogfood-operator"):
            raise ContractError("approved-test-member-preparation-binding-required")
        plan = operator_plan(ledger["plan"], context)
        envelope = validate_operator_envelope(ledger["operatorEnvelope"])
        operator_grant_evidence(ledger)
        grants = envelope["receipt"]
        if (grants["planId"] != plan["planId"] or grants["status"] != "passed" or grants["stage"] not in {"grants-ready", "provision-members-ready"}
                or any(grants[key] != plan[key] for key in ("organizationOrigin", "issuerUrl", "spaceRef"))
                or grants["apiBaseUrl"] != context.env["WEAVE_API_URL"]):
            raise ContractError("approved-test-member-preparation-binding-required")
        owner_name = envelope["privateData"]["ownerDisplayName"]
        expected_fixture = {"schemaVersion": "weave.dogfood-functional-identities/v1", "organizationOrigin": plan["organizationOrigin"],
                            "ownerDisplayName": owner_name, "members": [{"email": m["email"], "displayName": m["displayName"],
                                "password": ledger["passwords"][m["slot"]]} for m in plan["members"]]}
        if fixture != expected_fixture:
            raise ContractError("approved-test-member-preparation-binding-required")
    else:
        if set(ledger) != {"schemaVersion", "plan", "passwords", "caSha256Fingerprint", "registrationState", "invitationReceipt", "grantReceipt", "ownerBinding", "candidate", "fixtureSha256"}:
            raise ContractError("approved-test-member-preparation-binding-required")
        plan = validate_plan(ledger["plan"], context)
        invitations = validate_receipt(ledger["invitationReceipt"], plan, "invitations-ready", ledger["invitationReceipt"]["nonce"])
        grants = ledger["grantReceipt"]
        validate_receipt(grants, plan, "grants-ready", grants["nonce"], invitations)
        owner_name = grants["ownerDisplayName"]
    expected_hash = hashlib.sha256(json.dumps(fixture, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    owner = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], grants["intendedOwnerAccountRef"]),
             "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], grants["organizationId"])}
    if (ledger.get("schemaVersion") != ("weave.dogfood-operator-preparation-ledger/v1" if operator else "weave.dogfood-admin-preparation-ledger/v1")
            or ledger.get("caSha256Fingerprint") != public_setup(context)[1]["caSha256Fingerprint"]
            or ledger.get("fixtureSha256") != expected_hash or ledger.get("ownerBinding") != owner
            or fixture.get("ownerDisplayName") != owner_name):
        raise ContractError("approved-test-member-preparation-binding-required")
    return {"owner": owner, "members": {member["slot"]: {
        "accountDigest": identity_digest("weave-account", plan["issuerUrl"], member["accountRef"]),
        "organizationDigest": owner["organizationDigest"]} for member in grants["members"]}}


def stock_action_link(candidate: str, issuer: str, verification: bool) -> bool:
    """Exact stock issuer action only; never infer HTTPS or a realm from display text."""
    try:
        if (not isinstance(candidate, str) or not isinstance(issuer, str)
                or not 1 <= len(candidate) <= 8192 or not 1 <= len(issuer) <= 2048
                or any(character.isspace() or ord(character) < 32 for character in issuer)):
            return False
        expected, parsed = urlsplit(issuer), urlsplit(candidate)
        if (expected.scheme != "https" or not expected.hostname or expected.username is not None or expected.password is not None
                or expected.query or expected.fragment or not expected.path.startswith("/realms/")
                or any(part in ("", ".", "..") for part in expected.path.split("/")[2:])
                or "%" in expected.path or "\\" in expected.path):
            return False
        # Accessing .port also rejects malformed/out-of-range explicit ports.
        if not 1 <= (443 if expected.port is None else expected.port) <= 65535 or not 1 <= (443 if parsed.port is None else parsed.port) <= 65535:
            return False
        path = expected.path + ("/login-actions/action-token" if verification else "/protocol/openid-connect/registrations")
        query = parse_qs(parsed.query, keep_blank_values=True)
        return (parsed.scheme == "https" and parsed.netloc == expected.netloc and parsed.path == path
                and parsed.username is None and parsed.password is None and not parsed.fragment
                and not any(character.isspace() or ord(character) < 32 for character in candidate)
                and query.get("client_id") == ["account"]
                and (verification or query.get("response_type") == ["code"])
                and len(query.get("key" if verification else "token", [])) == 1
                and bool(query["key" if verification else "token"][0]))
    except (ValueError, TypeError, AttributeError):
        return False


def action_links(message: dict, issuer: str, verification: bool) -> set[str]:
    """Select only stock, exact-issuer actions; URLs remain private in memory."""
    strings = []
    def collect(value):
        if isinstance(value, str): strings.append(value)
        elif isinstance(value, dict):
            for child in value.values(): collect(child)
        elif isinstance(value, list):
            for child in value: collect(child)
    collect(message)
    found = set()
    for raw in strings:
        decoded = html.unescape(raw.replace("=\r\n", "").replace("=\n", ""))
        decoded = re.sub("=3d", "=", decoded, flags=re.I)
        for candidate in re.findall(r"https://[^\s\"'<>]+", decoded):
            if stock_action_link(candidate, issuer, verification):
                found.add(candidate)
    return found


def await_mail_link(context, email: str, issuer: str, verification: bool, *, timeout=60) -> str:
    from compose_runtime import _mailpit_addresses
    port = context.env["WEAVE_MAILPIT_WEB_HOST_PORT"]
    if not str(port).isdigit() or not 1 <= int(port) <= 65535:
        raise ContractError("private-test-member-inbox-unavailable")
    base = "http://127.0.0.1:" + str(port) + "/api/v1/"
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs): return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    def fetch(suffix):
        with opener.open(base + suffix, timeout=10) as response:
            payload = response.read(2 * 1024 * 1024 + 1)
            if response.status != 200 or len(payload) > 2 * 1024 * 1024:
                raise ValueError()
            return json.loads(payload)
    deadline = time.monotonic() + timeout
    try:
        while time.monotonic() < deadline:
            matches = set()
            start = 0
            while True:
                payload = fetch(f"messages?start={start}&limit=100")
                messages = payload.get("messages")
                if not isinstance(messages, list) or type(payload.get("total")) is not int or not 0 <= payload["total"] <= 10000:
                    raise ValueError()
                for summary in messages:
                    if not isinstance(summary, dict): raise ValueError()
                    if email.casefold() not in _mailpit_addresses(summary.get("To", [])): continue
                    identifier = summary.get("ID")
                    if not text(identifier, 128): raise ValueError()
                    message = fetch("message/" + quote(identifier, safe=""))
                    if email.casefold() not in _mailpit_addresses(message.get("To", [])): raise ValueError()
                    matches.update(action_links(message, issuer, verification))
                start += len(messages)
                if start >= payload["total"]: break
                if not messages or start >= 10000 or time.monotonic() >= deadline: raise ValueError()
            if len(matches) > 1:
                raise ContractError("private-test-member-inbox-ambiguous")
            if matches: return matches.pop()
            if time.monotonic() + 1 < deadline: time.sleep(1)
        raise ContractError("test-member-email-verification-required" if verification else "test-member-activation-mail-required")
    except (OSError, ValueError, TypeError, KeyError):
        raise ContractError("private-test-member-inbox-unavailable") from None


def registration(runner, actor: dict, fixture: dict) -> str:
    from dogfood_functional_smoke import prepare_browser_fixture, SmokeError
    identifier = actor.get("id")
    activation, issuer = fixture.get("activationUrl"), fixture.get("issuerUrl")
    if (actor.get("physical") is not False or not isinstance(identifier, str)
            or not re.fullmatch(r"[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}", identifier)
            or actor.get("destination") != "platform=iOS Simulator,id=" + identifier
            or fixture.get("schemaVersion") != "weave.dogfood-test-registration/v1"
            or fixture.get("phase") != "ordinary-registration"
            or fixture.get("appBundleIdentifier") != "com.massimotter.weave"
            or not isinstance(activation, str) or not isinstance(issuer, str)
            or not any(stock_action_link(activation, issuer, verification) for verification in (False, True))):
        raise ContractError("normal-test-member-registration-required")
    private = prepare_browser_fixture(actor["products"], fixture)
    try:
        # The stock link is delivered only in the private fixture. XCTest opens
        # it through XCUISystem; invitation URLs must never enter process argv.
        plan, = actor["products"].glob("*.xctestrun")
        output = runner.run(["xcodebuild", "test-without-building", "-xctestrun", str(plan),
            "-destination", actor["destination"], "-parallel-testing-enabled", "NO",
            "-only-testing:BrowserDriver/BrowserDriver/testOrdinaryTestRegistration",
            "-resultBundlePath", str(runner.directory / f"registration-{runner.sequence + 1}")], timeout=240)
        prefix = "NATIVE_IOS_REGISTRATION_RESULT "
        markers = [line[line.index(prefix):].strip() for line in output.splitlines() if prefix in line]
        complete = prefix + "status=passed stage=registration-complete supportSafe=true"
        pending = prefix + "status=incomplete stage=email-verification-required supportSafe=true"
        if markers == [complete]: return "complete"
        if markers == [pending]: return "verification"
        sign_in = prefix + "status=incomplete stage=ordinary-app-sign-in-required supportSafe=true"
        if markers == [sign_in]: return "sign-in"
        expired = prefix + "status=incomplete stage=authentication-session-expired supportSafe=true"
        if markers == [expired]: raise ContractError("normal-test-member-authentication-session-expired")
        raise ContractError("normal-test-member-registration-required")
    except SmokeError:
        raise ContractError("normal-test-member-registration-required") from None
    finally:
        private.unlink(missing_ok=True)


def build_actors(runner, context, candidate: str, devices: list[str]) -> dict:
    from dogfood_functional_smoke import CLIENT, BUNDLE, select_runtime, build_info
    from artifact_consumers import ArtifactError, native_bundle, copy_driver_products
    try:
        manifest, app, products = native_bundle("ios-simulator", "ordinary", candidate)
    except ArtifactError:
        raise ContractError("verified-hosted-simulator-artifact-required") from None
    build_info(app, manifest["buildMode"])
    runtime, device_type = select_runtime()
    actors = {}
    for slot in ("A", "B"):
        udid = runner.run(["xcrun", "simctl", "create", "weave-prepare-" + secrets.token_hex(8) + "-" + slot, device_type, runtime]).strip()
        if not re.fullmatch(r"[0-9A-Fa-f-]{36}", udid):
            raise ContractError("owned-preparation-simulator-identity-invalid")
        devices.append(udid)
        runner.run(["xcrun", "simctl", "boot", udid])
        runner.run(["xcrun", "simctl", "bootstatus", udid, "-b"], timeout=180)
        runner.run(["xcrun", "simctl", "keychain", udid, "add-root-cert", str(context.tls_root / "ca.pem")])
        runner.run(["xcrun", "simctl", "install", udid, str(app)])
        derived = runner.directory / ("driver-" + slot)
        copied_products = copy_driver_products(products, derived / "Build/Products")
        actors[slot] = {"id": udid, "physical": False, "destination": "platform=iOS Simulator,id=" + udid,
                        "products": copied_products, "app": app}
    return actors


def member_proof(runner, actor, context, plan: dict, member: dict, password: str, invitation: dict,
                 registration_state: str | None = None, record_registration=lambda _state: None,
                 *, allow_registration: bool = True) -> dict:
    from dogfood_functional_smoke import BUNDLE, SmokeError, restart, verify_discovered_routing, verify_installed_build
    def authenticate():
        restart(runner, actor)
        runner.ui(actor, {"organizationOrigin": plan["organizationOrigin"]}, "testOrdinaryOrganizationEntry")
        runner.ui(actor, {"email": member["email"], "password": password, "displayName": member["displayName"],
                         "issuerHost": urlsplit(plan["issuerUrl"]).hostname, "appBundleIdentifier": BUNDLE,
                         "phase": "initial", "nativeAuthCompletionPath": "", "ordinaryOrganizationOrigin": plan["organizationOrigin"]},
                  "testProductionBrowser")
    authenticated = False
    if not allow_registration:
        # Recovery of a started member is ordinary sign-in only. In particular,
        # an auth failure cannot authorize mail, activation replay or a reset.
        authenticate()
        authenticated = True
    interrupted = registration_state in {"activation-started", "email-verification-required"}
    if interrupted and not authenticated:
        # An invitation may already be consumed even when XCTest never emitted
        # its ACK. Normal sign-in can prove completion; never re-register or
        # replay that invitation on the strength of a missing checkpoint.
        try:
            authenticate()
            authenticated = True
        except SmokeError as error:
            if str(error) != "normal-test-member-browser-authentication-required": raise
    if not authenticated and not invitation.get("accountRef") and registration_state != "registration-complete":
        verification = interrupted
        try:
            activation = await_mail_link(context, member["email"], plan["issuerUrl"], verification, timeout=10 if interrupted else 60)
        except ContractError as error:
            if (not interrupted or str(error) != "test-member-email-verification-required" or not invitation.get("invitationHandle")):
                raise
            # The process may have stopped immediately after the durable
            # activation-started checkpoint. Only a fresh, unchanged normal
            # invitation readback permits one bounded retry of that SAME link.
            # Mailbox/network failures never authorize this branch, and the
            # provider must still recognize its stock registration page.
            verification = False
            activation = await_mail_link(context, member["email"], plan["issuerUrl"], False)
        fixture = {"schemaVersion": "weave.dogfood-test-registration/v1", "phase": "ordinary-registration",
                   "planId": plan["planId"], "nonce": secrets.token_hex(16), "slot": member["slot"],
                   "organizationOrigin": plan["organizationOrigin"], "issuerUrl": plan["issuerUrl"],
                   "activationUrl": activation, "email": member["email"], "displayName": member["displayName"],
                   "password": password, "appBundleIdentifier": BUNDLE}
        record_registration("activation-started" if not verification else "email-verification-required")
        result = registration(runner, actor, fixture)
        if result == "verification":
            record_registration("email-verification-required")
            fixture.update(nonce=secrets.token_hex(16), activationUrl=await_mail_link(context, member["email"], plan["issuerUrl"], True))
            result = registration(runner, actor, fixture)
            if result not in {"complete", "sign-in"}:
                raise ContractError("test-member-email-verification-required")
        elif result not in {"complete", "sign-in"}:
            raise ContractError("normal-test-member-registration-required")
        # Neither provider info nor a public handoff proves the member's app
        # session/identity. Keep the existing pending ledger state until proof.
    if not authenticated: authenticate()
    restart(runner, actor)
    fixture = {"schemaVersion": "weave.ordinary-product-ui/v1", "runId": "prepare-" + plan["planId"],
               "nonce": secrets.token_hex(16), "operation": "identity", "organizationOrigin": plan["organizationOrigin"],
               "conversation": "", "participant": "", "folder": "weave-smoke-prepare", "privateFolder": "weave-smoke-private-prepare",
               "filename": "weave-dual-smoke-prepare.txt", "filePayload": "", "event": "", "expected": {}}
    result = runner.ui(actor, fixture)
    verify_installed_build(runner, actor)
    verify_discovered_routing(runner, actor, context)
    resources = result.get("resources", {})
    if not all(isinstance(resources.get(key), str) and HASH.fullmatch(resources[key]) for key in ("account", "organization")):
        raise ContractError("normal-test-member-identity-proof-required")
    record_registration("registration-complete")
    return {"slot": member["slot"], "accountDigest": resources["account"], "organizationDigest": resources["organization"]}


def verify_proof(proof: dict, receipt: dict, plan: dict) -> None:
    if (proof.get("schemaVersion") != "weave.dogfood-admin-preparation-proof/v1" or proof.get("planId") != plan["planId"]
            or not isinstance(proof.get("nonce"), str) or not HEX.fullmatch(proof["nonce"])
            or set(proof) != {"schemaVersion", "planId", "nonce", "members"}
            or len(proof.get("members", [])) != 2):
        raise ContractError("normal-test-member-identity-proof-conflict")
    accounts = set()
    for observed in proof["members"]:
        members = [member for member in receipt["members"] if member["slot"] == observed.get("slot")]
        if (len(members) != 1 or set(observed) != {"slot", "accountDigest", "organizationDigest"}
                or observed["accountDigest"] != identity_digest("weave-account", plan["issuerUrl"], members[0]["accountRef"])
                or observed["organizationDigest"] != identity_digest("weave-organization", plan["issuerUrl"], receipt["organizationId"])):
            raise ContractError("normal-test-member-identity-proof-conflict")
        accounts.add(observed["accountDigest"])
    if len(accounts) != 2:
        raise ContractError("independent-test-member-identities-required")


def publish_fixture(path: Path, ledger_path: Path, ledger: dict, grants: dict, proof: dict, candidate: dict) -> None:
    plan = ledger["plan"]
    operator = ledger.get("schemaVersion") == "weave.dogfood-operator-preparation-ledger/v1"
    if operator:
        envelope = validate_operator_envelope(grants)
        grants = envelope["receipt"]
        if (ledger.get("authorizationKind") != "dogfood-operator" or grants["status"] != "passed" or grants["stage"] not in {"grants-ready", "provision-members-ready"}
                or grants["nonce"] != proof["nonce"] or grants["planId"] != plan["planId"]
                or any(grants[key] != candidate[key] for key in candidate)):
            raise ContractError("dogfood-preparation-authority-conflict")
        owner_name = envelope["privateData"]["ownerDisplayName"]
    else:
        validate_receipt(grants, plan, "grants-ready", proof["nonce"], ledger["invitationReceipt"])
        owner_name = grants["ownerDisplayName"]
    verify_proof(proof, grants, plan)
    fixture = {"schemaVersion": "weave.dogfood-functional-identities/v1", "organizationOrigin": plan["organizationOrigin"],
               "ownerDisplayName": owner_name, "members": [
                   {"email": member["email"], "displayName": member["displayName"], "password": ledger["passwords"][member["slot"]]}
                   for member in sorted(plan["members"], key=lambda member: member["slot"])]}
    if (path.exists() or path.is_symlink()) and private_read(path) != fixture:
        raise ContractError("approved-test-member-fixture-conflict")
    expected = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], grants["intendedOwnerAccountRef"]),
                "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], grants["organizationId"])}
    ledger.update(ownerBinding=expected, candidate=candidate,
                  fixtureSha256=hashlib.sha256(json.dumps(fixture, sort_keys=True, separators=(",", ":")).encode()).hexdigest())
    if operator:
        ledger["operatorEnvelope"] = envelope
        operator_grant_evidence(ledger)
        ledger["lastGrantsEnvelope"] = envelope
    else: ledger["grantReceipt"] = grants
    # Ledger first: a crash can leave an incomplete pair, never an accepted fixture
    # without its exact current authority/identity sidecar.
    private_write(ledger_path, ledger)
    private_write(path, fixture)


def operator_phases(context, backend: dict, candidate: dict, ledger_path: Path, ledger: dict, runner, actors: dict) -> tuple:
    from compose_runtime import _service_container
    plan = ledger["plan"]
    def invoke(operation, current_plan, proof=None):
        current = _service_container(context, "backend", deadline=time.monotonic() + 30)
        operator_backend(context, candidate, current, runner)
        if current["Id"] != backend["Id"] or current["Image"] != backend["Image"]:
            raise ContractError("dogfood-operator-candidate-mismatch")
        latest = ledger.get("operatorEnvelope")
        durable_grants = operator_grant_evidence(ledger)
        previous = durable_grants if operation == "grants" and durable_grants else latest
        envelope = operator_driver(context, current, current_plan, candidate, runner.directory, operation,
                                   previous["receipt"] if previous else None, proof)
        if latest:
            # A retained grants receipt supplies old ETags; the independently
            # fresh invitation readback still pins current identity references.
            validate_operator_envelope(envelope, previous=latest["receipt"])
        if (previous and previous["privateData"]["ownerDisplayName"] is not None
                and envelope["privateData"]["ownerDisplayName"] is not None and envelope["privateData"] != previous["privateData"]):
            raise ContractError("dogfood-operator-owner-binding-conflict")
        receipt = envelope["receipt"]
        if receipt["planId"] == plan["planId"]:
            # Partial/blocked current references must survive interruption;
            # never clear them or relabel them as human administrator work.
            ledger["operatorEnvelope"] = envelope
            private_write(ledger_path, ledger)
            if receipt["status"] == "passed" and receipt["stage"] in {"grants-ready", "provision-members-ready"}:
                operator_grant_evidence(ledger)
                ledger["lastGrantsEnvelope"] = envelope
                private_write(ledger_path, ledger)
        if receipt["status"] != "passed":
            raise ContractError("dogfood-operator-" + receipt["boundary"])
        return envelope
    retained = ledger.get("operatorEnvelope")
    retained_states = dict(ledger["registrationState"])
    proven = {}
    def prove(member, invitation, *, auth_only=False):
        slot = member["slot"]
        def record_registration(stage):
            ledger["registrationState"][slot] = stage
            private_write(ledger_path, ledger)
        arguments = (runner, actors[slot], context, plan, member, ledger["passwords"][slot],
                     invitation, ledger["registrationState"].get(slot), record_registration)
        return member_proof(*arguments, allow_registration=False) if auth_only else member_proof(*arguments)
    try:
        invitations = invoke("invitations", {**plan, "nonce": secrets.token_hex(16)})["receipt"]
    except ContractError as failure:
        if str(failure) != "dogfood-operator-test-member-not-activated" or retained is None:
            raise
        # The blocked result is not invitation authority. Only a previously
        # validated private operator plan with started slots permits sign-in.
        operator_plan(plan, context)
        previous = validate_operator_envelope(retained)["receipt"]
        expected = {key: plan[key] for key in ("planId", "organizationOrigin", "issuerUrl", "spaceRef")}
        expected["apiBaseUrl"] = context.env["WEAVE_API_URL"]
        if (ledger.get("schemaVersion") != "weave.dogfood-operator-preparation-ledger/v1"
                or ledger.get("authorizationKind") != "dogfood-operator"
                or any(previous[key] != value for key, value in expected.items())
                or (previous["status"] != "passed" and previous["boundary"] != "test-member-not-activated")):
            raise failure
        started = [member for member in plan["members"] if retained_states.get(member["slot"]) in
                   {"activation-started", "email-verification-required", "registration-complete"}]
        if not started:
            raise failure
        for member in sorted(started, key=lambda member: (retained_states[member["slot"]] != "registration-complete", member["slot"])):
            prior, = [row for row in previous["members"] if row["slot"] == member["slot"]]
            if not prior["intentRef"] or not prior["invitationHandle"] or prior["intentState"] in {"FAILED", "EXPIRED"}:
                raise failure
            proven[member["slot"]] = prove(member, {}, auth_only=True)
        # Never reuse the blocked receipt, even after a successful app login.
        invitations = invoke("invitations", {**plan, "nonce": secrets.token_hex(16)})["receipt"]
    proof = {"schemaVersion": "weave.dogfood-admin-preparation-proof/v1", "planId": plan["planId"],
             "nonce": secrets.token_hex(16), "members": []}
    for member in plan["members"]:
        invitation, = [value for value in invitations["members"] if value["slot"] == member["slot"]]
        if member["slot"] not in proven:
            proven[member["slot"]] = prove(member, invitation)
        proof["members"].append(proven[member["slot"]])
    # Ordinary own-app reconciliation must be reflected by a fresh passed
    # APPLIED readback before any conditional grant request is admitted.
    invitations = invoke("invitations", {**plan, "nonce": secrets.token_hex(16)})["receipt"]
    if any(member["intentState"] != "APPLIED" for member in invitations["members"]):
        raise ContractError("dogfood-operator-test-member-not-activated")
    verify_proof(proof, invitations, plan)
    envelope = invoke("grants", {**plan, "nonce": proof["nonce"]}, proof)
    verify_proof(proof, envelope["receipt"], plan)
    return envelope, proof


def prepare_members(context, result: dict, fixture_path: Path, ledger_path: Path, authorization_mode: str = "operator") -> dict:
    if authorization_mode == "console":
        return prepare_members_onboarding(context, result, fixture_path, ledger_path, authorization_mode)
    if authorization_mode != "operator" or ledger_path != ledger_for(fixture_path):
        raise ContractError("dogfood-preparation-authorization-invalid")
    from prepare_member_scenario import prepare_context
    result.update(prepare_context(context, fixture_path))
    return result


def prepare_members_onboarding(context, result: dict, fixture_path: Path, ledger_path: Path, authorization_mode: str = "operator") -> dict:
    """Explicit onboarding diagnostic; prepared collaboration does not invoke this browser setup."""
    from compose_runtime import _service_container, verify_dogfood_identity
    from dogfood_functional_smoke import Runner, SmokeError
    if result.get("localTrustStatus") != "passed": return result
    if authorization_mode not in {"operator", "console"}:
        raise ContractError("dogfood-preparation-authorization-invalid")
    result.update(stage="operator-preparation" if authorization_mode == "operator" else "administrator-preparation",
                  authorizationKind="dogfood-operator" if authorization_mode == "operator" else "human-administrator",
                  boundary="operation-incomplete" if authorization_mode == "operator" else "normal-administrator-authentication-required")
    devices = []
    previous_term = signal.getsignal(signal.SIGTERM)
    def terminate(_number, _frame):
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        raise ContractError("administrator-preparation-terminated")
    signal.signal(signal.SIGTERM, terminate)
    try:
        if authorization_mode == "operator" and (context.environment != "dogfood" or context.isolated_namespace is not None):
            raise ContractError("dogfood-operator-posture-required")
        with _dogfood_session_lock(context), tempfile.TemporaryDirectory(prefix="weave-dogfood-preparation-") as temporary:
            # Canonicalize only this newly created operation-owned directory:
            # macOS's default OS temp root may use the /var -> /private/var alias.
            directory = Path(temporary).resolve(strict=True); directory.chmod(0o700)
            runner = Runner(directory)
            try:
                candidate = {"candidateCommit": runner.run(["git", "rev-parse", "HEAD"]).strip(),
                             "sourceTree": runner.run(["git", "rev-parse", "HEAD^{tree}"]).strip(),
                             "specificationCommit": json.loads((ROOT / "specs/weave-specs.lock.json").read_text())["specCorpus"]["gitCommit"]}
                if runner.run(["git", "status", "--porcelain"]):
                    raise ContractError("clean-exact-candidate-required")
                verify_dogfood_identity(context)
                before = _service_container(context, "backend", deadline=time.monotonic() + 30)
                from artifact_consumers import verify_runtime_container
                verify_runtime_container(before, candidate["candidateCommit"])
                if authorization_mode == "operator":
                    operator_backend(context, candidate, before, runner)
                    ledger = operator_ledger(context, ledger_path, result["trust"]["caSha256Fingerprint"], fixture_path)
                else:
                    ledger = preparation_ledger(context, ledger_path, result["trust"]["caSha256Fingerprint"])
                actors = build_actors(runner, context, candidate["candidateCommit"], devices)
                plan = ledger["plan"]
                if authorization_mode == "operator":
                    grants, proof = operator_phases(context, before, candidate, ledger_path, ledger, runner, actors)
                else:
                    current_plan = {**plan, "nonce": secrets.token_hex(16)}
                    observed = admin_driver(current_plan, directory, "invitations")
                    if observed.get("stage") == "grants-ready":
                        # Only the Console can refresh its still-approved state.
                        validate_receipt(observed, plan, "grants-ready", current_plan["nonce"], ledger.get("invitationReceipt"))
                        if "invitationReceipt" not in ledger:
                            raise ContractError("private-preparation-record-unsafe")
                        invitations = ledger["invitationReceipt"]
                    else:
                        invitations = validate_receipt(observed, plan, "invitations-ready", current_plan["nonce"], ledger.get("invitationReceipt"))
                    ledger["invitationReceipt"] = invitations
                    private_write(ledger_path, ledger)
                    proof = {"schemaVersion": "weave.dogfood-admin-preparation-proof/v1", "planId": plan["planId"],
                             "nonce": secrets.token_hex(16), "members": []}
                    for member in sorted(plan["members"], key=lambda value: value["slot"]):
                        invitation, = [value for value in invitations["members"] if value["slot"] == member["slot"]]
                        def record_registration(stage):
                            ledger["registrationState"][member["slot"]] = stage
                            private_write(ledger_path, ledger)
                        proof["members"].append(member_proof(runner, actors[member["slot"]], context, plan, member,
                            ledger["passwords"][member["slot"]], invitation, ledger["registrationState"].get(member["slot"]), record_registration))
                    grants = validate_receipt(admin_driver(plan, directory, "grants", proof), plan, "grants-ready", proof["nonce"], invitations)
                    verify_proof(proof, grants, plan)
                after = _service_container(context, "backend", deadline=time.monotonic() + 30)
                if before.get("Image") != after.get("Image"):
                    raise ContractError("deployed-image-changed-during-preparation")
                if authorization_mode == "operator":
                    operator_backend(context, candidate, after, runner)
                    if before["Id"] != after["Id"]:
                        raise ContractError("dogfood-operator-candidate-mismatch")
                # Do not publish until owned simulator cleanup also succeeds.
                pending = (ledger, grants, proof, candidate)
            finally:
                signal.signal(signal.SIGTERM, signal.SIG_IGN)
                runner.deadline = time.monotonic() + 120
                clean = True
                for device in devices:
                    try:
                        runner.run(["xcrun", "simctl", "delete", device], timeout=30)
                    except (SmokeError, OSError, subprocess.SubprocessError): clean = False
                if not clean: raise ContractError("owned-preparation-simulator-cleanup-required")
                result["ownedPreparationSimulatorsRemoved"] = True
            publish_fixture(fixture_path, ledger_path, *pending)
            result.update(stage="test-members", status="passed", preparationComplete=True, boundary="ordinary-functional-smoke-required",
                          humanAction="Continue the protected ordinary functional smoke and retained-owner iPhone preparation; preparation is not human acceptance.")
    except (ContractError, SmokeError) as error:
        category = str(error)
        if not re.fullmatch(r"[a-z][a-z0-9-]{1,100}", category): category = "administrator-preparation-unavailable"
        result.update(status="incomplete", preparationComplete=False, boundary=category)
        result["humanAction"] = ("Review the two named test members and exact view/edit grants in the normal Admin Console, then approve that displayed plan."
                                 if str(error) == "reviewed-test-member-plan-approval-required"
                                 else "Complete normal administrator sign-in in the trusted Admin Console and rerun dogfoodPrepare."
                                 if str(error) in {"normal-administrator-authentication-required", "administrator-session-unavailable"}
                                 else "Review the current private preparation state and the reported boundary; no owner credentials or access were replaced.")
    finally:
        signal.signal(signal.SIGTERM, previous_term)
    return result


def native_trust(descriptor: dict) -> list[dict]:
    if sys.platform != "darwin":
        raise ContractError("macos-native-trust-verification-required")
    origins = [descriptor[key] for key in ("organizationOrigin", "apiOrigin", "issuerOrigin")]
    try:
        command = ["/usr/bin/swift", str(ROOT / "infra/weave-workspace/scripts/dogfood_native_trust.swift"),
                   descriptor["caSha256Fingerprint"], *origins]
        response = subprocess.run(command, capture_output=True, text=True, timeout=75)
        value = json.loads(response.stdout)
        if (set(value) != {"schemaVersion", "checks", "supportSafe", "containsSecretValues"}
                or value["schemaVersion"] != "weave.dogfood-native-trust-check/v1"
                or value["supportSafe"] is not True or value["containsSecretValues"] is not False
                or not isinstance(value["checks"], list) or len(value["checks"]) != 3
                or response.returncode not in (0, 2)):
            raise ValueError()
        for origin, check in zip(origins, value["checks"]):
            if (not isinstance(check, dict) or set(check) - {"origin", "status", "category", "nativeCode", "httpStatus"}
                    or check.get("origin") != origin or type(check.get("nativeCode")) is not int
                    or check.get("status") not in {"passed", "incomplete"}
                    or check.get("category") not in {"trusted", "tls-untrusted", "tls-certificate-invalid", "tls-handshake-failed", "tls-ca-mismatch", "dns", "timeout", "transport"}
                    or (check["status"] == "passed" and (check["category"] != "trusted"
                        or check["nativeCode"] != 0 or type(check.get("httpStatus")) is not int
                        or not 100 <= check["httpStatus"] <= 599))):
                raise ValueError()
        if (response.returncode == 0) != all(check["status"] == "passed" for check in value["checks"]):
            raise ValueError()
        return value["checks"]
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        raise ContractError("native-trust-probe-unavailable") from None


def preflight(context, local_bundle: Path) -> dict:
    with _dogfood_session_lock(context):
        descriptor = write_public_setup(context)
        write_public_setup(context, local_bundle)
        result = {"schemaVersion": "weave.dogfood-preparation/v1", "stage": "local-trust",
                  "status": "incomplete", "preparationComplete": False,
                  "environment": "dogfood", "trust": descriptor, "supportSafe": True,
                  "containsSecretValues": False, "verifiedAtUtc": datetime.now(timezone.utc).isoformat()}
        try:
            checks = native_trust(descriptor)
            result["originChecks"] = checks
            result["localTrustStatus"] = "passed" if all(c["status"] == "passed" for c in checks) else "incomplete"
            categories = {check["category"] for check in checks if check["status"] != "passed"}
            result["boundary"] = ("reviewed-tls-ca-mismatch" if "tls-ca-mismatch" in categories
                                  else "reviewed-tls-certificate-invalid" if categories & {"tls-certificate-invalid", "tls-handshake-failed"}
                                  else "macos-ca-trust-required" if "tls-untrusted" in categories
                                  else "native-dns-unavailable" if "dns" in categories
                                  else "native-connectivity-required" if categories
                                  else "administrator-preparation-required")
        except ContractError as error:
            result.update(localTrustStatus="incomplete", boundary=str(error))
        result["humanAction"] = ("Compare the local CA fingerprint, then explicitly install and trust that CA in macOS; rerun dogfoodPrepare."
                                 if result["boundary"] == "macos-ca-trust-required"
                                 else "Complete normal administrator preparation after local HTTPS trust is verified."
                                 if result["boundary"] == "administrator-preparation-required"
                                 else "Verify the deployed certificates match the reviewed persistent CA and validity; do not replace or trust a different CA."
                                 if result["boundary"] in {"reviewed-tls-ca-mismatch", "reviewed-tls-certificate-invalid"}
                                 else "Restore ordinary reviewed DNS/HTTPS connectivity and rerun dogfoodPrepare; do not bypass certificate validation.")
        return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--identity-file", type=Path, default=Path.home() / ".weave/dogfood/functional-smoke-identities.json")
    parser.add_argument("--authorization-mode", choices=("operator", "console"), default="operator")
    parser.add_argument("--onboarding-diagnostic", action="store_true")
    args = parser.parse_args()
    context = load_context("dogfood", ROOT / "infra/weave-workspace", str(effective_reviewed_env_file(args.env_file)))
    result = preflight(context, Path.home() / ".weave/dogfood/public-setup")
    preparation = prepare_members_onboarding if args.onboarding_diagnostic else prepare_members
    result = preparation(context, result, args.identity_file, ledger_for(args.identity_file), authorization_mode=args.authorization_mode)
    output = args.output or context.generated_root / "preparation/trust-result.json"
    _no_symlink(output)
    _private_json(output, result)
    print("DOGFOOD_PREPARATION_RESULT status=" + result["status"] + " stage=" + result["stage"] + " boundary=" + result["boundary"] + " supportSafe=true")
    # Completed member prerequisites are never functional or human acceptance.
    return 0 if result.get("preparationComplete") is True else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContractError, OSError, ValueError):
        print("DOGFOOD_PREPARATION_RESULT status=incomplete stage=local-trust boundary=reviewed-dogfood-preparation-unavailable supportSafe=true")
        raise SystemExit(2)
