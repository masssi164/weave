#!/usr/bin/env python3
"""Validate the GitHub-only stable iOS dogfood distribution contract."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    workflow = read(".github/workflows/ios-dogfood.yml")
    project = read("client/ios/Runner.xcodeproj/project.pbxproj")
    entitlements = read("client/ios/Runner/Runner.entitlements")
    development_entitlements = read("client/ios/Runner/RunnerDevelopment.entitlements")
    development_fallback = read("tools/dogfood_ios_development_fallback.sh")
    docs = read("docs/ios-dogfood-distribution.md")

    require("pull_request:" not in workflow, "TestFlight workflow must not run for pull requests")
    require("push:" not in workflow, "TestFlight must not race the persistent deployment on dogfood push")
    require("workflow_run:" in workflow, "TestFlight workflow must consume a completed deployment")
    require("- Test Stack Deploy" in workflow, "TestFlight workflow is not ordered after Test Stack Deploy")
    require(
        'gh run download "$deployment_run_id" --name weave-test-stack-evidence' in workflow,
        "TestFlight does not resolve its candidate from immutable deployment evidence",
    )
    require(
        "'.candidateCommit' \"$source_evidence\"" in workflow
        and "'.branch' \"$source_manifest\"" in workflow
        and "== dogfood" in workflow,
        "automatic TestFlight distribution is not bound to exact-candidate dogfood evidence",
    )
    require("workflow_dispatch:" in workflow, "TestFlight workflow has no explicit manual dispatch")
    require("candidate_sha:" in workflow and "deployment_run_id:" in workflow, "manual recovery dispatch must identify the exact deployed candidate")
    require("name: ios-dogfood" in workflow, "TestFlight upload is not protected by ios-dogfood environment")
    require("group: ios-dogfood" in workflow and "cancel-in-progress: true" in workflow, "superseded pending iOS candidates are not cancelled")
    require("No successful isolated Live Stack E2E run targets" in workflow, "iOS distribution does not require exact-candidate isolated E2E")
    require("Test Stack Deploy ${DEPLOYMENT_RUN_ID} is not successful" in workflow, "iOS distribution does not verify the deployment result")
    require("xcrun altool --upload-app" in workflow, "TestFlight workflow does not upload through Apple tooling")
    require("credentialsIncluded:false" in workflow, "distribution evidence does not deny credential inclusion")
    require("WEAVE_CANDIDATE_COMMIT=${CANDIDATE_SHA}" in workflow, "archive does not embed its candidate commit")
    require("WEAVE_CANDIDATE_EVIDENCE_REF=${DEPLOYMENT_RUN_URL}" in workflow, "archive does not embed its support-safe evidence reference")
    require("CFBundleShortVersionString" in workflow, "archive version is not verified")
    require("IOS_DOGFOOD_DISTRIBUTION_RESULT" in workflow, "distribution workflow has no stable evidence marker")

    for secret_ref in (
        "APPLE_DISTRIBUTION_CERTIFICATE_P12_BASE64",
        "APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD",
        "APPLE_PROVISIONING_PROFILE_BASE64",
        "APPLE_PROVISIONING_PROFILE_NAME",
        "APP_STORE_CONNECT_API_KEY_ID",
        "APP_STORE_CONNECT_ISSUER_ID",
        "APP_STORE_CONNECT_API_PRIVATE_KEY_BASE64",
    ):
        require(f"secrets.{secret_ref}" in workflow, f"missing protected SecretRef {secret_ref}")

    require(project.count("PRODUCT_BUNDLE_IDENTIFIER = com.massimotter.weave;") == 3, "iOS app bundle identity is not stable in Debug/Profile/Release")
    require(project.count("DEVELOPMENT_TEAM = KNDHGC2KV6;") == 3, "iOS Apple team identity is not stable in Debug/Profile/Release")
    require(project.count("CODE_SIGN_ENTITLEMENTS = Runner/Runner.entitlements;") == 3, "iOS entitlements are not applied in Debug/Profile/Release")
    require("$(AppIdentifierPrefix)com.massimotter.weave" in entitlements, "device-bound Keychain application identity is not explicit")
    require("com.apple.developer.associated-domains" in entitlements, "production/TestFlight entitlements lost Associated Domains")
    require("$(AppIdentifierPrefix)com.massimotter.weave" in development_entitlements, "development fallback lost the stable Keychain identity")
    require("com.apple.developer.associated-domains" not in development_entitlements, "Personal Team fallback must omit Associated Domains")
    for marker in (
        "WEAVE_CANDIDATE_COMMIT",
        "WEAVE_CANDIDATE_EVIDENCE_REF",
        "WEAVE_BUILD_NUMBER",
        "RunnerDevelopment.entitlements",
        "device install app",
        "inPlaceUpdate: true",
        "sessionContinuityClaimed: false",
        "IOS_DEVELOPMENT_FALLBACK_RESULT",
    ):
        require(marker in development_fallback, f"development-signed fallback is missing {marker!r}")
    for marker in (
        "stable-signing-fallback:",
        "inputs.upload_to_testflight == false",
        "EXPECTED_RUNNER_NAME: weave-live-mac-mini",
        "secrets.WEAVE_IOS_DEVICE_ID",
        "tools/dogfood_ios_development_fallback.sh",
        "ios-dogfood-distribution.json",
        "protected-stable-signing-fallback",
    ):
        require(marker in workflow, f"protected fallback workflow is missing {marker!r}")
    for marker in (
        'schemaVersion: "weave.ios-dogfood-distribution.v2"',
        'channel: "stable-signing-fallback"',
        'result: "success"',
        "credentialsIncluded: false",
        "sessionContinuityClaimed: false",
    ):
        require(marker in development_fallback, f"fallback distribution evidence is missing {marker!r}")

    for phrase in (
        "TestFlight",
        "update in place",
        "ios-dogfood",
        "DOGFOOD_SESSION_CONTINUITY_RESULT",
        "destructive uninstall",
        "approval request expires after 24 hours",
        "physical-iPhone VoiceOver acceptance",
        "Development-signed in-place fallback",
        "session continuity unclaimed",
        "upload_to_testflight=false",
    ):
        require(phrase in docs, f"iOS dogfood documentation is missing {phrase!r}")

    print("ios-dogfood-distribution-check: ok")
    return 0


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        raise SystemExit(f"missing required iOS dogfood file: {relative_path}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    raise SystemExit(main())
