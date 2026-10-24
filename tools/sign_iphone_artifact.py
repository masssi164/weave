#!/usr/bin/env python3
"""Sign a verified hosted iPhone artifact using existing local profiles; never compile."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import platform
import plistlib
import re
import shutil
import subprocess
import tempfile

from build_artifacts import ArtifactError, ROOT, SHA, digest_file, tree_digest, verify_component


class SigningError(ArtifactError):
    """An actionable local preparation boundary, not permission to rebuild or re-enroll."""


def run(arguments: list[str]) -> bytes:
    try:
        return subprocess.check_output(arguments, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError:
        raise SigningError("local-signing-or-keychain-operation-failed") from None


def profile_compatible(profile: dict, bundle: str, team: str, identity: str, device: str,
                       now: datetime | None = None) -> bool:
    now = now or datetime.now(timezone.utc)
    expires = profile.get("ExpirationDate")
    if isinstance(expires, datetime) and expires.tzinfo is None:
        expires = expires.replace(tzinfo=timezone.utc)
    entitlements = profile.get("Entitlements", {})
    prefix = profile.get("ApplicationIdentifierPrefix", [])
    application = entitlements.get("application-identifier", "")
    return bool(isinstance(expires, datetime) and expires > now
        and profile.get("TeamIdentifier") == [team]
        and len(prefix) == 1 and re.fullmatch(r"[A-Z0-9]{10}", prefix[0])
        and entitlements.get("com.apple.developer.team-identifier") == team
        and entitlements.get("get-task-allow") is True
        and fnmatch.fnmatchcase(prefix[0] + "." + bundle, application)
        and device in profile.get("ProvisionedDevices", [])
        and any(hashlib.sha1(cert).hexdigest().upper() == identity.upper()
                for cert in profile.get("DeveloperCertificates", []) if isinstance(cert, bytes)))


def select_profile(directories: list[Path], bundle: str, team: str, identity: str, device: str) -> tuple[Path, dict]:
    matches = []
    for directory in directories:
        if directory.is_symlink() or not directory.is_dir():
            continue
        for path in sorted(directory.glob("*.mobileprovision")):
            if path.is_symlink() or not path.is_file():
                continue
            try:
                profile = plistlib.loads(run(["security", "cms", "-D", "-i", str(path)]))
                if profile_compatible(profile, bundle, team, identity, device):
                    matches.append((path, profile))
            except (SigningError, ValueError, TypeError):
                continue
    if not matches:
        raise SigningError("existing-device-provisioning-profile-required-" + bundle)
    # A renewal may coexist with its previous profile. Select the latest compatible
    # expiry deterministically; no profile is created or changed here.
    return max(matches, key=lambda item: (item[1]["ExpirationDate"].replace(tzinfo=timezone.utc).timestamp(),
                                          item[1].get("UUID", "")))


def select_identity(directories: list[Path], bundles: list[str], team: str, device: str) -> str:
    available = set(re.findall(rb"\b([A-Fa-f0-9]{40})\b", run(["security", "find-identity", "-v", "-p", "codesigning"])))
    matching = []
    for encoded in available:
        identity = encoded.decode().upper()
        try:
            for bundle in bundles:
                select_profile(directories, bundle, team, identity, device)
            matching.append(identity)
        except SigningError:
            continue
    if len(matching) != 1:
        raise SigningError("one-existing-compatible-signing-identity-required")
    return matching[0]


def verify_certificate_team(profile: dict, identity: str, expected: str | None) -> None:
    if expected is None:
        return
    if not re.fullmatch(r"[A-Z0-9]{10}", expected):
        raise SigningError("expected-developer-certificate-team-invalid")
    certificate, = [item for item in profile["DeveloperCertificates"]
                    if hashlib.sha1(item).hexdigest().upper() == identity.upper()]
    with tempfile.NamedTemporaryFile(suffix=".der") as temporary:
        Path(temporary.name).write_bytes(certificate)
        subject = run(["openssl", "x509", "-inform", "DER", "-in", temporary.name,
                       "-noout", "-subject", "-nameopt", "sep_multiline,sname,utf8"]).decode()
    observed = re.findall(r"^\s*OU\s*=\s*([A-Z0-9]{10})\s*$", subject, re.MULTILINE)
    if observed != [expected]:
        raise SigningError("developer-certificate-team-mismatch")


def signing_entitlements(profile: dict, bundle: str, requested: dict) -> dict:
    permitted = profile["Entitlements"]
    prefix = profile["ApplicationIdentifierPrefix"][0] + "."
    result = {"application-identifier": prefix + bundle,
              "com.apple.developer.team-identifier": profile["TeamIdentifier"][0],
              "get-task-allow": True}
    for key, value in requested.items():
        if isinstance(value, list):
            resolved = [item.replace("$(AppIdentifierPrefix)", prefix).replace("$(CFBundleIdentifier)", bundle)
                        if isinstance(item, str) else item for item in value]
            allowed = permitted.get(key, [])
            if not isinstance(allowed, list) or not all(isinstance(item, str) and
                    any(isinstance(pattern, str) and fnmatch.fnmatchcase(item, pattern) for pattern in allowed)
                    for item in resolved):
                raise SigningError("existing-profile-capability-mismatch")
            result[key] = resolved
        elif permitted.get(key) != value:
            raise SigningError("existing-profile-capability-mismatch")
        else:
            result[key] = value
    return result


def sign_bundle(app: Path, profile_path: Path, profile: dict, identity: str, requested: dict) -> dict:
    info = plistlib.loads((app / "Info.plist").read_bytes())
    bundle = info["CFBundleIdentifier"]
    shutil.copy2(profile_path, app / "embedded.mobileprovision")
    entitlements = signing_entitlements(profile, bundle, requested)
    # Innermost code first; --deep is used only for verification, never for signing.
    nested = sorted((path for path in app.rglob("*") if not path.is_symlink()
                     and (path.suffix in {".framework", ".xctest", ".appex", ".dylib"})),
                    key=lambda path: len(path.parts), reverse=True)
    for path in nested:
        if path.suffix == ".appex":
            raise SigningError("nested-extension-needs-own-provisioning-profile")
        run(["codesign", "--force", "--sign", identity, "--timestamp=none", str(path)])
    with tempfile.NamedTemporaryFile(suffix=".plist") as file:
        Path(file.name).write_bytes(plistlib.dumps(entitlements))
        run(["codesign", "--force", "--sign", identity, "--timestamp=none", "--entitlements", file.name, str(app)])
    run(["codesign", "--verify", "--deep", "--strict", str(app)])
    observed = plistlib.loads(run(["codesign", "--display", "--entitlements", ":-", str(app)]))
    if observed.get("application-identifier") != entitlements["application-identifier"]:
        raise SigningError("signed-application-identity-mismatch")
    return {"bundleId": bundle, "profileSha256": digest_file(profile_path),
            "signedTreeDigest": tree_digest(app), "profileExpiresAt": profile["ExpirationDate"].isoformat()}


def sign(root: Path, artifact: Path, output: Path, candidate: str, team: str, identity: str | None,
         device: str, profiles: list[Path], certificate_team: str | None = None) -> dict:
    if platform.system() != "Darwin":
        raise SigningError("local-macos-signing-required")
    if not re.fullmatch(r"[A-Z0-9]{10}", team) or (identity is not None and not re.fullmatch(r"[A-Fa-f0-9]{40}", identity)):
        raise SigningError("explicit-team-and-valid-signing-identity-required")
    if not re.fullmatch(r"[A-Fa-f0-9-]{20,40}", device):
        raise SigningError("physical-device-provisioning-udid-required")
    if not SHA.fullmatch(candidate):
        raise SigningError("exact-candidate-commit-required")
    manifest = verify_component(root, artifact, candidate, "ios-device")
    if output.exists() or output.is_symlink():
        raise SigningError("fresh-owned-signing-output-required")
    payload = artifact / "payload"
    ordinary = payload / "ordinary/Runner.app"
    drivers = list((payload / "driver/Products").glob("**/*-Runner.app"))
    if len(drivers) != 1:
        raise SigningError("one-prebuilt-device-test-runner-required")
    sources = [ordinary, drivers[0]]
    bundles = [plistlib.loads((app / "Info.plist").read_bytes())["CFBundleIdentifier"] for app in sources]
    identity = identity or select_identity(profiles, bundles, team, device)
    selected = []
    for app, bundle in zip(sources, bundles):
        profile_path, profile = select_profile(profiles, bundle, team, identity, device)
        verify_certificate_team(profile, identity, certificate_team)
        selected.append((app, profile_path, profile))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".weave-signing-", dir=output.parent) as temporary:
        staged = Path(temporary) / "signed"
        shutil.copytree(payload, staged, symlinks=True)
        bindings = []
        for original, profile_path, profile in selected:
            app = staged / original.relative_to(payload)
            requested = plistlib.loads((payload / "ordinary/entitlements.plist").read_bytes()) if original == ordinary else {}
            bindings.append(sign_bundle(app, profile_path, profile, identity, requested))
        receipt = {"schemaVersion": "weave.signed-iphone-artifact/v1", "candidateCommit": candidate,
            "sourceCommit": manifest["sourceCommit"], "inputDigest": manifest["inputDigest"],
            "unsignedPayloadSha256": manifest["payload"]["sha256"], "teamId": team,
            "signingIdentitySha1": identity.upper(), "certificateTeamId": certificate_team,
            "buildNumber": manifest["buildNumber"],
            "signedPayloadTreeDigest": tree_digest(staged), "applications": bindings,
            "status": "passed", "supportSafe": True, "containsSecretValues": False}
        (staged / "signing-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        staged.rename(output)
    return receipt


def verify_signed(root: Path, artifact: Path, output: Path, candidate: str) -> dict:
    manifest = verify_component(root, artifact, candidate, "ios-device")
    receipt = json.loads((output / "signing-receipt.json").read_text())
    expected = {"schemaVersion": "weave.signed-iphone-artifact/v1", "candidateCommit": candidate,
                "sourceCommit": manifest["sourceCommit"], "inputDigest": manifest["inputDigest"],
                "unsignedPayloadSha256": manifest["payload"]["sha256"], "buildNumber": manifest["buildNumber"],
                "status": "passed", "supportSafe": True, "containsSecretValues": False}
    if (any(receipt.get(key) != value for key, value in expected.items())
            or tree_digest(output, exclude=frozenset({"signing-receipt.json"})) != receipt.get("signedPayloadTreeDigest")):
        raise SigningError("signed-artifact-binding-mismatch")
    apps = [output / "ordinary/Runner.app", *list((output / "driver/Products").glob("**/*-Runner.app"))]
    if len(apps) != 2:
        raise SigningError("signed-artifact-applications-missing")
    for app in apps:
        run(["codesign", "--verify", "--deep", "--strict", str(app)])
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--artifact-directory", type=Path, required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--team", required=True)
    parser.add_argument("--identity-sha1")
    parser.add_argument("--certificate-team")
    parser.add_argument("--device-udid", required=True)
    parser.add_argument("--profiles", type=Path, action="append")
    args = parser.parse_args()
    profiles = args.profiles or [Path.home() / "Library/MobileDevice/Provisioning Profiles",
                                Path.home() / "Library/Developer/Xcode/UserData/Provisioning Profiles"]
    result = sign(args.root, args.artifact_directory, args.output, args.candidate, args.team,
                  args.identity_sha1, args.device_udid, profiles, args.certificate_team)
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ArtifactError, OSError, ValueError, KeyError) as error:
        print("WEAVE_IPHONE_SIGNING_ERROR " + (str(error) if isinstance(error, ArtifactError) else type(error).__name__))
        raise SystemExit(2) from None
