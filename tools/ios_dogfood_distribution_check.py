#!/usr/bin/env python3
"""Validate the development-signed, in-place physical iPhone dogfood path."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        raise SystemExit(f"missing required iOS dogfood file: {relative_path}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> int:
    workflow = read(".github/workflows/ios-dogfood.yml")
    smoke = read("tools/dogfood_ios_deeplink_smoke.sh")
    entry = read("tools/dogfood_iphone_entry.sh")
    project = read("client/ios/Runner.xcodeproj/project.pbxproj")
    development_entitlements = read("client/ios/Runner/RunnerDevelopment.entitlements")
    release_entitlements = read("client/ios/Runner/Runner.entitlements")
    docs = read("docs/ios-dogfood-distribution.md")

    require("name: Prepare Human Test" in workflow, "physical iPhone workflow must be the one-click human-test preparation")
    require("workflow_dispatch:" in workflow and "candidate_sha:" in workflow, "human-test preparation must select one exact dogfood SHA")
    require("Full Compose E2E" in workflow and ".github/workflows/live-stack-e2e.yml" in workflow, "physical install is not gated by exact green E2E")
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
        "mail.weave.test:44443",
    ):
        require(phrase in docs, f"iOS dogfood documentation is missing {phrase!r}")

    print("ios-dogfood-distribution-check: ok channel=development-in-place")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
