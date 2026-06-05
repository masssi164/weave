#!/usr/bin/env python3
"""Validate Sprint 31 physical iPhone LAN dogfood artifacts and guards."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_TEXT = [
    "access_token=",
    "refresh_token=",
    "id_token=",
    "client_secret=",
    "password=",
    "secretref://",
    "raw provider payload",
    "raw provider diagnostics",
    "homeserver internals",
]
FORBIDDEN_MEMBER_INPUTS = [
    "OIDC issuer",
    "OIDC client ID",
    "Matrix URL",
    "Nextcloud URL",
    "provider hostname",
    "TLS certificate",
    "provider diagnostic",
    "SecretRef",
    "credential URL",
]


def fail(message: str) -> None:
    print(f"sprint31-lan-dogfood-check: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        fail(f"missing required file: {rel}")


def run(args: list[str], expect_success: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True, check=False)
    if expect_success and result.returncode != 0:
        fail(f"command failed: {' '.join(args)}\n{result.stdout}\n{result.stderr}")
    if not expect_success and result.returncode == 0:
        fail(f"command unexpectedly succeeded: {' '.join(args)}")
    return result


def assert_no_forbidden(rel: str) -> None:
    text = read(rel).lower()
    for fragment in FORBIDDEN_TEXT:
        if fragment.lower() in text and rel.endswith(".json"):
            fail(f"forbidden support detail in {rel}: {fragment}")


def main() -> None:
    profile = json.loads(read("release/sprint-31-physical-iphone-lan-dogfood/local-lan-dogfood.profile.json"))
    if profile.get("entrypoint") != "weavectl profile apply" or not profile.get("singlePipeline"):
        fail("Sprint 31 profile must use the unified weavectl profile apply pipeline")
    if profile.get("publicDnsRequired") or profile.get("trustedInternetTlsRequired"):
        fail("local-lan-dogfood must not require public DNS or trusted internet TLS")
    for item in FORBIDDEN_MEMBER_INPUTS:
        if item not in profile.get("forbiddenMemberInputs", []):
            fail(f"profile must forbid member input: {item}")

    with tempfile.TemporaryDirectory() as tmp:
        result = run([
            "python3",
            "tools/weavectl",
            "profile",
            "apply",
            "--profile",
            "local-lan-dogfood",
            "--lan-host",
            "192.168.42.10",
            "--emit-handoff",
            "--emit-evidence",
            "--preflight-mode",
            "validate-only",
            "--run-id",
            "s31-check",
            "--output-dir",
            tmp,
        ])
        if "tester_next_action=" not in result.stdout:
            fail("weavectl output must include tester next action")
        base = Path(tmp) / "s31-check"
        readiness = json.loads((base / "readiness.json").read_text(encoding="utf-8"))
        handoff = json.loads((base / "handoff.json").read_text(encoding="utf-8"))
        evidence = json.loads((base / "evidence.json").read_text(encoding="utf-8"))
        if readiness.get("phoneReachability", {}).get("hostClass") != "rfc1918-lan-ip":
            fail("readiness must classify RFC1918 LAN IP")
        if handoff.get("memberPath") != ["open handoff", "SSO", "Weave workspace home"]:
            fail("handoff must preserve normal member path")
        tester_visible = handoff.get("testerVisible", {})
        if not tester_visible.get("screenReaderPrompt"):
            fail("handoff must include screen-reader-friendly prompt")
        if not str(tester_visible.get("deepLink", "")).startswith("weave:/join?"):
            fail("handoff must include the separate Weave member handoff deep link scheme")
        for item in FORBIDDEN_MEMBER_INPUTS:
            if item not in handoff.get("forbiddenMemberInputs", []):
                fail(f"handoff must forbid member input: {item}")
        if evidence.get("supportSafe") is not True:
            fail("evidence must be marked support-safe")
        for artifact in [base / "readiness.json", base / "handoff.json", base / "evidence.json"]:
            text = artifact.read_text(encoding="utf-8").lower()
            for fragment in ["access_token", "client_secret", "secretref://", "credential_url"]:
                if fragment in text:
                    fail(f"credential-bearing fragment in generated artifact: {artifact}")

    for bad_host in ["localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal", "weave.local"]:
        run([
            "python3",
            "tools/weavectl",
            "profile",
            "apply",
            "--profile",
            "local-lan-dogfood",
            "--lan-host",
            bad_host,
            "--emit-handoff",
        ], expect_success=False)

    client_join = read("client/lib/features/onboarding/domain/entities/member_handoff.dart")
    for phrase in ["WEAVE-LAN-UNREACHABLE", "WEAVE-HANDOFF-SECRET-BLOCKED", "handoff_ref", "app_base_url"]:
        if phrase not in client_join:
            fail(f"client handoff parser missing {phrase}")
    sign_in = read("client/lib/features/auth/presentation/sign_in_screen.dart")
    for leaked in ["signInConfigurationIssuer", "signInConfigurationClientId"]:
        if leaked in sign_in:
            fail(f"sign-in screen must not display provider setup detail: {leaked}")
    ios_plist = read("client/ios/Runner/Info.plist")
    if "<string>weave</string>" not in ios_plist or "same local network" not in ios_plist:
        fail("iOS must register the separate Weave handoff scheme and explain LAN usage")
    android_manifest = read("client/android/app/src/main/AndroidManifest.xml")
    if "weave_member_handoff" not in android_manifest or "android:scheme=\"weave\"" not in android_manifest:
        fail("Android must register a separate Weave member handoff scheme on MainActivity")

    for rel in [
        "docs/sprint-31-iphone-lan-dogfood-runbook.md",
        "docs/evidence/sprint-31-physical-iphone-lan-dogfood/physical-iphone-checklist.md",
        "docs/sprint-31-closure-report.md",
    ]:
        text = read(rel)
        for phrase in ["physical iPhone", "SSO", "workspace/home"]:
            if phrase not in text:
                fail(f"{rel} missing {phrase}")
        assert_no_forbidden(rel)

    print("sprint31-lan-dogfood-check: ok")


if __name__ == "__main__":
    main()
