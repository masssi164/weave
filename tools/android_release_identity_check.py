#!/usr/bin/env python3
"""Validate Android package identity and release-signing fail-safe posture."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "client" / "android"
APP_GRADLE = ANDROID / "app" / "build.gradle.kts"
MANIFEST = ANDROID / "app" / "src" / "main" / "AndroidManifest.xml"
MAIN_ACTIVITY = ANDROID / "app" / "src" / "main" / "kotlin" / "com" / "massimotter" / "weave" / "MainActivity.kt"
GITIGNORE = ROOT / ".gitignore"
PACKAGE_ID = "com.massimotter.weave"
TEMPLATE_ID = "com.example.weave"


def fail(message: str) -> None:
    print(f"android-release-identity-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(text: str, fragment: str, label: str) -> None:
    if fragment not in text:
        fail(f"{label} missing {fragment!r}")


def main() -> None:
    gradle = read(APP_GRADLE)
    manifest = read(MANIFEST)
    activity = read(MAIN_ACTIVITY)
    gitignore = read(GITIGNORE)

    for path in ANDROID.rglob("*"):
        if path.is_file() and TEMPLATE_ID in path.read_text(encoding="utf-8", errors="ignore"):
            fail(f"template package id remains in {path.relative_to(ROOT)}")

    require(gradle, f'namespace = "{PACKAGE_ID}"', "Android Gradle config")
    require(gradle, f'applicationId = "{PACKAGE_ID}"', "Android Gradle config")
    require(gradle, f'"appAuthRedirectScheme" to "{PACKAGE_ID}"', "Android app-auth redirect config")
    require(manifest, 'android:scheme="${appAuthRedirectScheme}"', "Android manifest")
    require(activity, f"package {PACKAGE_ID}", "MainActivity package")

    forbidden_fragments = [
        'signingConfig = signingConfigs.getByName("debug")',
        'Signing with the debug keys',
    ]
    for fragment in forbidden_fragments:
        if fragment in gradle:
            fail(f"release signing still contains forbidden debug fallback: {fragment!r}")

    require(gradle, 'rootProject.file("key.properties")', "release signing config")
    require(gradle, "storeFile", "release signing config")
    require(gradle, "storePassword", "release signing config")
    require(gradle, "keyAlias", "release signing config")
    require(gradle, "keyPassword", "release signing config")
    require(gradle, "Debug signing must not be used for release artifacts.", "release signing guard")

    for fragment in ["**/android/key.properties", "**/android/*.jks", "**/android/*.keystore"]:
        require(gitignore, fragment, ".gitignore")

    print("android-release-identity-check: ok")


if __name__ == "__main__":
    main()
