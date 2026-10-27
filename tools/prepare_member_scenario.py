#!/usr/bin/env python3
"""Prepare the two managed scenario members; normal login and collaboration remain separate proof."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time

from dogfood_prepare import (ROOT, ContractError, effective_reviewed_env_file, fixture_binding, identity_digest,
    ledger_for, load_context, operator_backend, operator_driver, operator_grant_evidence,
    operator_ledger, private_directory, private_read, private_write, public_setup,
    validate_operator_envelope)


def candidate_identity(runner) -> dict:
    candidate = {"candidateCommit": runner.run(["git", "rev-parse", "HEAD"]).strip(),
                 "sourceTree": runner.run(["git", "rev-parse", "HEAD^{tree}"]).strip(),
                 "specificationCommit": json.loads((ROOT / "specs/weave-specs.lock.json").read_text())["specCorpus"]["gitCommit"]}
    if runner.run(["git", "status", "--porcelain"]):
        raise ContractError("clean-exact-candidate-required")
    return candidate


def ca_fingerprint(context, runner) -> str:
    if context.environment == "dogfood":
        return public_setup(context)[1]["caSha256Fingerprint"]
    certificate = context.tls_root / "ca.pem"
    from dogfood_public_setup import _no_symlink
    _no_symlink(certificate)
    value = runner.run(["openssl", "x509", "-in", str(certificate), "-noout", "-fingerprint", "-sha256"], timeout=10).strip()
    fingerprint = value.partition("=")[2].upper()
    if not re.fullmatch(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}", fingerprint):
        raise ContractError("existing-isolated-ca-unavailable")
    return fingerprint


def collaboration_input(envelope: dict, plan: dict, passwords: dict, run_id: str) -> dict:
    from member_test_scenario import resolve_scenario, validate_member_bindings
    envelope = validate_operator_envelope(envelope)
    receipt = envelope["receipt"]
    if receipt["status"] != "passed" or receipt["stage"] != "provision-members-ready":
        raise ContractError("managed-test-member-preparation-required")
    subjects = {member["slot"]: member["identityRef"] for member in envelope["privateData"]["members"]}
    observed = {member["slot"]: member for member in receipt["members"]}
    bindings, actors = [], []
    for member in plan["members"]:
        slot = member["slot"]
        subject = subjects[slot]
        account = "acct_" + hashlib.sha256(("issuer+subject:" + plan["issuerUrl"] + "#" + subject).encode()).hexdigest()[:32]
        if observed[slot]["accountRef"] != account:
            raise ContractError("managed-test-identity-binding-conflict")
        actors.append({"slot": slot, "email": member["email"], "password": passwords[slot],
                       "identityRef": subject, "accountRef": account})
        bindings.append({"slot": slot, "organizationId": receipt["organizationId"], "identityRef": subject,
                         "accountRef": account, "organizationRole": "member", "spaceRef": receipt["spaceRef"],
                         "spacePermissions": ["VIEW", "EDIT"], "grantVersion": observed[slot]["grantVersion"]})
    validate_member_bindings(bindings, receipt["organizationId"], receipt["spaceRef"])
    return {"schemaVersion": "weave.member-collaboration-input/v1", "runId": run_id,
            "organizationId": receipt["organizationId"], "publicOrigin": plan["organizationOrigin"],
            "issuer": plan["issuerUrl"], "memberScenario": resolve_scenario(run_id),
            "actors": actors, "memberBindings": bindings}


def publish_managed_fixture(path: Path, ledger_path: Path, ledger: dict, envelope: dict, candidate: dict) -> None:
    """Publish observed setup bindings without constructing an application-authentication proof."""
    envelope = validate_operator_envelope(envelope)
    receipt = envelope["receipt"]
    plan = ledger["plan"]
    if (receipt["status"] != "passed" or receipt["stage"] != "provision-members-ready"
            or receipt["planId"] != plan["planId"] or any(receipt[key] != candidate[key] for key in candidate)):
        raise ContractError("managed-test-member-preparation-required")
    fixture = {"schemaVersion": "weave.dogfood-functional-identities/v1", "organizationOrigin": plan["organizationOrigin"],
               "ownerDisplayName": envelope["privateData"]["ownerDisplayName"], "members": [
                   {"email": member["email"], "displayName": member["displayName"], "password": ledger["passwords"][member["slot"]]}
                   for member in plan["members"]]}
    if (path.exists() or path.is_symlink()) and private_read(path) != fixture:
        raise ContractError("approved-test-member-fixture-conflict")
    owner = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], receipt["intendedOwnerAccountRef"]),
             "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], receipt["organizationId"])}
    ledger.update(operatorEnvelope=envelope, lastGrantsEnvelope=envelope, ownerBinding=owner, candidate=candidate,
                  fixtureSha256=hashlib.sha256(json.dumps(fixture, sort_keys=True, separators=(",", ":")).encode()).hexdigest())
    operator_grant_evidence(ledger)
    private_write(ledger_path, ledger)
    private_write(path, fixture)


def verify_member_workspace(context, candidate: dict, runner, directory: Path, tester_slot: str,
                            identity_file: Path | None = None) -> dict:
    """Read-only handoff prerequisite, called under the existing exclusive Dogfood session lock."""
    from compose_runtime import _service_container
    if context.environment != "dogfood" or context.isolated_namespace is not None or tester_slot not in {"A", "B"}:
        raise ContractError("persistent-ordinary-member-required")
    identity_file = identity_file or Path.home() / ".weave/dogfood/functional-smoke-identities.json"
    fixture = private_read(identity_file)
    binding = fixture_binding(context, identity_file, fixture)["members"][tester_slot]
    ledger = private_read(ledger_for(identity_file), 65536)
    prior = operator_grant_evidence(ledger)
    if (ledger.get("authorizationKind") != "dogfood-operator" or prior is None
            or prior["receipt"]["stage"] != "provision-members-ready"):
        raise ContractError("managed-test-member-preparation-required")
    before = _service_container(context, "backend", deadline=time.monotonic() + 30)
    operator_backend(context, candidate, before, runner)
    plan = {**ledger["plan"], "nonce": secrets.token_hex(16)}
    envelope = operator_driver(context, before, plan, candidate, directory, "verify-members", previous=prior["receipt"])
    receipt = validate_operator_envelope(envelope, previous=prior["receipt"])["receipt"]
    if receipt["status"] != "passed":
        raise ContractError(receipt["boundary"])
    if (receipt["operation"] != "verify-members" or receipt["nonce"] != plan["nonce"]
            or any(receipt[key] != candidate[key] for key in candidate)):
        raise ContractError("member-workspace-candidate-mismatch")
    member = next(item for item in receipt["members"] if item["slot"] == tester_slot)
    observed = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], member["accountRef"]),
                "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], receipt["organizationId"])}
    if observed != binding:
        raise ContractError("member-workspace-identity-conflict")
    after = _service_container(context, "backend", deadline=time.monotonic() + 30)
    operator_backend(context, candidate, after, runner)
    if before["Id"] != after["Id"] or before["Image"] != after["Image"]:
        raise ContractError("deployed-image-changed-during-preparation")
    return {"schemaVersion": "weave.member-workspace-preparation/v1", **candidate,
            "environment": "dogfood", "status": "passed", "testerSlot": tester_slot,
            "organizationRole": "member", **observed, "spaceRef": "workspace-default",
            "grantVersion": member["grantVersion"], "memberAccessVerified": True,
            "authenticationEvidence": False, "humanAcceptance": False,
            "supportSafe": True, "containsSecretValues": False}


def prepare_context(context, identity_file: Path, *, collaboration_output: Path | None = None,
                    run_id: str | None = None, output: Path | None = None) -> dict:
    from compose_runtime import (_dogfood_session_lock, _service_container, require_existing_owned_resources,
                                 verify_dogfood_identity)
    from dogfood_functional_smoke import Runner, SmokeError
    from artifact_consumers import ArtifactError, candidate_composition, verify_runtime_container
    from member_test_scenario import resolve_scenario
    persistent = context.environment == "dogfood" and context.isolated_namespace is None
    isolated = context.environment == "e2e" and context.isolated_namespace is not None
    if not (persistent or isolated):
        raise ContractError("managed-test-preparation-posture-required")
    result = {"schemaVersion": "weave.member-scenario-preparation/v1", "environment": context.environment,
              "status": "incomplete", "stage": "test-members", "boundary": "operation-incomplete",
              "preparationComplete": False, "supportSafe": True, "containsSecretValues": False,
              "authenticationEvidence": False, "humanAcceptance": False}
    try:
        private_directory(identity_file.parent)
        with _dogfood_session_lock(context), tempfile.TemporaryDirectory(prefix="weave-member-preparation-") as temporary:
            directory = Path(temporary).resolve(strict=True)
            directory.chmod(0o700)
            runner = Runner(directory)
            candidate = candidate_identity(runner)
            result.update(candidate)
            if isolated:
                require_existing_owned_resources(context)
                if run_id != context.env["WEAVE_E2E_RUN_ID"]:
                    raise ContractError("isolated-preparation-run-mismatch")
            else:
                verify_dogfood_identity(context)
            fingerprint = ca_fingerprint(context, runner)
            ledger_path = ledger_for(identity_file)
            ledger = operator_ledger(context, ledger_path, fingerprint, identity_file)
            run_id = run_id or "dogfood-" + candidate["candidateCommit"][:12] + "-" + secrets.token_hex(4)
            scenario = resolve_scenario(run_id)
            result["scenarioDigest"] = "sha256:" + scenario["scenarioSha256"]
            before = _service_container(context, "backend", deadline=time.monotonic() + 30)
            operator_backend(context, candidate, before, runner, allow_isolated=isolated)
            component = verify_runtime_container(before, candidate["candidateCommit"])
            result["runtimeArtifact"] = component
            result["compositionDigest"] = candidate_composition(candidate["candidateCommit"])["compositionDigest"]
            prior = operator_grant_evidence(ledger) or ledger.get("operatorEnvelope")
            current_plan = {**ledger["plan"], "nonce": secrets.token_hex(16)}
            envelope = operator_driver(context, before, current_plan, candidate, directory, "provision-members",
                                       previous=prior["receipt"] if prior else None, initial_credentials=ledger["passwords"])
            # Persist exact partial references before surfacing an interrupted or rejected operation.
            ledger["operatorEnvelope"] = envelope
            private_write(ledger_path, ledger)
            if envelope["receipt"]["status"] != "passed":
                raise ContractError(envelope["receipt"]["boundary"])
            after = _service_container(context, "backend", deadline=time.monotonic() + 30)
            operator_backend(context, candidate, after, runner, allow_isolated=isolated)
            if before["Id"] != after["Id"] or before["Image"] != after["Image"]:
                raise ContractError("deployed-image-changed-during-preparation")
            collaboration = collaboration_input(envelope, ledger["plan"], ledger["passwords"], run_id)
            publish_managed_fixture(identity_file, ledger_path, ledger, envelope, candidate)
            if collaboration_output is not None:
                private_write(collaboration_output, collaboration)
            result.update(status="passed", preparationComplete=True, boundary="ordinary-functional-smoke-required",
                          receipt=envelope["receipt"],
                          humanAction="Continue ordinary independent member login and collaboration before iPhone handoff.")
    except (ContractError, SmokeError, ArtifactError) as failure:
        boundary = str(failure)
        result["boundary"] = boundary if re.fullmatch(r"[a-z][a-z0-9-]{1,100}", boundary) else "managed-test-preparation-unavailable"
    if output is not None:
        if len(json.dumps(result).encode()) > 32768:
            raise ContractError("managed-test-preparation-receipt-too-large")
        private_write(output, result)
    return result


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=("dogfood", "e2e"), default="dogfood")
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--identity-file", type=Path, default=Path.home() / ".weave/dogfood/functional-smoke-identities.json")
    parser.add_argument("--collaboration-output", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    env_file = effective_reviewed_env_file(args.env_file) if args.environment == "dogfood" else args.env_file or os.environ.get("WEAVE_ENV_FILE")
    context = load_context(args.environment, ROOT / "infra/weave-workspace", str(env_file) if env_file else None)
    result = prepare_context(context, args.identity_file, collaboration_output=args.collaboration_output,
                             run_id=args.run_id, output=args.output)
    print("MEMBER_SCENARIO_PREPARATION status=" + result["status"] + " boundary=" + result["boundary"] + " supportSafe=true")
    return 0 if result["preparationComplete"] else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContractError, OSError, ValueError, subprocess.SubprocessError):
        print("MEMBER_SCENARIO_PREPARATION status=incomplete boundary=managed-test-preparation-unavailable supportSafe=true")
        raise SystemExit(2)
