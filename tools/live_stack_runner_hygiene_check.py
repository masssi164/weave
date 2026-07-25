#!/usr/bin/env python3
"""Validate bounded cleanup and disk-headroom ordering for Live Stack E2E."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/live-stack-e2e.yml"
DOGFOOD_DEPLOY_WORKFLOW = ROOT / ".github/workflows/test-stack-deploy.yml"
DOGFOOD_MEMBER_WORKFLOW = ROOT / ".github/workflows/dogfood-member.yml"
DOGFOOD_RECOVERY_WORKFLOW = ROOT / ".github/workflows/dogfood-pending-identity-recovery.yml"
IOS_DOGFOOD_WORKFLOW = ROOT / ".github/workflows/ios-dogfood.yml"
PERSISTENT_RESOURCE_GUARD = ROOT / "tools/persistent_dogfood_resource_guard.sh"
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
IOS_SIMULATOR_XCRUN = ROOT / "tools/ios_simulator_xcrun.py"
IOS_SIMULATOR_XCRUN_SHIM = ROOT / "tools/ios-simulator-xcrun/xcrun"
LIVE_PHASE_OUTCOMES = ROOT / "tools/live_phase_outcomes.py"


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    dogfood_deploy_workflow = DOGFOOD_DEPLOY_WORKFLOW.read_text(encoding="utf-8")
    dogfood_member_workflow = DOGFOOD_MEMBER_WORKFLOW.read_text(encoding="utf-8")
    dogfood_recovery_workflow = DOGFOOD_RECOVERY_WORKFLOW.read_text(encoding="utf-8")
    ios_dogfood_workflow = IOS_DOGFOOD_WORKFLOW.read_text(encoding="utf-8")
    persistent_resource_guard = PERSISTENT_RESOURCE_GUARD.read_text(encoding="utf-8")
    docs = DOCS.read_text(encoding="utf-8")
    client_bridge = CLIENT_BRIDGE.read_text(encoding="utf-8")
    client_makefile = CLIENT_MAKEFILE.read_text(encoding="utf-8")
    client_live_tls = CLIENT_LIVE_TLS.read_text(encoding="utf-8")
    client_http_overrides = CLIENT_HTTP_OVERRIDES.read_text(encoding="utf-8")
    client_live_oidc = CLIENT_LIVE_OIDC.read_text(encoding="utf-8")
    ios_simulator_xcrun = IOS_SIMULATOR_XCRUN.read_text(encoding="utf-8")
    ios_simulator_xcrun_shim = IOS_SIMULATOR_XCRUN_SHIM.read_text(encoding="utf-8")
    live_phase_outcomes = LIVE_PHASE_OUTCOMES.read_text(encoding="utf-8")

    ordered_steps = (
        "- name: Verify run-scoped live runtime host",
        "- name: Remove stale runner-owned Weave outputs",
        "- name: Check out weave",
        "- name: Materialize run-scoped test environment",
        "- name: Verify runner disk headroom",
        "- name: Bind immutable images to source and lane evidence",
        "- name: Clean up stale stack state before bootstrap",
        "- name: Provision real Keycloak identities and verify runtime ReBAC",
        "- name: Prove missing-capability expired-token and revoked-session denials",
        "- name: Expose generated local CA to Rust Matrix tests",
        "- name: Boot an isolated iPhone Simulator and trust the local CA",
        "- name: Enable bounded iOS Simulator VM-service discovery",
        "- name: Verify live test disk headroom and reserve recovery space",
        "- name: Run live stack integration tests",
        "- name: Prove durable Matrix Synapse collaboration behind the Weave facade",
        "- name: Clean disposable identities and retain only hashed evidence",
        "- name: Generate live stack acceptance evidence",
        "- name: Generate support-safe failure diagnostics",
        "- name: Destroy stack and scrub stale resources",
        "- name: Verify persistent dogfood resources were preserved",
        "- name: Record independent live phase outcomes",
        "- name: Aggregate two-pass human-testing automation evidence",
        "- name: Upload live stack acceptance evidence",
        "- name: Scrub current runner-owned Weave outputs",
    )
    positions = [workflow.index(step) for step in ordered_steps]
    require(positions == sorted(positions), "live-stack cleanup/evidence steps are misordered")
    require(
        all(
            "group: weave-live-mac-mini-exclusive" in document
            for document in (
                workflow,
                dogfood_deploy_workflow,
                dogfood_member_workflow,
                ios_dogfood_workflow,
            )
        ),
        "all Mac runner mutators must share the non-cancelling exclusive lock",
    )
    require(
        "cancel-in-progress: false" in dogfood_deploy_workflow
        and "cancel-in-progress: false" in dogfood_member_workflow
        and "cancel-in-progress: false" in workflow,
        "Mac runner mutations must never cancel one another",
    )
    require(
        "runs-on: ubuntu-24.04" in dogfood_recovery_workflow
        and "- self-hosted" not in dogfood_recovery_workflow
        and "Pending-identity retirement is guarded." in dogfood_recovery_workflow,
        "guarded recovery must not consume or mutate the dedicated Mac runner",
    )
    require(
        "- weave-live" in workflow
        and "needs: isolation-gate" in workflow
        and "Fail closed without run-scoped runtime approval" in workflow,
        "destructive live-stack E2E must fail closed on the dedicated Mac runner",
    )
    require(
        'spec_commit="$(jq -er \''
        '.specCorpus.gitCommit | select(type == "string" and '
        'test("^[0-9a-f]{40}$"))\' specs/weave-specs.lock.json)"'
        in workflow
        and 'echo "commit=$spec_commit" >>"$GITHUB_OUTPUT"' in workflow
        and r'type == \"string\"' not in workflow,
        "the pinned specification resolver must fail closed before publishing one exact SHA",
    )
    require(
        "REQUESTED_SOURCE_REF: ${{ github.ref_name }}" in workflow
        and '"$REQUESTED_SOURCE_REF" != dev' in workflow
        and '"$REQUESTED_SOURCE_REF" != dogfood' in workflow
        and "feature-branch trees are PR evidence, not release-lane candidates"
        in workflow,
        "manual feature-branch dispatch must fail before it consumes the dedicated Mac runner",
    )
    require(
        "EXPECTED_RUNNER_NAME: weave-live-mac-mini" in workflow
        and '"${RUNNER_NAME:-}" != "${EXPECTED_RUNNER_NAME}"' in workflow,
        "live-stack E2E must explicitly require the single configured Mac runner",
    )
    require(
        "enable_run_scoped_e2e" in workflow
        and "enable_isolated_runner" not in workflow,
        "manual live-stack approval must describe run-scoped isolation",
    )
    require(
        "persistent_dogfood_resource_guard.sh" in workflow
        and "capture persistent dogfood resource baseline" in workflow.lower()
        and "verify persistent dogfood resources were preserved" in workflow.lower()
        and "persistent-dogfood-preservation" in workflow
        and "^weave[-_]" in persistent_resource_guard
        and "^weave[-_]e2e[-_]" in persistent_resource_guard
        and "cmp -s" in persistent_resource_guard,
        "single-runner E2E must prove persistent dogfood resource preservation",
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
        workflow.count('weave-live-stack-docker-config') >= 3
        and 'echo "WEAVE_DOCKER_AUTH_CONFIG=$WEAVE_DOCKER_AUTH_CONFIG"'
        in workflow
        and 'docker --config "${WEAVE_DOCKER_AUTH_CONFIG}" pull' in workflow
        and 'docker compose version >/dev/null' in workflow
        and "DOCKER_CONFIG=" not in workflow,
        "workflow-owned Docker auth must be namespaced without hiding host CLI plugins",
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
        'docker container inspect "${WEAVE_E2E_RUN_NAMESPACE}-db"' in workflow
        and 'docker network inspect "${WEAVE_E2E_RUN_NAMESPACE}_network"' in workflow
        and "ISOLATED_STACK_TEARDOWN status=not-required" in workflow,
        "stack teardown must tolerate a failure before exact Compose resources exist",
    )
    require(
        'case "${WEAVE_E2E_STARTUP_ENV_PATH:-}" in' in workflow
        and 'source "$WEAVE_E2E_STARTUP_ENV_PATH"' in workflow
        and workflow.rindex('source "$WEAVE_E2E_STARTUP_ENV_PATH"')
        < workflow.index(
            'WEAVE_TEARDOWN_EVIDENCE_FILE="$WEAVE_ACCEPTANCE_EVIDENCE_DIR/isolated-stack-teardown.json"'
        ),
        "Compose cleanup must reload and verify the exact run-scoped startup environment",
    )
    require(
        "TF_VAR_" not in workflow
        and "./compose.sh test up" in workflow
        and "./compose.sh test identity-verify" in workflow
        and "./compose.sh dogfood" not in workflow,
        "live-stack bootstrap must use the test-profile Compose and Identity Ops path",
    )
    require(
        "WEAVE_ENV_FILE: ${{ github.workspace }}/weave/infra/weave-workspace/environments/test.env.example"
        not in workflow
        and "- name: Materialize run-scoped test environment" in workflow
        and 'source_env="$PWD/environments/test.env.example"' in workflow
        and 'runtime_env="$runtime_root/reviewed-test.env"' in workflow
        and 'install -m 600 "$source_env" "$runtime_env"' in workflow
        and 'echo "WEAVE_ENV_FILE=$runtime_env" >> "$GITHUB_ENV"' in workflow
        and workflow.index("- name: Materialize run-scoped test environment")
        < workflow.index("- name: Prepare three disposable identity profiles")
        and workflow.index("- name: Bind immutable images to source and lane evidence")
        < workflow.index("- name: Clean up stale stack state before bootstrap")
        < workflow.index("- name: Bootstrap local stack")
        and "WEAVE_TEST_REVIEWED_ENV_FILE" not in workflow
        and "environment: dogfood" not in workflow
        and "environment: dogfood" in dogfood_deploy_workflow
        and "WEAVE_TEST_REVIEWED_ENV_FILE: ${{ vars.WEAVE_TEST_REVIEWED_ENV_FILE }}"
        in dogfood_deploy_workflow
        and "WEAVE_TEST_BACKUP_ROOT: ${{ vars.WEAVE_TEST_BACKUP_ROOT }}"
        in dogfood_deploy_workflow,
        "isolated E2E must not claim a protected deployment environment while persistent deployment requires it and its host paths",
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
    provider_proof_step = workflow[
        workflow.index(
            "- name: Prove durable Matrix Synapse collaboration behind the Weave facade"
        ) : workflow.index("- name: Clean disposable identities")
    ]
    require(
        "if: always()" in provider_proof_step
        and "WEAVE_CHAT_PROVIDER_PROOF_STATUS" in provider_proof_step
        and 'status:"unavailable"' in provider_proof_step
        and 'status:"failed"' in provider_proof_step,
        "Matrix/Synapse provider proof must run independently and retain support-safe failure evidence",
    )
    require(
        "live-phase-outcomes-v2" in live_phase_outcomes
        and "weave/tools/live_phase_outcomes.py" in workflow
        and "provider-persistence-exactly-once" in workflow
        and "identity-cleanup" in workflow
        and "WEAVE_COLLABORATION_TEST_STATUS" in workflow
        and "WEAVE_CALENDAR_CONTAINMENT_STATUS" in workflow
        and "WEAVE_STACK_TEARDOWN_STEP_OUTCOME" in workflow,
        "live-stack evidence must record independent functional, provider, recovery, cleanup, and teardown outcomes",
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
    require(
        'vm_service_shim="$GITHUB_WORKSPACE/weave/tools/ios-simulator-xcrun"'
        in workflow
        and 'test -x "$vm_service_shim/xcrun"' in workflow
        and 'echo "$vm_service_shim" >> "$GITHUB_PATH"' in workflow,
        "live collaboration must install the bounded xcrun compatibility shim only after simulator setup",
    )
    require(
        IOS_SIMULATOR_XCRUN_SHIM.stat().st_mode & 0o111 != 0
        and "ios_simulator_xcrun.py" in ios_simulator_xcrun_shim,
        "the xcrun compatibility entrypoint must be executable and delegate to the reviewed helper",
    )
    for marker in (
        "processIdentifier == {process_id}",
        'parsed.hostname != "127.0.0.1"',
        '"--start"',
        "simulator_runner_pids(snapshot(), simulator_udid)",
        "WEAVE_IOS_VM_SERVICE_DISCOVERY_ERROR",
        "supportSafe=true",
    ):
        require(
            marker in ios_simulator_xcrun,
            f"bounded simulator VM-service replay is missing {marker!r}",
        )
    for forbidden in (
        "write_text(",
        "write_bytes(",
        "mkstemp(",
        "NamedTemporaryFile(",
    ):
        require(
            forbidden not in ios_simulator_xcrun,
            "simulator VM-service replay must not persist the private service URI",
        )

    finalizer = workflow[positions[-1] :]
    require("if: always()" in finalizer, "runner-output finalizer must run on failure")
    require(
        finalizer.index('"${WEAVE_ACCEPTANCE_EVIDENCE_DIR:-$RUNNER_TEMP/weave-live-stack-acceptance-evidence}"')
        < finalizer.index('if [[ "$WEAVE_LIVE_JOB_STATUS" == success ]]'),
        "finalizer must remove evidence-local output before deciding candidate image retention",
    )
    require(
        '"${WEAVE_ACCEPTANCE_TEST_LOG:-$RUNNER_TEMP/weave-live-stack-e2e.log}"'
        in finalizer,
        "finalizer must tolerate failure before evidence environment setup",
    )
    require(
        "WEAVE_LIVE_JOB_STATUS: ${{ job.status }}" in finalizer
        and "Preserving the attested immutable image set for the locked downstream dogfood deployment."
        in finalizer
        and '"${WEAVE_IDENTITY_OPS_IMAGE:-}"' in finalizer
        and 'docker image rm "$image_ref"' in finalizer,
        "finalizer must retain successful candidate images for dogfood and clean failed partial sets",
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
        "Flutter VM-service discovery is bounded",
        "URI remains in memory",
    ):
        require(phrase in docs, f"quality documentation is missing {phrase!r}")

    print("live-stack-runner-hygiene-check: ok")
    return 0


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


if __name__ == "__main__":
    raise SystemExit(main())
