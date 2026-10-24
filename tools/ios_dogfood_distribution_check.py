#!/usr/bin/env python3
"""Validate the development-signed, in-place physical iPhone dogfood path."""

from __future__ import annotations

from pathlib import Path
from datetime import datetime, timedelta, timezone
from unittest.mock import patch
import json
import re
import tempfile
import subprocess

import dogfood_handoff_bundle as handoff


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        raise SystemExit(f"missing required iOS dogfood file: {relative_path}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def check_discovery_evidence() -> None:
    origin = "https://weave.test:9443/"
    now = datetime.now(timezone.utc)
    candidate, spec = "a" * 40, "b" * 40
    bundle = {"stackCommit": candidate, "specRevision": spec, "organizationOrigin": origin,
              "testerVisible": {"deepLink": "weave://join?organization_origin=https%3A%2F%2Fweave.test%3A9443%2F"}}
    prefs = {}
    for key, schema, field, value in (
        ("last_handoff_consumed_v1", "weave.client.last_handoff_consumed.v1", "result", "saved_configuration"),
        ("dogfood_visible_state_v1", "weave.client.dogfood_visible_state.v1", "state", "handoff_ready"),
        ("dogfood_auth_state_v1", "weave.client.dogfood_auth_state.v1", "state", "ready_for_sso"),
    ):
        prefs[key] = json.dumps({"schemaVersion": schema, "recordedAt": now.isoformat(), field: value,
                                "organizationOriginHost": "weave.test", "supportSafe": True,
                                "accessKind": "server_uri", "platformConfigHost": "weave.test",
                                "platformConfigPath": "/api/platform/config"})
    started = now - timedelta(seconds=5)
    handoff.validate_device_preferences(prefs, bundle, started, candidate, spec)
    def rejected(changed_prefs=prefs, changed_bundle=bundle):
        try:
            handoff.validate_device_preferences(changed_prefs, changed_bundle, started, candidate, spec)
        except (ValueError, KeyError, TypeError):
            return
        raise SystemExit("invalid device discovery evidence accepted")
    for key in prefs:
        rejected({**prefs, key: "[]"})
        for field, value in (("recordedAt", (started - timedelta(seconds=1)).isoformat()),
                             ("recordedAt", (now + timedelta(days=1)).isoformat()),
                             ("schemaVersion", "unknown"), ("supportSafe", "true"),
                             ("organizationOriginHost", "other.test"), ("runId", "old-run")):
            changed = json.loads(prefs[key]); changed[field] = value
            rejected({**prefs, key: json.dumps(changed)})
    changed = json.loads(prefs["last_handoff_consumed_v1"]); changed["result"] = "failed"
    rejected({**prefs, "last_handoff_consumed_v1": json.dumps(changed)})
    changed = json.loads(prefs["dogfood_auth_state_v1"]); changed["state"] = "handoff_received"
    rejected({**prefs, "dogfood_auth_state_v1": json.dumps(changed)})
    rejected(changed_bundle={**bundle, "stackCommit": "c" * 40})
    rejected(changed_bundle={**bundle, "specRevision": "c" * 40})
    for value in ("http://weave.test", "https://user:password@weave.test", "https://weave.test/api",
                  "https://weave.test?token=secret", "https://weave.test#fragment", "https://weave.test:99999"):
        try:
            handoff.canonical_origin(value)
        except ValueError:
            continue
        raise SystemExit("invalid canonical organization origin accepted")
    with tempfile.TemporaryDirectory() as output:
        subprocess.run(["python3", str(ROOT / "tools/dogfood_handoff_bundle.py"),
                        "--product-base-url", "https://offline.example:9443", "--output-dir", output],
                       check=True, capture_output=True)
        generated = json.loads((Path(output) / "handoff.json").read_text())
        require(generated["testerVisible"]["platformConfigUrl"] == "https://offline.example:9443/api/platform/config",
                "bundle did not derive bootstrap from canonical origin")
        require("caUrl" not in generated["certificates"] and generated["certificates"]["caFile"] is None,
                "offline bundle invented CA location")
        require((Path(output) / "handoff.md").is_file(), "handoff Markdown was not written")
        rejected_cli = subprocess.run(["python3", str(ROOT / "tools/dogfood_handoff_bundle.py"),
                                       "--platform-config-url", "https://alternate.example/api/platform/config"],
                                      capture_output=True)
        require(rejected_cli.returncode != 0, "retired alternate bootstrap option still accepted")
    with tempfile.TemporaryDirectory() as temp:
        tls = Path(temp) / "tls"
        class Context:
            env = {"WEAVE_PUBLIC_URL": origin}
            tls_root = tls
        with patch.object(handoff, "load_context", return_value=Context()) as loader:
            resolved = handoff.bootstrap_context()
            require(resolved["organizationOrigin"] == origin and resolved["caFile"] == str(tls / "ca.pem"),
                    "reviewed Compose origin and TLS path not preserved")
            require(resolved["localCaRequired"] is True, "reviewed TLS prerequisite lost")
            loader.assert_called_once_with("dogfood", ROOT / "infra/weave-workspace")
        with patch.object(handoff, "load_context", side_effect=AssertionError("offline override must not read deployment")):
            require(handoff.bootstrap_context("https://offline.example")["organizationOrigin"] == "https://offline.example/",
                    "offline origin-only override is unavailable")



PUBLIC_PREPARATION_FILES = (
    "weave/build/evidence/prepare-human-test/preparation-result.json",
    "weave/build/evidence/prepare-human-test/tls-persistence.json",
    "weave/build/evidence/prepare-human-test/owner-space.json",
)


def check_public_preparation_upload(workflow: str) -> None:
    steps = re.split(r"(?m)^      - ", workflow)
    uploads = [step for step in steps if "uses: actions/upload-artifact@" in step]
    require(len(uploads) == 1, "physical preparation must have exactly one bounded public upload")
    expected = "          path: |\n" + "".join(f"            {path}\n" for path in PUBLIC_PREPARATION_FILES)
    require(expected in uploads[0], "physical public upload must contain only the safe literal files")
    require(len(re.findall(r"(?m)^          path:", uploads[0])) == 1,
            "physical public upload must have exactly one path selection")
    paths = re.search(r"(?m)^          path:[^\n]*(?:\n            [^\n]*)*", uploads[0])
    require(paths is not None and paths.group(0) + "\n" == expected,
            "physical public upload contains an unexpected path")
    require(workflow.index("name: Initialize private physical diagnostics") < workflow.index("name: Start dogfood twice"),
            "private physical directory must be initialized before evidence producers")
    initialization = next(step for step in steps if step.startswith("name: Initialize private physical diagnostics"))
    for guard in ("umask 077", "path.is_symlink()", "directory.stat().st_uid != os.getuid()", "directory.chmod(0o700)"):
        require(guard in initialization, "private physical directory ownership/mode guard missing")
    install_name = "Sign the hosted artifact, install in place, and observe ordinary startup"
    for name in ("Create first owner invitation in private Mailpit", "Establish and verify current owner default Space access", install_name):
        step = next((step for step in steps if step.startswith("name: " + name)), None)
        require(step is not None, "required physical preparation producer is missing")
        require("        run: |\n          umask 077\n" in step,
                "raw physical diagnostic producer must use a private creation mask")
    setup = next(step for step in steps if step.startswith(
        "name: Establish and verify current owner default Space access"))
    require("infra/weave-workspace/compose.sh dogfood bootstrap-space" in setup
            and "        if:" not in setup,
            "owner Space access must be an unconditional preparation prerequisite")
    require(workflow.index("name: Establish and verify current owner default Space access")
            < workflow.index("name: " + install_name),
            "current owner Space access must be established before installation")
    require('receipt["ownerWorkspace"] = workspace' in workflow
            and "owner_workspace_passed(workspace, sha, tree, spec)" in workflow,
            "physical evidence must validate and bind owner Space access to the exact candidate")
    require('preparation_passed(receipt, sha, tree, spec,' in workflow
            and 'int(os.environ["FULL_COMPOSE_E2E_RUN_ATTEMPT"])' in workflow,
            "physical startup evidence must bind the exact successful qualification attempt")
    install = next(step for step in steps if step.startswith("name: " + install_name))
    require("tools/prepare_iphone_artifact.py" in install
            and '--qualification-run-attempt "$FULL_COMPOSE_E2E_RUN_ATTEMPT"' in install
            and "--diagnostic-only" not in install and "continue-on-error" not in install,
            "physical preparation requires observed ordinary startup from the qualified hosted artifact")
    consume = next((step for step in steps if step.startswith(
        "name: Consume the qualified runtime and iPhone artifacts")), "")
    require("tools/artifact_consumers.py fetch-composition" in consume and "--qualified" in consume
            and "tools/build_artifacts.py consume" in consume
            and "--components runtime-linux-amd64,ios-device" in consume,
            "physical installation must consume the qualified runtime and device composition")
    require(workflow.index("name: Consume the qualified runtime and iPhone artifacts")
            < workflow.index("name: " + install_name)
            < workflow.index("name: Bind the prepared owner Space to the installed artifact")
            < workflow.index("name: Summarize tester handoff"),
            "verified artifact consumption and ordinary startup must precede handoff")
    for forbidden in ("--stage physical", "flutter build", "build-for-testing", "dogfood_iphone_entry.sh"):
        require(forbidden not in workflow, "protected physical preparation reintroduces a local build or owner-authentication suite")


def check_hosted_preparation_producer(producer: str) -> None:
    for marker in ("'schemaVersion': 'weave.hil-preparation/v4'", "'ownerHIL': 'pending'",
                   "'install': 'update_in_place'", "'artifactComposition': composition",
                   "candidate_composition(candidate)", "native_bundle('ios-device'",
                   "verify_runtime_container(", "sign(ROOT, unsigned, signed, candidate,",
                   "verify_signed(ROOT, unsigned, signed, candidate)",
                   "verify_installed_build(runner, actor)", "verify_discovered_routing(runner, actor, context)",
                   "'testOrdinaryPreparation'", "parse_startup_result",
                   "{'retained-workspace', 'normal-sign-in'}", "value['ownerSessionReplaced'] is not False",
                   "'driverCleanupComplete': True"):
        require(marker in producer, "physical preparation producer is missing artifact, startup or preservation proof")
    for forbidden in ("flutter build", "build-for-testing", "WEAVE_DOGFOOD_MEMBER_PASSWORD", "'device', 'uninstall', 'app', '--device', device, BUNDLE"):
        require(forbidden not in producer, "physical preparation producer must not compile or replace owner state")


def check_public_preparation_upload_regressions(workflow: str) -> None:
    check_public_preparation_upload(workflow)
    literal = "          path: |\n" + "".join(f"            {path}\n" for path in PUBLIC_PREPARATION_FILES)
    variants = [
        workflow.replace('preparation_passed(receipt, sha, tree, spec,', 'bool(receipt), #'),
        workflow.replace('tools/prepare_iphone_artifact.py', 'echo skipped installer'),
        workflow.replace('--qualification-run-attempt "$FULL_COMPOSE_E2E_RUN_ATTEMPT"', '--qualification-run-attempt 1'),
        workflow.replace('--components runtime-linux-amd64,ios-device', '--components runtime-linux-amd64'),
        workflow.replace('--qualified --candidate', '--candidate'),
        workflow + '\n# tools/dogfood_functional_smoke.py --stage physical\n',
        workflow.replace("Establish and verify current owner default Space access", "Optional workspace setup"),
        workflow.replace("infra/weave-workspace/compose.sh dogfood bootstrap-space", "echo skipped setup"),
        workflow.replace('receipt["ownerWorkspace"] = workspace', 'receipt["ownerWorkspace"] = {}'),
        workflow.replace(literal, "          path: weave/build/evidence/prepare-human-test\n"),
        workflow.replace(PUBLIC_PREPARATION_FILES[1], "weave/build/evidence/prepare-human-test/*"),
        workflow.replace(PUBLIC_PREPARATION_FILES[1], "weave/build/evidence/prepare-human-test/ios-app-preferences.txt"),
        workflow.replace(literal, literal + "            weave/build/evidence/prepare-human-test/ios-device-details.json\n"),
        workflow.replace(literal, literal + "            weave/build/evidence/prepare-human-test/private-member.json\n"),
        workflow.replace(literal, literal + "            weave/build/evidence/prepare-human-test/functional.xcresult\n"),
        workflow.replace(literal, literal + "            weave/build/evidence/prepare-human-test/browser.log\n"),
        workflow.replace(literal, literal + "          path: weave/build/evidence/prepare-human-test\n"),
        workflow + "\n      - uses: actions/upload-artifact@unsafe\n        with:\n          path: weave/build/evidence/prepare-human-test\n",
    ]
    for variant in variants:
        try:
            check_public_preparation_upload(variant)
        except SystemExit:
            pass
        else:
            raise SystemExit("unsafe physical evidence upload was accepted")
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        directory = root / "weave/build/evidence/prepare-human-test"
        directory.mkdir(parents=True)
        for path in PUBLIC_PREPARATION_FILES:
            (root / path).write_text("{}", encoding="utf-8")
        sentinel = directory / "ios-app-preferences.txt"
        sentinel.write_text("private-sentinel", encoding="utf-8")
        selected = [root / path for path in PUBLIC_PREPARATION_FILES]
        require(all(path.is_file() for path in selected) and sentinel not in selected,
                "public physical selection included a private diagnostic sentinel")


def main() -> int:
    check_discovery_evidence()
    workflow = read(".github/workflows/ios-dogfood.yml")
    check_public_preparation_upload_regressions(workflow)
    producer = read("tools/prepare_iphone_artifact.py")
    check_hosted_preparation_producer(producer)
    for before, after in (("weave.hil-preparation/v4", "weave.hil-preparation/v3"),
                          ("'ownerHIL': 'pending'", "'ownerHIL': 'passed'"),
                          ("verify_installed_build(runner, actor)", "None"),
                          ("value['ownerSessionReplaced'] is not False", "False"),
                          ("'driverCleanupComplete': True", "'driverCleanupComplete': False")):
        try:
            check_hosted_preparation_producer(producer.replace(before, after))
        except SystemExit:
            pass
        else:
            raise SystemExit("incomplete or fabricated physical startup evidence was accepted")
    smoke = read("tools/dogfood_ios_deeplink_smoke.sh")
    entry = read("tools/dogfood_iphone_entry.sh")
    project = read("client/ios/Runner.xcodeproj/project.pbxproj")
    development_entitlements = read("client/ios/Runner/RunnerDevelopment.entitlements")
    release_entitlements = read("client/ios/Runner/Runner.entitlements")
    docs = read("docs/ios-dogfood-distribution.md")

    require("name: Prepare Human Test" in workflow, "physical iPhone workflow must be the one-click human-test preparation")
    require("workflow_dispatch:" in workflow and "candidate_sha:" in workflow, "human-test preparation must select one exact dogfood SHA")
    require("verify_main_promotion_evidence.py" in workflow and "--qualification-only" in workflow,
            "physical install is not gated by exact Compose native and deployment evidence")
    require("preparation-result.json" in workflow and "qualificationRunId" in producer
            and '--qualification-run-id "$FULL_COMPOSE_E2E_RUN_ID"' in workflow,
            "physical preparation must bind installed source to qualification")
    require("./gradlew --no-daemon dogfoodUp" in workflow, "physical preparation does not start Compose dogfood")
    require("tools/dogfood_cert_persistence_smoke.py" in workflow, "TLS identity stability is not checked")
    require("tools/prepare_iphone_artifact.py" in workflow, "hosted physical-device installation entrypoint is missing")
    require("WEAVE_IOS_RESET_MODE: update_in_place" in workflow, "workflow does not preserve Developer App trust")
    require("secrets.WEAVE_IOS_DEVICE_ID" in workflow, "paired device identifier is not provided privately")
    for forbidden in ("TestFlight", "upload_to_testflight", "APP_STORE_CONNECT", "APPLE_DISTRIBUTION", "environment:"):
        require(forbidden not in workflow, f"retired distribution gate remains: {forbidden}")

    require(project.count("PRODUCT_BUNDLE_IDENTIFIER = com.massimotter.weave;") == 3, "bundle identity is not stable across build modes")
    require(project.count("DEVELOPMENT_TEAM = KNDHGC2KV6;") == 3, "Apple team identity is not stable across build modes")
    require(
        project.count("CODE_SIGN_ENTITLEMENTS = Runner/RunnerDevelopment.entitlements;") == 1,
        "the Profile dogfood build must use the Personal Team-compatible entitlements",
    )
    require(
        project.count("CODE_SIGN_ENTITLEMENTS = Runner/Runner.entitlements;") == 2,
        "Debug and Release must retain the full app entitlements",
    )
    require(
        "$(AppIdentifierPrefix)com.massimotter.weave" in development_entitlements,
        "development Keychain identity is not stable",
    )
    require(
        "com.apple.developer.associated-domains" not in development_entitlements,
        "the Personal Team-compatible Profile must not request Associated Domains",
    )
    require(
        "com.apple.developer.associated-domains" in release_entitlements,
        "the Release build must retain Associated Domains",
    )

    for marker in (
        'BUNDLE_ID="com.massimotter.weave"',
        'EXPECTED_TEAM_ID="${WEAVE_IOS_EXPECTED_TEAM_ID:-KNDHGC2KV6}"',
        'EXPECTED_DEVELOPER_CERT_TEAM_ID="${WEAVE_IOS_EXPECTED_DEVELOPER_CERT_TEAM_ID:-6RUS2Z848X}"',
        "WEAVE_CANDIDATE_COMMIT",
        "WEAVE_IOS_BUILD_NUMBER",
        "WEAVE_BUILD_CHANNEL=development-dogfood",
        'if [[ "${RESET_MODE}" == "destructive_uninstall" ]]',
        "device install app",
    ):
        require(marker in smoke, f"development-signed installer is missing {marker!r}")
    require('RESET_MODE="${WEAVE_IOS_RESET_MODE:-update_in_place}"' in smoke, "installer default is not in-place update")
    require('RESET_MODE="${WEAVE_IOS_RESET_MODE:-update_in_place}"' in entry, "entrypoint default is not in-place update")

    for phrase in (
        "Full Compose E2E",
        "update in place",
        "WEAVE_IOS_DEVICE_ID",
        "com.massimotter.weave",
        "KNDHGC2KV6",
        "6RUS2Z848X",
        "WEAVE_MAILPIT_URL",
    ):
        require(phrase in docs, f"iOS dogfood documentation is missing {phrase!r}")

    print("ios-dogfood-distribution-check: ok channel=development-in-place")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
