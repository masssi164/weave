#!/usr/bin/env python3
"""Validate the development-signed, in-place physical iPhone dogfood path."""

from __future__ import annotations

from pathlib import Path
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
        require(generated["qualificationEvidence"] is False and generated["iosClient"]["installMode"] == "update_in_place",
                "informational handoff bundle must not claim qualification or request app reset")
        require(not {"handoffConsumedEvidenceKey", "trustPreservingFreshReset"}.intersection(generated["iosClient"]),
                "handoff bundle exposes retired private-app evidence or reset instructions")
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



RETIRED_DOGFOOD_TOOLS = (
    "dogfood_onboarding_evidence_check.py", "dogfood_onboarding_evidence_check_test.py",
    "dogfood_member_onboarding_gate.sh", "dogfood_ios_deeplink_smoke.sh",
    "dogfood_ios_simulator_onboarding_smoke.sh", "dogfood_ios_session_continuity_check.py",
    "dogfood_ios_session_continuity_check_test.py", "dogfood_ios_session_restore_smoke.sh",
    "dogfood_post_login_chat_files_check.py", "dogfood_iphone_entry.sh", "dogfood_trust_stability_check.py",
)
RETIRED_APP_EVIDENCE = (
    "last_handoff_consumed_v1", "dogfood_visible_state_v1", "dogfood_auth_state_v1",
    "SIMULATOR_E2E_GREEN", "DOGFOOD_MEMBER_ONBOARDING_RESULT", "DOGFOOD_SESSION_CONTINUITY_RESULT",
    "DOGFOOD_TRUST_STABILITY_RESULT", "DOGFOOD_TRUST_STABILITY_BLOCKED",
)


def check_external_qualification() -> None:
    """Keep active qualification on external normal-UI/API owners after retirement."""
    for filename in RETIRED_DOGFOOD_TOOLS:
        require(not (ROOT / "tools" / filename).exists(), "retired private-app Dogfood tool was restored: " + filename)
    for path in (
        "client/tool/run_ios_simulator_product_acceptance.py",
        "client/tool/ios_browser_driver/OrdinaryProductSmoke.swift",
        "tools/dogfood_functional_smoke.py", "tools/prepare_iphone_artifact.py",
    ):
        read(path)
    sources = [ROOT / "e2e/scenario_mappings.json"]
    for directory in ("tools", "client/tool", ".github/workflows", "gradle/tasks"):
        sources.extend((ROOT / directory).rglob("*"))
    for path in sources:
        if (not path.is_file() or path.suffix not in {".py", ".sh", ".swift", ".yml", ".gradle", ".json"}
                or path.name == Path(__file__).name or path.name.startswith("test_") or path.stem.endswith("_test")):
            continue
        source = path.read_text(encoding="utf-8")
        for retired in (*RETIRED_DOGFOOD_TOOLS, *RETIRED_APP_EVIDENCE):
            require(retired not in source, "active qualification references retired private-app evidence: " + str(path.relative_to(ROOT)))
    for directory, suffix in (("client/lib", ".dart"), ("client/ios/Runner", ".swift")):
        for path in (ROOT / directory).rglob("*" + suffix):
            source = path.read_text(encoding="utf-8")
            require(not any(marker in source for marker in RETIRED_APP_EVIDENCE),
                    "production client must not publish Dogfood-only evidence")
    for path in ("tools/dogfood_functional_smoke.py", "tools/prepare_iphone_artifact.py"):
        source = read(path)
        require("Library/Preferences" not in source and "server_configuration_v1" not in source,
                "external qualification must observe normal UI/API instead of saved app preferences")


PUBLIC_PREPARATION_FILES = (
    "weave/build/evidence/prepare-human-test/preparation-result.json",
    "weave/build/evidence/prepare-human-test/tls-persistence.json",
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
    for name in (install_name,):
        step = next((step for step in steps if step.startswith("name: " + name)), None)
        require(step is not None, "required physical preparation producer is missing")
        require("        run: |\n          umask 077\n" in step,
                "raw physical diagnostic producer must use a private creation mask")
    require("--tester-slot \"$WEAVE_HUMAN_TEST_MEMBER_SLOT\"" in workflow
            and "WEAVE_HUMAN_TEST_MEMBER_SLOT: ${{ inputs.tester_slot }}" in workflow,
            "physical preparation must select a stable ordinary member")
    for forbidden in ("bootstrap-owner", "bootstrap-space", "ownerWorkspace", "WEAVE_DOGFOOD_OWNER_"):
        require(forbidden not in workflow, "physical handoff must not provision owner authority")
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
            < workflow.index("name: Verify the prepared member and installed artifact")
            < workflow.index("name: Summarize tester handoff"),
            "verified artifact consumption and ordinary startup must precede handoff")
    for forbidden in ("--stage physical", "flutter build", "build-for-testing", "dogfood_iphone_entry.sh"):
        require(forbidden not in workflow, "protected physical preparation reintroduces a local build or owner-authentication suite")


def check_hosted_preparation_producer(producer: str) -> None:
    for marker in ("'schemaVersion': 'weave.hil-preparation/v5'", "'humanAcceptance': 'pending'",
                   "'install': 'update_in_place'", "'artifactComposition': composition",
                   "candidate_composition(candidate)", "native_bundle('ios-device'",
                   "verify_runtime_container(", "sign(ROOT, unsigned, signed, candidate,",
                   "verify_signed(ROOT, unsigned, signed, candidate)",
                   "verify_installed_build(runner, actor)", "verify_discovered_routing(runner, actor, context,", "verify_member_workspace(context,",
                   "receipt['memberWorkspace'][key]", "routing_state != startup['visibleState']",
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
        workflow.replace('--tester-slot "$WEAVE_HUMAN_TEST_MEMBER_SLOT"', '--tester-slot A'),
        workflow + '\n# compose.sh dogfood bootstrap-owner\n',
        workflow + '\n# compose.sh dogfood bootstrap-space\n',
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
    check_external_qualification()
    workflow = read(".github/workflows/ios-dogfood.yml")
    check_public_preparation_upload_regressions(workflow)
    producer = read("tools/prepare_iphone_artifact.py")
    check_hosted_preparation_producer(producer)
    for before, after in (("weave.hil-preparation/v5", "weave.hil-preparation/v3"),
                          ("'humanAcceptance': 'pending'", "'humanAcceptance': 'passed'"),
                          ("verify_installed_build(runner, actor)", "None"),
                          ("value['ownerSessionReplaced'] is not False", "False"),
                          ("'driverCleanupComplete': True", "'driverCleanupComplete': False")):
        try:
            check_hosted_preparation_producer(producer.replace(before, after))
        except SystemExit:
            pass
        else:
            raise SystemExit("incomplete or fabricated physical startup evidence was accepted")
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
