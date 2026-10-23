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

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "infra/weave-workspace/scripts"))
from compose_env import ContractError, load_context
from compose_runtime import _dogfood_session_lock, _private_json
from dogfood_lifecycle import effective_reviewed_env_file
from dogfood_public_setup import _no_symlink, https_origin, public_setup, write_public_setup

HEX = re.compile(r"^[0-9a-f]{32}$")
HASH = re.compile(r"^[0-9a-f]{64}$")


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
        value = json.loads(path.read_text())
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
    if set(ledger) != {"schemaVersion", "plan", "passwords", "caSha256Fingerprint", "registrationState", "invitationReceipt", "grantReceipt", "ownerBinding", "candidate", "fixtureSha256"}:
        raise ContractError("approved-test-member-preparation-binding-required")
    plan = validate_plan(ledger["plan"], context)
    invitations = validate_receipt(ledger["invitationReceipt"], plan, "invitations-ready", ledger["invitationReceipt"]["nonce"])
    grants = ledger["grantReceipt"]
    validate_receipt(grants, plan, "grants-ready", grants["nonce"], invitations)
    expected_hash = hashlib.sha256(json.dumps(fixture, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    owner = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], grants["intendedOwnerAccountRef"]),
             "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], grants["organizationId"])}
    if (ledger.get("schemaVersion") != "weave.dogfood-admin-preparation-ledger/v1"
            or ledger.get("caSha256Fingerprint") != public_setup(context)[1]["caSha256Fingerprint"]
            or ledger.get("fixtureSha256") != expected_hash or ledger.get("ownerBinding") != owner
            or fixture.get("ownerDisplayName") != grants["ownerDisplayName"]):
        raise ContractError("approved-test-member-preparation-binding-required")
    return {"owner": owner, "members": {member["slot"]: {
        "accountDigest": identity_digest("weave-account", plan["issuerUrl"], member["accountRef"]),
        "organizationDigest": owner["organizationDigest"]} for member in grants["members"]}}


def action_links(message: dict, issuer: str, verification: bool) -> set[str]:
    """Select only stock, exact-issuer actions; URLs remain private in memory."""
    path = urlsplit(issuer).path + ("/login-actions/action-token" if verification else "/protocol/openid-connect/registrations")
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
            parsed = urlsplit(candidate)
            query = parse_qs(parsed.query, keep_blank_values=True)
            if (parsed.scheme == "https" and parsed.netloc == urlsplit(issuer).netloc and parsed.path == path
                    and not parsed.username and not parsed.password and not parsed.fragment
                    and query.get("client_id") == ["account"]
                    and (verification or query.get("response_type") == ["code"])
                    and len(query.get("key" if verification else "token", [])) == 1
                    and query["key" if verification else "token"][0]):
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
    private = prepare_browser_fixture(actor["products"], fixture)
    try:
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
        raise ContractError("normal-test-member-registration-required")
    except SmokeError:
        raise ContractError("normal-test-member-registration-required") from None
    finally:
        private.unlink(missing_ok=True)


def build_actors(runner, context, candidate: str, devices: list[str]) -> dict:
    from dogfood_functional_smoke import CLIENT, BUNDLE, select_runtime, build_info
    runtime, device_type = select_runtime()
    runner.run(["flutter", "build", "ios", "--simulator", "--debug", "--target=lib/main.dart",
                "--dart-define=WEAVE_CANDIDATE_COMMIT=" + candidate,
                "--dart-define=WEAVE_BUILD_CHANNEL=development-dogfood"], cwd=CLIENT, timeout=1200)
    app = CLIENT / "build/ios/iphonesimulator/Runner.app"
    build_info(app, "debug")
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
        runner.run(["xcodebuild", "build-for-testing", "-project", str(CLIENT / "tool/ios_browser_driver/BrowserDriver.xcodeproj"),
                    "-scheme", "BrowserDriver", "-destination", "platform=iOS Simulator,id=" + udid,
                    "-derivedDataPath", str(derived), "CODE_SIGNING_ALLOWED=NO"], timeout=600)
        actors[slot] = {"id": udid, "physical": False, "destination": "platform=iOS Simulator,id=" + udid,
                        "products": derived / "Build/Products", "app": app}
    return actors


def member_proof(runner, actor, context, plan: dict, member: dict, password: str, invitation: dict,
                 registration_state: str | None = None, record_registration=lambda _state: None) -> dict:
    from dogfood_functional_smoke import BUNDLE, SmokeError, restart, verify_discovered_routing, verify_installed_build
    def authenticate():
        restart(runner, actor)
        runner.ui(actor, {"organizationOrigin": plan["organizationOrigin"]}, "testOrdinaryOrganizationEntry")
        runner.ui(actor, {"email": member["email"], "password": password, "displayName": member["displayName"],
                         "issuerHost": urlsplit(plan["issuerUrl"]).hostname, "appBundleIdentifier": BUNDLE,
                         "phase": "initial", "nativeAuthCompletionPath": "", "ordinaryOrganizationOrigin": plan["organizationOrigin"]},
                  "testProductionBrowser")
    authenticated = False
    interrupted = registration_state in {"activation-started", "email-verification-required"}
    if interrupted:
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
            if registration(runner, actor, fixture) != "complete":
                raise ContractError("test-member-email-verification-required")
        record_registration("registration-complete")
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
    validate_receipt(grants, plan, "grants-ready", proof["nonce"], ledger["invitationReceipt"])
    verify_proof(proof, grants, plan)
    fixture = {"schemaVersion": "weave.dogfood-functional-identities/v1", "organizationOrigin": plan["organizationOrigin"],
               "ownerDisplayName": grants["ownerDisplayName"], "members": [
                   {"email": member["email"], "displayName": member["displayName"], "password": ledger["passwords"][member["slot"]]}
                   for member in sorted(plan["members"], key=lambda member: member["slot"])]}
    if (path.exists() or path.is_symlink()) and private_read(path) != fixture:
        raise ContractError("approved-test-member-fixture-conflict")
    expected = {"accountDigest": identity_digest("weave-account", plan["issuerUrl"], grants["intendedOwnerAccountRef"]),
                "organizationDigest": identity_digest("weave-organization", plan["issuerUrl"], grants["organizationId"])}
    ledger.update(grantReceipt=grants, ownerBinding=expected, candidate=candidate,
                  fixtureSha256=hashlib.sha256(json.dumps(fixture, sort_keys=True, separators=(",", ":")).encode()).hexdigest())
    # Ledger first: a crash can leave an incomplete pair, never an accepted fixture
    # without its exact current authority/identity sidecar.
    private_write(ledger_path, ledger)
    private_write(path, fixture)


def prepare_members(context, result: dict, fixture_path: Path, ledger_path: Path) -> dict:
    from compose_runtime import _service_container, verify_dogfood_identity
    from dogfood_functional_smoke import Runner, SmokeError
    if result.get("localTrustStatus") != "passed": return result
    result.update(stage="administrator-preparation", boundary="normal-administrator-authentication-required")
    devices = []
    previous_term = signal.getsignal(signal.SIGTERM)
    def terminate(_number, _frame):
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        raise ContractError("administrator-preparation-terminated")
    signal.signal(signal.SIGTERM, terminate)
    try:
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
                deployed = before.get("Config", {}).get("Labels", {}).get("org.opencontainers.image.revision", "")
                if not re.fullmatch(r"[0-9a-f]{40}", deployed) or runner.run(["git", "rev-parse", deployed + "^{tree}"]).strip() != candidate["sourceTree"]:
                    raise ContractError("deployed-source-tree-mismatch")
                ledger = preparation_ledger(context, ledger_path, result["trust"]["caSha256Fingerprint"])
                actors = build_actors(runner, context, candidate["candidateCommit"], devices)
                plan = ledger["plan"]
                current_plan = {**plan, "nonce": secrets.token_hex(16)}
                observed = admin_driver(current_plan, directory, "invitations")
                if observed.get("stage") == "grants-ready":
                    # The UI may resume its still-approved session only after
                    # fresh current-state readbacks with this invocation nonce.
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
    args = parser.parse_args()
    context = load_context("dogfood", ROOT / "infra/weave-workspace", str(effective_reviewed_env_file(args.env_file)))
    result = preflight(context, Path.home() / ".weave/dogfood/public-setup")
    result = prepare_members(context, result, args.identity_file, ledger_for(args.identity_file))
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
