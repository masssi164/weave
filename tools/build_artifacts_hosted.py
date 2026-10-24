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
import struct
import subprocess
import tempfile

from build_artifacts import (ArtifactError, ROOT, component_configuration, digest_file,
                             git, publish_manifest, require_clean_source)
from runtime_image_registry import publish as publish_runtime_images


def run(root: Path, arguments: list[str], *, capture: bool = False, env: dict | None = None) -> str:
    result = subprocess.run(arguments, cwd=root, check=True, text=True, env=env,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""


def copy_bundle(source: Path, destination: Path) -> None:
    if not source.is_dir() or source.is_symlink():
        raise ArtifactError("built-bundle-unavailable")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, destination, symlinks=True)


def simulator_entitlement_sections(executable: Path) -> list[dict[str, bytes]]:
    """Read the simulator entitlements from every executable slice, without changing it."""
    data = executable.read_bytes()
    try:
        magic = data[:4]
        if magic in {b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"}:
            count = struct.unpack_from(">I", data, 4)[0]
            entry_size = 20 if magic == b"\xca\xfe\xba\xbe" else 32
            if not count or 8 + count * entry_size > len(data):
                raise ValueError()
            slices = []
            for index in range(count):
                position = 8 + index * entry_size
                architecture = struct.unpack_from(">II", data, position)
                offset, size = struct.unpack_from(">II" if entry_size == 20 else ">QQ", data, position + 8)
                if offset < 8 + count * entry_size or not size or offset + size > len(data):
                    raise ValueError()
                slices.append((architecture, data[offset:offset + size]))
        else:
            slices = [(None, data)]
        results, architectures = [], set()
        for architecture, binary in slices:
            magic, cpu, subtype, filetype, count, command_bytes, _, _ = struct.unpack_from("<8I", binary)
            if (magic != 0xFEEDFACF or filetype != 2 or cpu not in {0x01000007, 0x0100000C} or cpu in architectures
                    or (architecture is not None and architecture != (cpu, subtype))):
                raise ValueError()
            architectures.add(cpu)
            command_end, position = 32 + command_bytes, 32
            if command_end > len(binary) or count > command_bytes // 8:
                raise ValueError()
            sections = {}
            for _ in range(count):
                command, size = struct.unpack_from("<II", binary, position)
                if size < 8 or position + size > command_end:
                    raise ValueError()
                if command == 0x19:  # LC_SEGMENT_64
                    if size < 72:
                        raise ValueError()
                    parent_segment = binary[position + 8:position + 24].rstrip(b"\0")
                    section_count = struct.unpack_from("<I", binary, position + 64)[0]
                    if 72 + section_count * 80 > size:
                        raise ValueError()
                    for index in range(section_count):
                        section = position + 72 + index * 80
                        name = binary[section:section + 16].rstrip(b"\0")
                        segment = binary[section + 16:section + 32].rstrip(b"\0")
                        if segment != b"__TEXT" or name not in {b"__entitlements", b"__ents_der"}:
                            continue
                        if parent_segment != segment:
                            raise ValueError()
                        length = struct.unpack_from("<Q", binary, section + 40)[0]
                        offset = struct.unpack_from("<I", binary, section + 48)[0]
                        key = name.decode("ascii")
                        if key in sections or not length or offset + length > len(binary):
                            raise ValueError()
                        sections[key] = binary[offset:offset + length]
                position += size
            if position != command_end or "__entitlements" not in sections:
                raise ValueError()
            results.append(sections)
        if 0x0100000C not in architectures:
            raise ValueError()
        return results
    except (struct.error, ValueError):
        raise ArtifactError("simulator-app-entitlements-missing-or-malformed") from None


def verify_simulator_app(client: Path, app: Path, environment: dict) -> None:
    # Simulator capabilities are linker sections, not the macOS codesign entitlement slots.
    # Xcode's processed input preserves its actual App ID prefix and configured access groups.
    run(client, ["codesign", "--verify", "--deep", "--strict", "--all-architectures", str(app)], env=environment)
    settings_result = json.loads(run(client, ["xcodebuild", "-workspace", "ios/Runner.xcworkspace",
        "-scheme", "Runner", "-configuration", "Debug", "-sdk", "iphonesimulator",
        "-destination", "generic/platform=iOS Simulator", "BUILD_DIR=" + str((client / "build/ios").resolve()),
        "-showBuildSettings", "-json"], capture=True, env=environment))
    try:
        targets = [item["buildSettings"] for item in settings_result if item.get("target") == "Runner"]
        if len(targets) != 1:
            raise ValueError()
        settings = targets[0]
        info = plistlib.loads((app / "Info.plist").read_bytes())
        bundle, executable = info["CFBundleIdentifier"], info["CFBundleExecutable"]
        if (not isinstance(bundle, str) or not bundle or bundle != settings["PRODUCT_BUNDLE_IDENTIFIER"]
                or app.name != settings["FULL_PRODUCT_NAME"] or not isinstance(executable, str)
                or not executable or Path(executable).name != executable or executable in {".", ".."}):
            raise ValueError()
        requested = plistlib.loads((Path(settings["SRCROOT"]) / settings["CODE_SIGN_ENTITLEMENTS"]).read_bytes())
        expected_path = Path(settings.get("LD_ENTITLEMENTS_SECTION") or
                             str(Path(settings["TARGET_TEMP_DIR"]) / (settings["FULL_PRODUCT_NAME"] + "-Simulated.xcent")))
        expected = plistlib.loads(expected_path.read_bytes())
        requested_groups, groups = requested["keychain-access-groups"], expected["keychain-access-groups"]
        if (not isinstance(requested_groups, list) or not requested_groups
                or not all(isinstance(group, str) and group for group in requested_groups)
                or not isinstance(groups, list) or not groups
                or not all(isinstance(group, str) and group and "$" not in group and "*" not in group for group in groups)):
            raise ValueError()
        identifier = expected.get("application-identifier")
        if identifier is not None and (not isinstance(identifier, str) or
                not (identifier == bundle or identifier.endswith("." + bundle)) or "$" in identifier or "*" in identifier):
            raise ValueError()
        templates = [group.replace("$(CFBundleIdentifier)", bundle).replace("$(PRODUCT_BUNDLE_IDENTIFIER)", bundle)
                     for group in requested_groups]
        prefix_token = "$(AppIdentifierPrefix)"
        prefix = identifier[:-len(bundle)] if identifier is not None else None
        if prefix is None and templates[0].startswith(prefix_token):
            suffix = templates[0][len(prefix_token):]
            if suffix and groups[0].endswith(suffix):
                prefix = groups[0][:-len(suffix)]
        # Use Xcode's processed prefix, which need not equal the development team.
        if groups != [template.replace(prefix_token, prefix or "") for template in templates]:
            raise ValueError()
        der_path = Path(settings.get("LD_ENTITLEMENTS_SECTION_DER") or str(expected_path) + ".der")
        expected_der = der_path.read_bytes() if der_path.is_file() else None
        if expected_der == b"" or (settings.get("LD_ENTITLEMENTS_SECTION_DER") and expected_der is None):
            raise ValueError()
        for sections in simulator_entitlement_sections(app / executable):
            actual = plistlib.loads(sections["__entitlements"].rstrip(b"\0"))
            if actual != expected or sections.get("__ents_der") != expected_der:
                raise ArtifactError("simulator-app-entitlements-mismatch")
    except (KeyError, TypeError, AttributeError, ValueError, OSError):
        raise ArtifactError("simulator-app-signing-evidence-invalid") from None
    print("WEAVE_HOSTED_SIMULATOR_SIGNING_RESULT variant=" + app.parent.name + " status=passed")


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
    evidence = json.loads((payload / "keycloak-build-evidence.json").read_text())
    if evidence.get("supportSafe") is not True or evidence.get("containsSecretValues") is not False:
        raise ArtifactError("keycloak-build-evidence-not-support-safe")
    # The immutable registry receipt replaces a large Docker-save archive.
    # The Mac runner pulls these *same* image IDs by the recorded registry digests.
    (payload / "images.json").write_text(json.dumps(
        publish_runtime_images(images, source, spec_digest), indent=2) + "\n")


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
    signing_flags = [] if simulator else ["--no-codesign"]
    ordinary_build = ["flutter", "build", "ios", mode, *target_flags, *signing_flags, "--target=lib/main.dart", *defines]
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
        verify_simulator_app(client, payload / "ordinary/Runner.app", environment)
        run(client, ["flutter", "build", "ios", "--simulator", "--debug",
                     "--target=integration_test/dual_ios_collaboration_test.dart", *defines,
                     "--dart-define=WEAVE_NATIVE_EXPECT_SIMULATOR=true",
                     "--dart-define=WEAVE_DEVICE_DISPOSABLE_STACK=true"], env=environment)
        require_clean_source(root, "apple-collaboration")
        copy_bundle(source_app, payload / "collaboration/Runner.app")
        verify_simulator_app(client, payload / "collaboration/Runner.app", environment)
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
