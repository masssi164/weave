#!/usr/bin/env python3
"""Validate the GitHub-only stable iOS dogfood distribution contract."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    workflow = read(".github/workflows/ios-dogfood.yml")
    project = read("client/ios/Runner.xcodeproj/project.pbxproj")
    entitlements = read("client/ios/Runner/Runner.entitlements")
    docs = read("docs/ios-dogfood-distribution.md")

    require("pull_request:" not in workflow, "TestFlight workflow must not run for pull requests")
    require(re.search(r"branches:\s*\n\s*- dogfood", workflow), "TestFlight workflow is not bound to dogfood")
    require("workflow_dispatch:" in workflow, "TestFlight workflow has no explicit manual dispatch")
    require("name: ios-dogfood" in workflow, "TestFlight upload is not protected by ios-dogfood environment")
    require("xcrun altool --upload-app" in workflow, "TestFlight workflow does not upload through Apple tooling")
    require("credentialsIncluded:false" in workflow, "distribution evidence does not deny credential inclusion")

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

    for phrase in (
        "TestFlight",
        "update in place",
        "ios-dogfood",
        "DOGFOOD_SESSION_CONTINUITY_RESULT",
        "destructive uninstall",
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
