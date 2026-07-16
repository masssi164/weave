#!/usr/bin/env python3
"""Validate bounded cleanup and disk-headroom ordering for Live Stack E2E."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/live-stack-e2e.yml"
DOGFOOD_DEPLOY_WORKFLOW = ROOT / ".github/workflows/test-stack-deploy.yml"
DOGFOOD_MEMBER_WORKFLOW = ROOT / ".github/workflows/dogfood-member.yml"
DOCS = ROOT / "docs/quality-and-evidence.md"
CLIENT_BRIDGE = (
    ROOT
    / "client/lib/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart"
)
CLIENT_MAKEFILE = ROOT / "client/Makefile"
CLIENT_LIVE_TLS = ROOT / "client/integration_test/helpers/live_test_tls.dart"
CLIENT_HTTP_OVERRIDES = (
    ROOT / "client/integration_test/helpers/test_http_overrides.dart"
)
CLIENT_LIVE_OIDC = ROOT / "client/integration_test/helpers/live_oidc_test_driver.dart"


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    dogfood_deploy_workflow = DOGFOOD_DEPLOY_WORKFLOW.read_text(encoding="utf-8")
    dogfood_member_workflow = DOGFOOD_MEMBER_WORKFLOW.read_text(encoding="utf-8")
    docs = DOCS.read_text(encoding="utf-8")
    client_bridge = CLIENT_BRIDGE.read_text(encoding="utf-8")
    client_makefile = CLIENT_MAKEFILE.read_text(encoding="utf-8")
    client_live_tls = CLIENT_LIVE_TLS.read_text(encoding="utf-8")
    client_http_overrides = CLIENT_HTTP_OVERRIDES.read_text(encoding="utf-8")
    client_live_oidc = CLIENT_LIVE_OIDC.read_text(encoding="utf-8")

    ordered_steps = (
        "- name: Verify isolated disposable live runner",
        "- name: Remove stale runner-owned Weave outputs",
        "- name: Check out weave",
        "- name: Verify runner disk headroom",
        "- name: Provision real Keycloak identities and verify runtime ReBAC",
        "- name: Prove missing-capability expired-token and revoked-session denials",
        "- name: Expose generated local CA to Rust Matrix tests",
        "- name: Boot an isolated iPhone Simulator and trust the local CA",
        "- name: Verify live test disk headroom and reserve recovery space",
        "- name: Run live stack integration tests",
        "- name: Clean disposable identities and retain only hashed evidence",
        "- name: Generate live stack acceptance evidence",
        "- name: Generate support-safe failure diagnostics",
        "- name: Destroy stack and scrub stale resources",
        "- name: Aggregate two-pass human-testing automation evidence",
        "- name: Upload live stack acceptance evidence",
        "- name: Scrub current runner-owned Weave outputs",
    )
    positions = [workflow.index(step) for step in ordered_steps]
    require(positions == sorted(positions), "live-stack cleanup/evidence steps are misordered")
    require(
        "group: weave-persistent-dogfood" in dogfood_deploy_workflow
        and "group: weave-persistent-dogfood" in dogfood_member_workflow,
        "all persistent dogfood mutators must share the non-cancelling deployment lock",
    )
    require(
        "cancel-in-progress: false" in dogfood_deploy_workflow
        and "cancel-in-progress: false" in dogfood_member_workflow,
        "persistent dogfood operations must never cancel one another",
    )
    require(
        "- weave-disposable-live-stack" in workflow
        and "needs: isolation-gate" in workflow
        and "Fail closed without isolated runtime approval" in workflow,
        "destructive live-stack E2E must fail closed and target only an isolated runner",
    )
    require(
        "PERSISTENT_DOGFOOD_RUNNER_NAME: weave-live-mac-mini" in workflow
        and '"${RUNNER_NAME}" == "${PERSISTENT_DOGFOOD_RUNNER_NAME}"' in workflow,
        "destructive live-stack E2E must explicitly reject the persistent dogfood runner",
    )
    require(
        "group: weave-persistent-dogfood" not in workflow,
        "disposable E2E must not claim the persistent dogfood mutation lock",
    )
    require(
        "minimum_kib=$((10 * 1024 * 1024))" in workflow,
        "live-stack preflight must require 10 GiB after stale-output cleanup",
    )
    require(
        workflow.count('flutter_tool_cache="${runner_tool_cache%/}/flutter"') == 1
        and workflow.count('rm -rf -- "$flutter_tool_cache"') == 1,
        "low-headroom recovery must target only the restorable Flutter tool cache",
    )
    require(
        'if [ -z "$runner_tool_cache" ] || [ "$runner_tool_cache" = "/" ]'
        in workflow,
        "runner tool-cache cleanup must reject empty and root paths",
    )
    require(
        '[[ "$runner_tool_cache" != /* ]]' in workflow,
        "runner tool-cache cleanup must reject relative paths",
    )
    require(
        'if (( available_kib < minimum_kib )) && [ -d "$flutter_tool_cache" ]'
        in workflow,
        "the Flutter tool cache must be reclaimed only below the 10 GiB preflight",
    )
    require(
        workflow.count('"$checkout_root/client/.dart_tool"') == 2,
        "Flutter/Rust native outputs must be cleaned once before and once after the run",
    )
    require(
        workflow.count('weave-live-stack-docker-config') >= 3,
        "workflow-owned Docker auth must be namespaced and cleaned before and after the run",
    )
    require(
        "isolated-e2e-identities.sh prepare" in workflow
        and "isolated-e2e-identities.sh provision" in workflow
        and "isolated-e2e-identities.sh cleanup" in workflow,
        "live-stack E2E must own the full three-identity lifecycle",
    )
    require(
        "clean :server:bootJar :weave-mcp-server:bootJar" in workflow,
        "live-stack Java runtime artifacts must be compiled from a clean tree",
    )
    require(
        'docker container inspect "$keycloak_container"' in workflow
        and "ISOLATED_E2E_IDENTITIES state=cleanup-not-required" in workflow,
        "identity cleanup must tolerate a failure before provider runtime creation",
    )
    require(
        'runtime_root="${WEAVE_E2E_OUTPUT_ROOT:?}/${TF_VAR_isolated_e2e_namespace}/runtime"'
        in workflow
        and "ISOLATED_STACK_TEARDOWN status=not-required" in workflow,
        "stack teardown must tolerate a failure before OpenTofu runtime creation",
    )
    require(
        'expected_bootstrap_env="$PWD/.generated/isolated/${TF_VAR_isolated_e2e_namespace}/bootstrap.env"'
        in workflow
        and 'source "$expected_bootstrap_env"' in workflow
        and workflow.index('source "$expected_bootstrap_env"')
        < workflow.index(
            'WEAVE_TEARDOWN_EVIDENCE_FILE="$WEAVE_ACCEPTANCE_EVIDENCE_DIR/isolated-stack-teardown.json"'
        ),
        "OpenTofu destroy must reload the exact run-scoped variables generated during apply",
    )
    require(
        'export WEAVE_BOOTSTRAP_ENV="${WEAVE_E2E_STACK_BOOTSTRAP_ENV:?}"'
        in workflow
        and "/weave-workspace/.generated/bootstrap.env" not in workflow,
        "live-stack clients must consume only the run-scoped isolated bootstrap env",
    )
    require(
        "integration-multi-user-e2e" in workflow
        and "multi_user_e2e_evidence.py" in workflow
        and "isolated-e2e-authorization-probes.sh" in workflow
        and "--authorization-evidence" in workflow
        and "isolated-authorization.json" in workflow
        and "--require-passed" in workflow,
        "live-stack E2E must run and fail closed on two-pass collaboration and real authorization evidence",
    )
    require(
        workflow.count("capture_matrix_to_device_snapshot") == 3
        and "isolated-matrix-to-device-before-collaboration.json" in workflow
        and "isolated-matrix-to-device-after-collaboration.json" in workflow
        and "python3 ../tools/validate_matrix_to_device_evidence.py" in workflow
        and '"$matrix_before_collaboration_status"' in workflow
        and '"$matrix_after_collaboration_status"' in workflow,
        "three-user Matrix diagnostics must use validated support-safe boundary snapshots and fail closed",
    )
    matrix_single_user = workflow.index("single_user_status=${PIPESTATUS[0]}")
    matrix_before = workflow.index(
        "isolated-matrix-to-device-before-collaboration.json"
    )
    matrix_collaboration = workflow.index("WEAVE_E2E_EXECUTION_MODE=collaboration")
    matrix_after = workflow.index(
        "isolated-matrix-to-device-after-collaboration.json"
    )
    require(
        matrix_single_user < matrix_before < matrix_collaboration < matrix_after,
        "Matrix to-device snapshots must bound only the three-user collaboration phase",
    )
    require(
        "WEAVE_E2E_EXECUTION_MODE=collaboration" in workflow
        and "isolated-e2e-calendar-outage.sh begin" in workflow
        and "WEAVE_E2E_EXECUTION_MODE=calendar-failure-containment" in workflow
        and "isolated-e2e-calendar-outage.sh restore" in workflow
        and "--calendar-outage-evidence" in workflow
        and "isolated-calendar-outage.json" in workflow,
        "live-stack E2E must prove a real isolated Calendar outage and restored cached health",
    )
    calendar_begin = workflow.index("calendar_outage_begin_status=${PIPESTATUS[0]}")
    calendar_containment = workflow.index(
        "WEAVE_E2E_EXECUTION_MODE=calendar-failure-containment"
    )
    calendar_restore = workflow.index("if restore_calendar_outage; then")
    identity_cleanup = workflow.index("- name: Clean disposable identities")
    require(
        calendar_begin < calendar_containment < calendar_restore < identity_cleanup,
        "Calendar outage, containment, restoration, and identity cleanup are misordered",
    )
    identity_cleanup_section = workflow[
        identity_cleanup : workflow.index(
            "- name: Aggregate two-pass human-testing automation evidence"
        )
    ]
    require(
        identity_cleanup_section.index("isolated-e2e-calendar-outage.sh restore")
        < identity_cleanup_section.index("isolated-e2e-identities.sh cleanup")
        and '.state == "restored"' in identity_cleanup_section
        and ".recoveryRequired == false" in identity_cleanup_section
        and ".cachedHealth == {calendarStatus:2,filesStatus:2}"
        in identity_cleanup_section,
        "identity cleanup must fail closed until the isolated Calendar fixture is restored",
    )
    require(
        'calendar_outage_state="${WEAVE_E2E_OUTPUT_ROOT:?}/${WEAVE_E2E_RUN_NAMESPACE:?}/calendar-outage-state.json"'
        in workflow
        and "trap finalize_tests EXIT" in workflow
        and 'if [ "$calendar_outage_restored" != true ]' in workflow,
        "Calendar recovery must remain namespace-scoped and run on interruption or failure",
    )
    require(
        all(
            f'echo "{name}=true"' in workflow
            for name in (
                "WEAVE_E2E_MISSING_CAPABILITY_VERIFIED",
                "WEAVE_E2E_EXPIRED_TOKEN_VERIFIED",
                "WEAVE_E2E_REVOKED_SESSION_VERIFIED",
            )
        ),
        "client authorization flags must be set only after the real isolated probes pass",
    )
    require(
        "xcrun simctl bootstatus" in workflow
        and "xcrun simctl keychain" in workflow
        and "WEAVE_MULTI_USER_TEST_DEVICE" in workflow,
        "functional collaboration must run on a booted iPhone Simulator with the local CA",
    )
    require(
        'xcrun simctl create "$simulator_name" "$device_type" "$runtime_id"'
        in workflow
        and 'echo "WEAVE_E2E_SIMULATOR_UDID=$simulator_udid"' in workflow
        and 'xcrun simctl delete "$WEAVE_E2E_SIMULATOR_UDID"' in workflow
        and "xcrun simctl list devices available" not in workflow,
        "live collaboration must create and delete its own simulator instead of reusing app/Keychain state",
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
        "const _matrixLiveTestExtraRootBase64 = String.fromEnvironment("
        in client_bridge,
        "Matrix extra-root material must use a compile-time-only payload",
    )
    require(
        "Platform.environment" not in client_bridge
        and "File(path)" not in client_bridge,
        "sandboxed Matrix clients must not read runner paths at runtime",
    )
    require(
        "SecurityContext(withTrustedRoots: true)" in client_live_tls
        and "setTrustedCertificatesBytes" in client_live_tls
        and "maximumLiveTestRootPemBytes = 64 * 1024" in client_live_tls,
        "Dart live clients must add only the bounded generated CA while retaining platform roots",
    )
    require(
        "badCertificateCallback" not in client_live_tls
        and "badCertificateCallback" not in client_http_overrides
        and "badCertificateCallback" not in client_live_oidc,
        "live OIDC/HTTP helpers must preserve certificate-chain and hostname validation",
    )
    require(
        client_makefile.count(
            "--arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED"
        )
        == 3,
        "all live test define files must forward the compile-time root gate",
    )
    require(
        client_makefile.count("WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64") == 3,
        "all live test define files must carry the sandbox-safe Matrix root",
    )
    require(
        client_makefile.count("jq -n") == 3,
        "live test dart-define files must use structured JSON generation",
    )
    require(
        client_makefile.count('base64 < "$$matrix_extra_root_path"') == 3,
        "live tests must encode the generated root without logging its contents",
    )
    require(
        "minimum_kib=$((5 * 1024 * 1024))" in workflow,
        "live-stack native-test preflight must preserve 4 GiB plus the reserve",
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
        "rm -rf -- \"$HOME",
        "rm -rf -- /Users",
        "security add-trusted-cert",
        "danger_accept_invalid_certs",
        "badCertificateCallback",
    ):
        require(forbidden not in workflow, f"live-stack cleanup is too broad: {forbidden}")

    for phrase in (
        "stale Weave-generated outputs",
        "restorable runner-owned Flutter tool cache",
        "10 GiB",
        "5 GiB",
        "4 GiB",
        "1 GiB runner-owned emergency reserve",
        "explicit extra root",
        "after acceptance evidence upload",
        "unrelated simulators, containers, volumes, signing identities, or physical-device data",
        "controlled Calendar outage",
        "restored before disposable identity cleanup",
    ):
        require(phrase in docs, f"quality documentation is missing {phrase!r}")

    print("live-stack-runner-hygiene-check: ok")
    return 0


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    raise SystemExit(main())
