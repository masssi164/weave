#!/usr/bin/env python3
"""One secret-free member scenario consumed by real product test drivers.

This module resolves input data and validates observed private bindings. It does
not provision accounts, grant access, inject app sessions, or write product data.
The existing preparation owners perform those operations through Weave services.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import string
import sys


ROOT = Path(__file__).resolve().parents[1]
SCENARIO_PATH = ROOT / "fixtures/member-client-baseline/v1.json"
SCHEMA = "weave.member-client-scenario/v1"
RESOLVED_SCHEMA = "weave.member-client-scenario-resolved/v1"
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,159}")
CONTENT_FIELDS = {
    "conversation", "messageA", "messageB", "event", "updatedEvent", "folder",
    "privateFolder", "initialFileName", "replacementFileName", "initialContent",
    "replacementContent",
}


class ScenarioError(ValueError):
    """A shared scenario or its observed member binding is inconsistent."""


def _strict_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ScenarioError("member-scenario-duplicate-field")
        value[key] = item
    return value


def _canonical(value: dict) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def load_scenario(path: Path = SCENARIO_PATH) -> dict:
    """Read the versioned recipe; role/Space semantics cannot be overridden by a runner."""
    try:
        value = json.loads(path.read_text(), object_pairs_hook=_strict_object)
        if (set(value) != {"schemaVersion", "scenarioId", "version", "requiredDomains", "sharedSpace", "actors", "content"}
                or value["schemaVersion"] != SCHEMA
                or value["scenarioId"] != "member-client-baseline"
                or type(value["version"]) is not int or value["version"] != 1
                or value["requiredDomains"] != ["chat", "files", "calendar"]
                or value["sharedSpace"] != {"logicalId": "shared-space", "spaceRef": "workspace-default"}
                or value["actors"] != [
                    {"slot": slot, "logicalId": "member-" + slot.lower(), "organizationRole": "member",
                     "spacePermissions": ["VIEW", "EDIT"]} for slot in ("A", "B")]
                or set(value["content"]) != CONTENT_FIELDS):
            raise ScenarioError("member-scenario-contract-invalid")
        for template in value["content"].values():
            if not isinstance(template, str) or not template or len(template) > 512:
                raise ScenarioError("member-scenario-content-invalid")
            try:
                fields = list(string.Formatter().parse(template))
            except ValueError as failure:
                raise ScenarioError("member-scenario-template-invalid") from failure
            if (not any(field == "runId" for _, field, _, _ in fields)
                    or any(field not in (None, "runId") or spec or conversion
                           for _, field, spec, conversion in fields)):
                raise ScenarioError("member-scenario-template-invalid")
        for field in ("folder", "privateFolder", "initialFileName", "replacementFileName"):
            name = value["content"][field].format(runId="run")
            if name in (".", "..") or any(character in name for character in ("/", "\\")) or any(ord(character) < 32 for character in name):
                raise ScenarioError("member-scenario-name-invalid")
        return value
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as failure:
        raise ScenarioError("member-scenario-unavailable") from failure


def resolve_scenario(run_id: str, *, path: Path = SCENARIO_PATH) -> dict:
    """Compile one recipe once for an execution; opaque server identities remain separate."""
    if not isinstance(run_id, str) or not RUN_ID.fullmatch(run_id):
        raise ScenarioError("member-scenario-run-id-invalid")
    value = load_scenario(path)
    # Actual runs use short safe identifiers. Longer/generic product-flow IDs
    # retain a deterministic namespace without exceeding platform filename limits.
    namespace = run_id if re.fullmatch(r"[A-Za-z0-9-]{1,80}", run_id) else hashlib.sha256(run_id.encode()).hexdigest()[:24]
    content = {key: template.format(runId=namespace) for key, template in value["content"].items()}
    return {
        "schemaVersion": RESOLVED_SCHEMA,
        "scenarioId": value["scenarioId"],
        "scenarioVersion": value["version"],
        "scenarioSha256": hashlib.sha256(_canonical(value)).hexdigest(),
        "runId": run_id,
        "requiredDomains": list(value["requiredDomains"]),
        "sharedSpaceRef": value["sharedSpace"]["spaceRef"],
        "actors": copy.deepcopy(value["actors"]),
        "content": content,
    }


def scenario_sha256() -> str:
    return hashlib.sha256(_canonical(load_scenario())).hexdigest()


def validate_resolved_scenario(value: dict, run_id: str) -> dict:
    """Reject stale or runner-invented recipes instead of accepting partial parity."""
    if value != resolve_scenario(run_id):
        raise ScenarioError("member-scenario-resolution-mismatch")
    return value


def validate_member_bindings(bindings: list[dict], organization_id: str, space_ref: str) -> list[dict]:
    """Validate a private readback from the preparation owner, never an access grant.

    Callers must obtain current identity, role, Space access and strong grant
    versions through real sessions and Weave APIs before publishing this input.
    Both actor identities remain independent from the setup administrator.
    """
    required = {"slot", "organizationId", "identityRef", "accountRef", "organizationRole",
                "spaceRef", "spacePermissions", "grantVersion"}
    try:
        if (not isinstance(organization_id, str) or not organization_id
                or space_ref != load_scenario()["sharedSpace"]["spaceRef"]
                or not isinstance(bindings, list) or len(bindings) != 2):
            raise ScenarioError("member-scenario-bindings-invalid")
        for slot, value in zip(("A", "B"), bindings):
            if (not isinstance(value, dict) or set(value) != required or value["slot"] != slot
                    or value["organizationId"] != organization_id or value["spaceRef"] != space_ref
                    or value["organizationRole"] != "member" or value["spacePermissions"] != ["VIEW", "EDIT"]
                    or any(not isinstance(value[key], str) or not value[key] or len(value[key]) > 512
                           or any(ord(char) < 32 for char in value[key])
                           for key in ("identityRef", "accountRef", "grantVersion"))
                    or not re.fullmatch(r'"[^"\r\n]+"', value["grantVersion"])):
                raise ScenarioError("member-scenario-bindings-invalid")
        if any(len({value[key] for value in bindings}) != 2 for key in ("identityRef", "accountRef")):
            raise ScenarioError("member-scenario-independent-members-required")
        return bindings
    except (KeyError, TypeError) as failure:
        raise ScenarioError("member-scenario-bindings-invalid") from failure


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args(argv)
    try:
        print(json.dumps(resolve_scenario(args.run_id), ensure_ascii=False, sort_keys=True))
        return 0
    except ScenarioError as failure:
        print(str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
