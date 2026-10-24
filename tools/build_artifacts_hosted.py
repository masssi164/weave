#!/usr/bin/env python3
"""Build portable components on hosted CI; local consumers never invoke this recipe."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import plistlib
import shutil
import subprocess
import tempfile

from build_artifacts import (ArtifactError, ROOT, component_configuration, digest_file,
                             git, publish_manifest, require_clean_source)


def run(root: Path, arguments: list[str], *, capture: bool = False, env: dict | None = None) -> str:
    result = subprocess.run(arguments, cwd=root, check=True, text=True, env=env,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""


def copy_bundle(source: Path, destination: Path) -> None:
    if not source.is_dir() or source.is_symlink():
        raise ArtifactError("built-bundle-unavailable")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, destination, symlinks=True)


def portable_test_plans(products: Path, original_products: Path) -> None:
    plans = list(products.glob("*.xctestrun"))
    if len(plans) != 1:
        raise ArtifactError("one-portable-test-plan-required")
    def relocate(value):
        if isinstance(value, dict):
            return {key: relocate(item) for key, item in value.items()}
        if isinstance(value, list):
            return [relocate(item) for item in value]
        if isinstance(value, str):
            return value.replace(str(original_products), "__TESTROOT__")
        return value
    plans[0].write_bytes(plistlib.dumps(relocate(plistlib.loads(plans[0].read_bytes()))))


def runtime(root: Path, payload: Path, source: str) -> None:
    if platform.system() != "Linux" or platform.machine() not in {"x86_64", "amd64"}:
        raise ArtifactError("hosted-linux-amd64-required")
    environment = {**os.environ, "WEAVE_OPENDAL_CLASSIFIER": "linux-x86_64",
                   "RUSTUP_TOOLCHAIN": component_configuration(root, "runtime-linux-amd64")["toolchain"]["rust"]}
    run(root, ["./gradlew", "--no-daemon", "--max-workers=2", "--init-script", "tools/build_artifacts_driver.gradle",
               ":server:bootJar", ":weave-mcp-server:bootJar", ":weave-product-e2e:installDist"], env=environment)
    copy_bundle(root / "weave-product-e2e/build/install/weave-product-e2e", payload / "test-driver")
    created = git(root, "show", "-s", "--format=%cI", source).decode().strip()
    spec_digest = digest_file(root / "specs/weave-specs.lock.json")
    common = ["--build-arg", "WEAVE_IMAGE_CREATED=" + created,
              "--build-arg", "WEAVE_IMAGE_REVISION=" + source,
              "--build-arg", "WEAVE_IMAGE_VERSION=artifact-" + source[:12],
              "--build-arg", "WEAVE_SPEC_DIGEST=" + spec_digest,
              "--build-arg", "WEAVE_SBOM_REFERENCE=github-build-artifact",
              "--build-arg", "WEAVE_PROVENANCE_REFERENCE=github-build-artifact"]
    images = {}
    for name, module in (("backend", "server"), ("mcp", "weave-mcp-server")):
        tag = "weave-" + name + ":artifact-" + source[:12]
        run(root, ["docker", "build", "--platform", "linux/amd64", *common, "--tag", tag,
                   "--file", str(root / module / "Dockerfile"), str(root)])
        images[name] = run(root, ["docker", "image", "inspect", tag, "--format", "{{.Id}}"], capture=True)
    images["keycloak"] = run(root, ["python3", "infra/weave-workspace/scripts/build_keycloak_image.py",
                                    "--root", str(root), "--candidate-commit", source,
                                    "--output", str(payload / "keycloak-build-evidence.json")], capture=True).splitlines()[-1]
    images["runtimeState"] = run(root, ["bash", "infra/weave-workspace/scripts/build_runtime_state_image.sh"], capture=True).splitlines()[-1]
    run(root, ["docker", "save", "--output", str(payload / "images.tar"), *images.values()])
    (payload / "images.json").write_text(json.dumps({"schemaVersion": "weave.runtime-images/v1",
        "sourceCommit": source, "specDigest": spec_digest, "platform": "linux/amd64", "images": images}, indent=2) + "\n")
    evidence = json.loads((payload / "keycloak-build-evidence.json").read_text())
    if evidence.get("supportSafe") is not True or evidence.get("containsSecretValues") is not False:
        raise ArtifactError("keycloak-build-evidence-not-support-safe")


def apple(root: Path, component: str, payload: Path, source: str, *, configuration_only: bool = False) -> None:
    definition = component_configuration(root, component)
    pins = definition["toolchain"]
    if platform.system() != "Darwin" or platform.machine() != "arm64":
        raise ArtifactError("hosted-macos-arm64-required")
    if run(root, ["xcodebuild", "-version"], capture=True) != "Xcode " + pins["xcode"] + "\nBuild version " + pins["xcodeBuild"]:
        raise ArtifactError("pinned-xcode-unavailable")
    sdk = "macosx" if component == "macos-desktop" else "iphonesimulator" if component == "ios-simulator" else "iphoneos"
    if run(root, ["xcrun", "--sdk", sdk, "--show-sdk-version"], capture=True) != pins["sdk"]:
        raise ArtifactError("pinned-apple-sdk-unavailable")
    if run(root, ["pod", "--version"], capture=True) != pins["cocoapods"]:
        raise ArtifactError("pinned-cocoapods-unavailable")
    flutter = json.loads(run(root, ["flutter", "--version", "--machine"], capture=True))
    if flutter.get("frameworkVersion") != pins["flutter"] or flutter.get("channel") != "stable":
        raise ArtifactError("pinned-flutter-unavailable")
    environment = {**os.environ, "RUSTUP_TOOLCHAIN": pins["rust"]}
    client = root / "client"
    run(client, ["flutter", "pub", "get", "--enforce-lockfile"], env=environment)
    run(client, ["flutter", "gen-l10n"], env=environment)
    require_clean_source(root, "apple-dependencies")
    number = os.environ["GITHUB_RUN_NUMBER"]
    defines = ["--build-number=" + number, "--dart-define=WEAVE_CANDIDATE_COMMIT=" + source,
               "--dart-define=WEAVE_BUILD_CHANNEL=development-dogfood"]
    if component == "macos-desktop":
        run(client, ["flutter", "build", "macos", "--debug", "--config-only", "--target=lib/main.dart", *defines], env=environment)
        require_clean_source(root, "apple-macos-configuration")
        if configuration_only:
            return
        derived = client / "build/hosted-macos"
        run(client, ["xcodebuild", "-workspace", "macos/Runner.xcworkspace", "-scheme", "Runner",
                     "-configuration", "Debug", "-derivedDataPath", str(derived), "-destination", "generic/platform=macOS",
                     "ARCHS=arm64", "ONLY_ACTIVE_ARCH=YES", "CODE_SIGNING_ALLOWED=NO", "-jobs", "2", "build"], env=environment)
        copy_bundle(derived / "Build/Products/Debug/weave.app", payload / "weave.app")
        return
    simulator = component == "ios-simulator"
    mode = "--debug" if simulator else "--profile"
    target_flags = ["--simulator"] if simulator else []
    ordinary_build = ["flutter", "build", "ios", mode, *target_flags, "--no-codesign", "--target=lib/main.dart", *defines]
    # Flutter config-only runs project migrations and CocoaPods without compiling the app.
    run(client, [*ordinary_build, "--config-only"], env=environment)
    require_clean_source(root, "apple-ios-configuration")
    if configuration_only:
        return
    run(client, ordinary_build, env=environment)
    require_clean_source(root, "apple-ordinary")
    source_app = client / "build/ios" / ("iphonesimulator" if simulator else "iphoneos") / "Runner.app"
    copy_bundle(source_app, payload / "ordinary/Runner.app")
    if simulator:
        run(client, ["flutter", "build", "ios", "--simulator", "--debug", "--no-codesign",
                     "--target=integration_test/dual_ios_collaboration_test.dart", *defines,
                     "--dart-define=WEAVE_NATIVE_EXPECT_SIMULATOR=true",
                     "--dart-define=WEAVE_DEVICE_DISPOSABLE_STACK=true"], env=environment)
        require_clean_source(root, "apple-collaboration")
        copy_bundle(source_app, payload / "collaboration/Runner.app")
    else:
        shutil.copy2(client / "ios/Runner/RunnerDevelopment.entitlements", payload / "ordinary/entitlements.plist")
    with tempfile.TemporaryDirectory(prefix="weave-hosted-xctest-") as name:
        derived = Path(name)
        run(client, ["xcodebuild", "build-for-testing", "-project", str(client / "tool/ios_browser_driver/BrowserDriver.xcodeproj"),
                     "-scheme", "BrowserDriver", "-destination", "generic/platform=" + ("iOS Simulator" if simulator else "iOS"),
                     "-derivedDataPath", str(derived), "-sdk", sdk, "ARCHS=arm64", "ONLY_ACTIVE_ARCH=YES",
                     "CODE_SIGNING_ALLOWED=NO", "CODE_SIGNING_REQUIRED=NO"], env=environment)
        require_clean_source(root, "apple-driver")
        copy_bundle(derived / "Build/Products", payload / "driver/Products")
        portable_test_plans(payload / "driver/Products", derived / "Build/Products")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--component", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--configuration-only", action="store_true",
                        help="Diagnose Apple dependency/project preparation without compiling or publishing artifacts")
    args = parser.parse_args()
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
        raise ArtifactError("hosted-build-required")
    component_configuration(args.root, args.component)
    if args.configuration_only and args.component == "runtime-linux-amd64":
        raise ArtifactError("configuration-only-apple-component-required")
    source = git(args.root, "rev-parse", "HEAD").decode().strip()
    if source != os.environ.get("GITHUB_SHA"):
        raise ArtifactError("hosted-source-mismatch")
    require_clean_source(args.root, "before-build")
    with tempfile.TemporaryDirectory(prefix="weave-hosted-payload-") as temporary:
        payload = Path(temporary) / "payload"
        payload.mkdir()
        if args.component == "runtime-linux-amd64":
            runtime(args.root, payload, source)
        else:
            apple(args.root, args.component, payload, source, configuration_only=args.configuration_only)
        if args.configuration_only:
            print("WEAVE_HOSTED_CONFIGURATION_RESULT component=" + args.component + " status=passed")
            return 0
        manifest = publish_manifest(args.root, args.component, payload, args.output)
    print("WEAVE_HOSTED_BUILD_RESULT component=" + args.component + " input=" + manifest["inputDigest"] + " status=passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ArtifactError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print("WEAVE_HOSTED_BUILD_ERROR " + (str(error) if isinstance(error, ArtifactError) else type(error).__name__))
        raise SystemExit(2) from None
