#!/usr/bin/env python3
"""Validate the development-signed, in-place physical iPhone dogfood path."""

from __future__ import annotations

from pathlib import Path
from datetime import datetime, timedelta, timezone
from unittest.mock import patch
import json
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


def main() -> int:
    check_discovery_evidence()
    workflow = read(".github/workflows/ios-dogfood.yml")
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
    require("preparation-result.json" in workflow and "qualificationRunId" in workflow,
            "physical preparation must bind installed source to qualification")
    require("./gradlew --no-daemon dogfoodUp" in workflow, "physical preparation does not start Compose dogfood")
    require("tools/dogfood_cert_persistence_smoke.py" in workflow, "TLS identity stability is not checked")
    require("tools/dogfood_iphone_entry.sh" in workflow, "physical-device installation entrypoint is missing")
    require("--reset-mode update_in_place" in workflow, "workflow does not preserve Developer App trust")
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
