#!/usr/bin/env python3
"""Validate bounded cleanup and disk-headroom ordering for Live Stack E2E."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/live-stack-e2e.yml"
DOCS = ROOT / "docs/quality-and-evidence.md"
CLIENT_BRIDGE = (
    ROOT
    / "client/lib/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart"
)
CLIENT_MAKEFILE = ROOT / "client/Makefile"


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    docs = DOCS.read_text(encoding="utf-8")
    client_bridge = CLIENT_BRIDGE.read_text(encoding="utf-8")
    client_makefile = CLIENT_MAKEFILE.read_text(encoding="utf-8")

    ordered_steps = (
        "- name: Verify dedicated live runner",
        "- name: Remove stale runner-owned Weave outputs",
        "- name: Check out weave",
        "- name: Verify runner disk headroom",
        "- name: Expose generated local CA to Rust Matrix tests",
        "- name: Verify live test disk headroom and reserve recovery space",
        "- name: Run live stack integration tests",
        "- name: Generate live stack acceptance evidence",
        "- name: Generate support-safe failure diagnostics",
        "- name: Destroy stack and scrub stale resources",
        "- name: Upload live stack acceptance evidence",
        "- name: Scrub current runner-owned Weave outputs",
    )
    positions = [workflow.index(step) for step in ordered_steps]
    require(positions == sorted(positions), "live-stack cleanup/evidence steps are misordered")
    require(
        "minimum_kib=$((10 * 1024 * 1024))" in workflow,
        "live-stack preflight must require 10 GiB after stale-output cleanup",
    )
    require(
        workflow.count('"$checkout_root/client/.dart_tool"') == 2,
        "Flutter/Rust native outputs must be cleaned once before and once after the run",
    )
    require(
        workflow.count('weave-live-stack-docker-config') >= 3,
        "workflow-owned Docker auth must be namespaced and cleaned before and after the run",
    )

    finalizer = workflow[positions[-1] :]
    require("if: always()" in finalizer, "runner-output finalizer must run on failure")
    require(
        finalizer.index('"${WEAVE_ACCEPTANCE_EVIDENCE_DIR:-$RUNNER_TEMP/weave-live-stack-acceptance-evidence}"')
        < finalizer.index('docker image rm "$image_ref"'),
        "finalizer must remove evidence-local output before local image tags",
    )
    require(
        '"${WEAVE_ACCEPTANCE_TEST_LOG:-$RUNNER_TEMP/weave-live-stack-e2e.log}"'
        in finalizer,
        "finalizer must tolerate failure before evidence environment setup",
    )
    require(
        'if [[ "$image_ref" != ghcr.io/* ]]' in finalizer,
        "finalizer must preserve pulled registry images",
    )
    require(
        'openssl verify -CAfile "$CA_FILE" "$LEAF_FILE"'
        in workflow,
        "live-stack setup must verify the generated certificate chain",
    )
    require(
        'echo "WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH=$CA_FILE" >> "$GITHUB_ENV"'
        in workflow,
        "live-stack Rust TLS must receive the generated CA without mutating Keychains",
    )
    require(
        'echo "WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED=true" >> "$GITHUB_ENV"'
        in workflow,
        "live-stack workflow must explicitly enable the compile-time extra-root gate",
    )
    require(
        "const _matrixLiveTestExtraRootEnabled = bool.fromEnvironment(" in client_bridge,
        "Matrix extra-root loading must default off at compile time",
    )
    require(
        '\\"WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED\\": ' in client_makefile,
        "live tests must forward the compile-time extra-root gate as a dart-define",
    )
    require(
        "minimum_kib=$((4 * 1024 * 1024))" in workflow,
        "live-stack native-test preflight must require 4 GiB",
    )
    require(
        'mkfile 1g "$reserve"' in workflow,
        "live-stack tests must hold a 1 GiB emergency reserve",
    )
    require(
        "available_kib < 2 * 1024 * 1024" in workflow,
        "live-stack monitor must release the reserve before starving the runner",
    )
    require(
        "Live Stack E2E consumed its emergency disk reserve" in workflow,
        "live-stack tests must fail when emergency headroom is consumed",
    )
    require(
        "- name: Generate code" not in workflow,
        "live-stack behavior tests must not repeat root-CI generated-source work",
    )

    for forbidden in (
        "docker system prune",
        "docker volume prune",
        "docker builder prune",
        "xcrun simctl delete",
        "rm -rf -- \"$HOME",
        "rm -rf -- /Users",
        "security add-trusted-cert",
        "danger_accept_invalid_certs",
        "badCertificateCallback",
    ):
        require(forbidden not in workflow, f"live-stack cleanup is too broad: {forbidden}")

    for phrase in (
        "stale Weave-generated outputs",
        "10 GiB",
        "4 GiB",
        "1 GiB emergency reserve",
        "explicit extra root",
        "after acceptance evidence upload",
        "unrelated containers, volumes, signing identities, or physical-device data",
    ):
        require(phrase in docs, f"quality documentation is missing {phrase!r}")

    print("live-stack-runner-hygiene-check: ok")
    return 0


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    raise SystemExit(main())
