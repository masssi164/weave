#!/usr/bin/env python3
"""Validate the ordered, fail-closed enterprise dogfood candidate pipeline."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    value = ROOT / path
    if not value.is_file():
        raise SystemExit(f"candidate-pipeline-check: missing {path}")
    return value.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"candidate-pipeline-check: {message}")


def ordered(document: str, fragments: tuple[str, ...], label: str) -> None:
    positions = []
    for fragment in fragments:
        require(fragment in document, f"{label} is missing {fragment!r}")
        positions.append(document.index(fragment))
    require(positions == sorted(positions), f"{label} stages are not ordered")


def require_source_candidate_binding(
    document: str,
    evidence_directory: str,
    label: str,
    *,
    builds_images: bool,
) -> None:
    require(
        "fetch-depth: 0" in document
        and "'+refs/heads/dev:refs/remotes/origin/dev'" in document
        and "tools/candidate_source_mapping.py" in document
        and '--lane-candidate "$WEAVE_CANDIDATE_COMMIT"' in document
        and '--github-env "$GITHUB_ENV"' in document,
        f"{label} does not derive its image source from protected dev ancestry",
    )
    require(
        f'--output "{evidence_directory}/candidate-source-mapping.json"' in document,
        f"{label} does not emit source/lane image evidence",
    )
    require(
        "WEAVE_IMAGE_SOURCE_COMMIT:" not in document,
        f"{label} accepts a caller-supplied image source authority",
    )
    if builds_images:
        require(
            document.count(
                'org.opencontainers.image.revision=$WEAVE_IMAGE_SOURCE_COMMIT'
            )
            == 2
            and '--candidate-commit "$WEAVE_IMAGE_SOURCE_COMMIT"' in document
            and "scripts/build_identity_ops_image.py" in document
            and 'org.opencontainers.image.revision=$WEAVE_CANDIDATE_COMMIT'
            not in document
            and 'org.opencontainers.image.revision=$CANDIDATE_SHA' not in document,
            f"{label} does not bind all locally built images to the protected source candidate",
        )
        require(
            all(
                fragment in document
                for fragment in (
                    '--image "backend=$WEAVE_BACKEND_IMAGE"',
                    '--image "mcp=$WEAVE_MCP_IMAGE"',
                    '--image "keycloak=$WEAVE_KEYCLOAK_IMAGE"',
                    '--image "identity-ops=$WEAVE_IDENTITY_OPS_IMAGE"',
                )
            ),
            f"{label} does not emit a closed immutable source/lane image mapping",
        )
    else:
        require(
            'gh run download "$ISOLATED_E2E_RUN_ID"' in document
            and "--name weave-live-stack-acceptance-evidence" in document
            and '--expected-mapping "$expected_mapping"' in document
            and "--verify-local-images" in document
            and "docker buildx build" not in document
            and "weave_backend_image:" not in document
            and "weave_mcp_server_image:" not in document,
            f"{label} rebuilds or accepts substitutions instead of consuming isolated image evidence",
        )


def main() -> int:
    ci = read(".github/workflows/ci.yml")
    live = read(".github/workflows/live-stack-e2e.yml")
    deployment = read(".github/workflows/test-stack-deploy.yml")
    recovery = read(".github/workflows/dogfood-pending-identity-recovery.yml")
    ios = read(".github/workflows/ios-dogfood.yml")
    physical = read(".github/workflows/human-testing-readiness.yml")
    promotion = read(".github/workflows/main-promotion-gate.yml")
    docs = read("docs/ios-dogfood-distribution.md")
    readiness_assembler = read("tools/human_testing_readiness_assemble.py")

    require(
        "push:\n    branches:\n      - dev\n      - dogfood\n      - main" in ci,
        "root CI must run after protected-lane pushes without duplicating feature-branch PR runs",
    )
    require(
        "pull_request:" in ci and "merge_group:" in ci and "workflow_dispatch:" in ci,
        "root CI is missing a PR, merge-queue, or manual execution path",
    )
    require(
        "group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}-"
        in ci
        and "github.event.action == 'labeled'" in ci
        and "github.event.action == 'unlabeled'" in ci
        and "cancel-in-progress: ${{ github.event_name == 'pull_request'"
        in ci,
        "root CI does not isolate full and label-only concurrency or cancel superseded PR computation",
    )

    require("push:\n    branches:\n      - dogfood" in live, "isolated E2E does not run on the exact dogfood commit")
    require(
        "repository: ${{ github.repository_owner }}/weave-specs" in live
        and "ref: ${{ steps.spec-corpus.outputs.commit }}" in live
        and "path: weave-specs" in live,
        "isolated E2E does not consume the exact pinned specification corpus",
    )
    ordered(
        live,
        (
            "isolated-e2e-identities.sh prepare",
            "isolated-e2e-identities.sh provision",
            "isolated-e2e-authorization-probes.sh",
            "WEAVE_E2E_EXECUTION_MODE=collaboration",
            "calendar_outage_begin_status=${PIPESTATUS[0]}",
            "WEAVE_E2E_EXECUTION_MODE=calendar-failure-containment",
            "if restore_calendar_outage; then",
            "isolated-e2e-identities.sh cleanup",
            "multi_user_e2e_evidence.py",
        ),
        "isolated live E2E",
    )
    require("xcrun simctl bootstatus" in live, "functional collaboration is not bound to an iPhone Simulator")
    require(
        "--authorization-evidence" in live
        and "isolated-authorization.json" in live
        and "--require-passed" in live,
        "isolated collaboration and authorization evidence is not fail closed",
    )
    require(
        "isolated-e2e-calendar-outage.sh begin" in live
        and "isolated-e2e-calendar-outage.sh restore" in live
        and "--calendar-outage-evidence" in live
        and "isolated-calendar-outage.json" in live
        and "trap finalize_tests EXIT" in live,
        "real Calendar failure containment is not restored and bound to evidence",
    )
    require(
        "Resolve the approved stock Keycloak image" in live
        and "scripts/build_keycloak_image.py" in live
        and "stock-keycloak-image.json" in live
        and 'echo "WEAVE_BACKEND_IMAGE=$image_id"' in live
        and 'echo "WEAVE_MCP_IMAGE=$image_id"' in live
        and 'echo "WEAVE_KEYCLOAK_IMAGE=$image_id"' in live
        and '@sha256:[0-9a-f]{64}$' in live,
        "isolated E2E images are not immutable or do not resolve approved stock Keycloak",
    )
    require(
        "Build the exact-candidate Identity Ops image" in live
        and '--candidate-commit "$WEAVE_IMAGE_SOURCE_COMMIT"' in live
        and 'echo "WEAVE_IDENTITY_OPS_IMAGE=$image_id"' in live,
        "isolated E2E does not bind Identity Ops to the source candidate",
    )
    require_source_candidate_binding(
        live,
        "$WEAVE_ACCEPTANCE_EVIDENCE_DIR",
        "isolated E2E",
        builds_images=True,
    )

    require("workflow_run:" in deployment and "- Live Stack E2E" in deployment, "persistent deployment is not downstream of isolated E2E")
    require("ref: ${{ env.CANDIDATE_SHA }}" in deployment, "persistent deployment does not check out exact evidence candidate")
    require(
        "repository: ${{ github.repository_owner }}/weave-specs" in deployment
        and "ref: ${{ steps.spec-corpus.outputs.commit }}" in deployment
        and "WEAVE_SPEC_CORPUS_ROOT: ${{ github.workspace }}/canonical-weave-specs" in deployment,
        "persistent deployment does not bind rendering to the exact pinned specification corpus",
    )
    require(
        'git merge-base --is-ancestor "$CANDIDATE_SHA" origin/dogfood' in deployment,
        "manual persistent deployment can select a commit outside dogfood",
    )
    require("- weave-live" in deployment, "persistent deployment is not pinned to the dedicated live runner label")
    require("TF_VAR_" not in deployment, "persistent dogfood retains the retired infrastructure-variable channel")
    require(
        "WEAVE_ENVIRONMENT: test" in deployment
        and "WEAVE_STACK_SCOPE: persistent" in deployment
        and "WEAVE_TEST_REVIEWED_ENV_FILE:" in deployment,
        "persistent deployment is not bound to the reviewed test runtime profile and persistent scope",
    )
    require(
        "WEAVE_CANDIDATE_COMMIT: ${{ github.event.workflow_run.head_sha || inputs.candidate_sha }}"
        in deployment
        and "--verify-local-images" in deployment,
        "persistent dogfood does not preserve lane evidence and exact isolated image provenance",
    )
    require_source_candidate_binding(
        deployment,
        "$WEAVE_TEST_STACK_EVIDENCE_DIR",
        "persistent dogfood",
        builds_images=False,
    )
    require(
        "Dogfood Pending Identity Recovery (Guarded)" in recovery
        and "acknowledge-recovery-is-guarded" in recovery
        and "Pending-identity retirement is guarded." in recovery
        and "Use normal invitation resend and authenticated session reconciliation" in recovery
        and "exit 1" in recovery
        and "keycloak-sanitizer" not in recovery
        and "WEAVE_KEYCLOAK_SUPERVISOR" not in recovery,
        "pending-identity recovery does not fail closed after retiring its old authority",
    )
    require("WEAVE_TEST_PASSWORD" not in deployment, "persistent dogfood carries obsolete test-user credentials")
    persistent_credential_names = (
        "WEAVE_DB_ADMIN_PASSWORD",
        "WEAVE_BACKEND_DB_PASSWORD",
        "WEAVE_MCP_CLIENT_SECRET",
        "WEAVE_KEYCLOAK_ADMIN_PASSWORD",
        "WEAVE_KEYCLOAK_DB_PASSWORD",
        "WEAVE_MAS_DB_PASSWORD",
        "WEAVE_SYNAPSE_DB_PASSWORD",
        "WEAVE_NEXTCLOUD_DB_PASSWORD",
        "WEAVE_NEXTCLOUD_ADMIN_PASSWORD",
        "WEAVE_NEXTCLOUD_ACTOR_TOKEN",
        "WEAVE_MATRIX_MAS_CLIENT_SECRET",
        "WEAVE_IDENTITY_ADMIN_CLIENT_SECRET",
        "WEAVE_IDENTITY_EVENTS_HMAC_SECRET",
        "WEAVE_MAS_ENCRYPTION_SECRET",
        "WEAVE_MAS_SIGNING_KEY",
        "WEAVE_MAS_MATRIX_SECRET",
        "WEAVE_MATRIX_APPSERVICE_AS_TOKEN",
        "WEAVE_MATRIX_APPSERVICE_HS_TOKEN",
        "WEAVE_SYNAPSE_REGISTRATION_SHARED_SECRET",
        "WEAVE_SYNAPSE_MACAROON_SECRET_KEY",
        "WEAVE_SYNAPSE_FORM_SECRET",
    )
    require(
        all(f"{name}:" not in deployment for name in persistent_credential_names),
        "routine persistent deployment overrides restored credential authority",
    )
    require(
        "bash ./smoke-test.sh" not in deployment
        and deployment.count("./operator-check.sh test") == 2,
        "persistent dogfood must use non-destructive operator checks without the automation-user smoke suite",
    )
    require(
        deployment.count("./compose.sh test up") == 2
        and deployment.count("./compose.sh test identity-verify") == 2
        and deployment.count("./compose.sh test identity-plan") == 1,
        "persistent candidate must apply twice and prove one explicit empty second Identity Ops plan",
    )
    ordered(
        deployment,
        (
            "Apply and verify the persistent test profile",
            "Prove repeatability and an empty second identity plan",
            "Generate support-safe diagnostics",
            "Upload persistent test evidence",
        ),
        "persistent test-profile deployment",
    )
    require(
        "composeModelStable:true" in deployment
        and "identitySecondPlanEmpty:true" in deployment
        and "persistent-test-idempotence.json" in deployment,
        "persistent test deployment does not emit stable model and empty-plan evidence",
    )

    require("workflow_run:" in ios and "- Test Stack Deploy" in ios, "iOS distribution is not downstream of deployment")
    require('gh run download "$deployment_run_id" --name weave-test-stack-evidence' in ios, "iOS candidate is not read from deployment evidence")
    require("name: ios-dogfood" in ios and "cancel-in-progress: true" in ios, "iOS environment/supersession policy is incomplete")
    require("WEAVE_CANDIDATE_COMMIT=${CANDIDATE_SHA}" in ios, "iOS build does not embed its candidate")
    require(
        "stable-signing-fallback:" in ios
        and "inputs.upload_to_testflight == false" in ios
        and "tools/dogfood_ios_development_fallback.sh" in ios
        and "ios-dogfood-distribution.json" in ios,
        "documented stable-signing fallback does not emit protected canonical distribution evidence",
    )
    fallback = ios.split("  stable-signing-fallback:", 1)[1]
    require("- weave-live" in fallback, "physical fallback is not pinned to the dedicated live runner label")

    for value in (
        "installed_commit:",
        "voiceover_passed:",
        "session_upgrade_passed:",
        "navigation_and_interactions_passed:",
        "name: ios-dogfood",
        "--require-ready",
    ):
        require(value in physical, f"physical readiness workflow is missing {value!r}")
    require(
        "PHYSICAL_IPHONE_VOICEOVER_RESULT" in readiness_assembler
        and "HUMAN_TESTING_READINESS_RESULT" in readiness_assembler,
        "readiness assembler does not emit stable physical/final markers",
    )
    require("human_testing_readiness_manifest.py validate" in promotion and "--require-ready" in promotion, "main promotion does not require a ready exact-candidate manifest")
    require(
        all(value in promotion for value in (
            "test-stack-deploy.yml|dogfood|Weave release owner or dogfood operator",
            "ios-dogfood.yml|ios-dogfood|Weave release owner plus client/iOS release owner",
            "human-testing readiness is blocked workflow=",
            "environment=${environment}",
            "run=${blocking_run_url}",
            "commit=${candidate}",
            "requiredApprover=${approver}",
        )),
        "readiness blockers do not name the exact workflow environment run commit and approver",
    )
    require("approval request expires after 24 hours" in docs, "protected-environment approval expiry is undocumented")

    print("CANDIDATE_PIPELINE_RESULT status=passed ordered=true supportSafe=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
