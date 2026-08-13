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


def main() -> int:
    candidate = read(".github/workflows/candidate-images.yml")
    live = read(".github/workflows/live-stack-e2e.yml")
    deployment = read(".github/workflows/test-stack-deploy.yml")
    owner_bootstrap = read(".github/workflows/dogfood-owner-bootstrap.yml")
    ios = read(".github/workflows/ios-dogfood.yml")
    physical = read(".github/workflows/physical-iphone-human-test.yml")
    readiness = read(".github/workflows/human-testing-readiness.yml")
    promotion = read(".github/workflows/main-promotion-gate.yml")
    docs = read("docs/ios-dogfood-distribution.md")
    readiness_assembler = read("tools/human_testing_readiness_assemble.py")
    run_resolver = read("tools/candidate_cut_run_resolver.py")
    capacity_preflight = read("tools/runner_capacity_preflight.py")
    test_app = read("gradle/tasks/test-app.sh")

    require(
        "on:\n  workflow_dispatch:\n    inputs:\n      candidate_sha:" in candidate
        and "\n  push:\n" not in candidate,
        "Candidate Cut is not dispatch-only with one exact candidate_sha input",
    )
    require(
        "run-name: Candidate Cut ${{ inputs.candidate_sha }}" in candidate
        and "group: candidate-cut-${{ inputs.candidate_sha }}" in candidate,
        "Candidate Cut run identity and concurrency are not selected-SHA keyed",
    )
    ordered(
        candidate,
        (
            "verify-source:",
            "Verify selected candidate belongs to protected dev",
            '[[ "$GITHUB_REF" == "refs/heads/dev" ]]',
            '[[ "$WEAVE_CANDIDATE_COMMIT" =~ ^[0-9a-f]{40}$ ]]',
            'git merge-base --is-ancestor',
            "build-candidate:",
            "environment: candidate-cut",
            "packages: write",
            "fresh-product-proof:",
        ),
        "protected Candidate Cut",
    )
    require(
        "github.sha" not in candidate
        and "GITHUB_SHA" not in candidate
        and candidate.count("needs.verify-source.outputs.candidate_sha") >= 8,
        "candidate artifacts still derive identity from ambient workflow SHA",
    )
    require(
        "provenance: mode=max" in candidate
        and "sbom: true" in candidate
        and "candidate-manifest-${{ needs.verify-source.outputs.candidate_sha }}" in candidate,
        "Candidate Cut weakened immutable manifest, SBOM, or provenance binding",
    )
    require(
        "Resolve canonical realm definition identities" in candidate
        and ".provenance.baselineRevision" in candidate
        and "infra/weave-workspace/keycloak/migration-definition.json" in candidate
        and "semanticRealmSourceDigest" in candidate
        and "migrationDefinitionDigest" in candidate
        and "weave-realm.json" not in candidate.split("Upload candidate evidence", 1)[1].split("Summarize immutable deployment inputs", 1)[0],
        "Candidate Cut does not separate semantic realm identity from environment render bytes",
    )

    require("push:\n    branches: [dogfood]" in live, "isolated product flow does not run on the exact dogfood commit")
    require(
        "name: Invitation, OIDC, Weave-native collaboration, ARC, and MCP" in live
        and "Invitation, OIDC, WebDAV, ARC, and MCP" not in live,
        "Live Stack job name does not match the protected dogfood check context",
    )
    ordered(
        live,
        (
            "Capture persistent dogfood resources",
            "Run the manifest-bound Fresh product proof",
            "./gradlew --no-daemon testApp",
            "Ensure exact isolated Fresh namespace is absent",
            "Verify product proof and persistent dogfood preservation",
            "Upload support-safe live-stack evidence",
        ),
        "isolated Fresh product flow",
    )
    require(
        "timeout-minutes: 75" in live
        and "Run the manifest-bound Fresh product proof\n        timeout-minutes: 60" in live
        and "Ensure exact isolated Fresh namespace is absent\n        if: always()\n        timeout-minutes: 10" in live,
        "isolated Fresh proof does not reserve its exact cleanup window",
    )
    require(
        ".credentialsIncluded == false" in live
        and ".actionLinksIncluded == false" in live
        and '.mcpTool == "files.search"' in live,
        "Fresh product-flow evidence is not fail closed",
    )
    require(
        "WEAVE_TEST_PASSWORD" not in live
        and "WEAVE_E2E_AUTHOR_PASSWORD" not in live
        and "isolated-e2e-identities.sh provision" not in live,
        "obsolete credential-based Flutter identity provisioning remains in the live lane",
    )

    require(
        "workflow_run:" in deployment
        and "- Live Stack Product Flow" in deployment
        and "weave-live-stack-acceptance-evidence" in deployment,
        "persistent deployment is not downstream of the isolated product flow",
    )
    require(
        "tools/candidate_cut_run_resolver.py" in live
        and "gh api --paginate --slurp" in live
        and '--source-sha "$WEAVE_IMAGE_SOURCE_COMMIT"' in live
        and '--repository "$GITHUB_REPOSITORY"' in live
        and '--run-id "$candidate_run_id"' in live
        and "runs?head_sha=${WEAVE_IMAGE_SOURCE_COMMIT}" not in live
        and "[0].id // empty" not in live,
        "dogfood does not uniquely resolve one successful protected Candidate Cut",
    )
    require(
        '.name == "Candidate Cut"' not in live
        and 'jq -r .name <<<"$run"' not in live
        and "WORKFLOW_PATH = \".github/workflows/candidate-images.yml\"" in run_resolver
        and '"workflow_dispatch"' in run_resolver
        and '"head_branch": "dev"' in run_resolver
        and '"head_sha": source_sha' in run_resolver
        and 'f"Candidate Cut {source_sha}"' in run_resolver
        and '"conclusion": "success"' in run_resolver
        and "len(matching) != 1" in run_resolver,
        "Candidate Cut resolver does not enforce one shared dynamic-title metadata contract",
    )
    require(
        capacity_preflight.count("subprocess.run(") == 1
        and '["docker", "info"]' in capacity_preflight
        and '["docker", "compose", "version"]' in capacity_preflight
        and "minimum_free_gib * GIB" in capacity_preflight
        and "docker builder prune" not in capacity_preflight
        and "docker system prune" not in capacity_preflight
        and "docker volume" not in capacity_preflight,
        "shared runner capacity preflight is not read-only or complete",
    )
    ordered(
        live,
        (
            "Verify runner capacity before candidate work",
            "--minimum-free-gib 20",
            "Resolve protected dev source candidate",
        ),
        "live runner capacity preflight",
    )
    ordered(
        deployment,
        (
            "Verify runner capacity before persistent deployment work",
            "--minimum-free-gib 20",
            "Resolve pinned specification corpus",
        ),
        "persistent runner capacity preflight",
    )
    ordered(
        ios,
        (
            "Verify runner capacity before physical-device build",
            "--minimum-free-gib 20",
            "Install the exact candidate in place",
        ),
        "physical-device runner capacity preflight",
    )
    require(
        "tools/runner_capacity_preflight.py" in test_app
        and "--minimum-free-gib 8" in test_app
        and "require_free_disk_space" not in test_app,
        "local Fresh product proof still duplicates runner capacity logic",
    )
    require("ref: ${{ env.LANE_CANDIDATE_COMMIT }}" in deployment, "persistent deployment does not check out the exact lane candidate")
    require(
        'git merge-base --is-ancestor "$LANE_CANDIDATE_COMMIT" origin/dogfood' in deployment,
        "manual persistent deployment can select a commit outside dogfood",
    )
    require("weave-live" in deployment, "persistent deployment is not pinned to the dedicated live runner label")
    ordered(
        deployment,
        (
            "weave-test-stack-evidence-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}",
            'phase:"initialized"',
            "WEAVE_TEST_STACK_EVIDENCE_DIR=$evidence_dir",
            '[[ "$LANE_CANDIDATE_COMMIT" =~ ^[0-9a-f]{40}$ ]]',
            '[[ "${WEAVE_ENV_FILE:-}" == /* ]]',
            'phase:"request-validated"',
            "Consume immutable manifest and exact isolated image evidence",
            "tools/candidate_source_mapping.py",
            "Upload persistent dogfood evidence",
        ),
        "persistent support-safe evidence lifecycle",
    )
    require(
        "weave.test-stack-run-context.v1" in deployment
        and 'deploymentMode:$mode,phase:"initialized"' in deployment
        and 'path: ${{ env.WEAVE_TEST_STACK_EVIDENCE_DIR }}' in deployment
        and "if-no-files-found: error" in deployment,
        "persistent deployment does not initialize support-safe evidence before fallible verification",
    )
    require(
        "deployment_mode:" in deployment
        and "default: routine" in deployment
        and "- routine" in deployment
        and "- fresh-start" in deployment
        and "DEPLOYMENT_MODE: ${{ inputs.deployment_mode || 'routine' }}" in deployment,
        "persistent deployment does not distinguish routine convergence from explicit Fresh Start",
    )
    require(
        "TF_VAR_create_test_user" not in deployment
        and "TF_VAR_test_user_password" not in deployment
        and "WEAVE_TEST_PASSWORD" not in deployment,
        "persistent dogfood carries an obsolete static human identity contract",
    )
    persistent_credential_names = (
        "TF_VAR_db_admin_password",
        "TF_VAR_backend_db_password",
        "TF_VAR_keycloak_admin_password",
        "TF_VAR_keycloak_db_password",
        "TF_VAR_mas_db_password",
        "TF_VAR_synapse_db_password",
        "TF_VAR_nextcloud_db_password",
        "TF_VAR_nextcloud_admin_password",
        "TF_VAR_nextcloud_backend_actor_token",
        "TF_VAR_matrix_mas_client_secret",
        "TF_VAR_identity_admin_client_secret",
        "TF_VAR_identity_events_hmac_secret",
        "TF_VAR_mas_encryption_secret",
        "TF_VAR_mas_signing_key_pem",
        "TF_VAR_mas_matrix_secret",
        "TF_VAR_matrix_chat_appservice_as_token",
        "TF_VAR_matrix_chat_appservice_hs_token",
        "TF_VAR_synapse_registration_shared_secret",
        "TF_VAR_synapse_macaroon_secret_key",
        "TF_VAR_synapse_form_secret",
    )
    require(
        all(f"{name}:" not in deployment for name in persistent_credential_names),
        "routine persistent deployment overrides restored credential authority",
    )
    for obsolete in (
        "persistent-adoption",
        "WEAVE_ADOPTION_RECEIPT",
        "adoption-check",
        "adoption-rehearsal",
        "weave-persistent-test-evidence",
    ):
        require(obsolete not in deployment, f"obsolete adoption authority remains in persistent deployment: {obsolete}")
    for required in (
        "fresh-start-backup-rehearsal.sh",
        "fresh-start.py plan",
        "FreshStartBackupRehearsal.json",
        "DELETE_OLD_WEAVE:${plan_sha}",
        "issues/1266/comments",
        "fresh-start.py apply",
        "weave-test-stack-evidence",
        "dogfood-deployment-evidence.json",
        "test-stack-manifest.json",
    ):
        require(required in deployment, f"Fresh Start persistent deployment is missing {required!r}")
    ordered(
        deployment,
        (
            "Create or reuse the exact private backup, restore proof, and Fresh Start plan",
            "Resolve exact destructive approval from delivery issue",
            "Apply only the exact approved Fresh Start plan",
            "Prove canonical lifecycle convergence and stable realm artifacts",
            "Verify running image identities and assemble deployment evidence",
            "Upload persistent dogfood evidence",
        ),
        "persistent dogfood deployment",
    )
    ordered(
        deployment,
        (
            "Capture routine persistent resource identity",
            "Prove canonical lifecycle convergence and stable realm artifacts",
            "Compare routine persistent resource identity",
            "Verify running image identities and assemble deployment evidence",
        ),
        "routine persistent dogfood deployment",
    )
    require(
        "tools/dogfood_resource_continuity.py capture" in deployment
        and "tools/dogfood_resource_continuity.py compare" in deployment
        and '--generation "$WEAVE_RESOURCE_GENERATION"' in deployment
        and '--persistent-comparison "$comparison"' in deployment
        and "twoNonDestructiveInstallsPreservedState" in read("tools/dogfood_resource_continuity.py")
        and "humanWriterAbsent" in read("tools/dogfood_resource_continuity.py"),
        "routine deployment does not prove non-destructive state and authority continuity",
    )
    ordered(
        owner_bootstrap,
        (
            "Verify protected dogfood boundary",
            "Create private owner request",
            "Run bounded Server-owned owner bootstrap",
            "Remove private request",
            "Upload support-safe evidence",
        ),
        "bounded first-owner bootstrap",
    )
    require(
        "./compose.sh dogfood bootstrap-owner" in owner_bootstrap
        and ".requestAnchorPresent == true" in owner_bootstrap
        and ".bootstrapAuthorityAbsent == true" in owner_bootstrap
        and ".tokenAbsent == true" in owner_bootstrap
        and "dogfood-member.sh" not in owner_bootstrap
        and "admin-cli" not in owner_bootstrap,
        "first-owner bootstrap does not use the bounded Server-owned lifecycle",
    )
    require(
        "bootstrap-owner" not in deployment
        and "WEAVE_DOGFOOD_MEMBER_EMAIL" not in deployment,
        "routine or Fresh Start deployment must not create or resend a human invitation",
    )
    require(
        ".realmDefinition.semanticRealmSourceDigest" in deployment
        and ".realmDefinition.migrationDefinitionDigest" in deployment
        and ".realmIdentity.semanticRealmSourceDigest" in deployment
        and ".realmIdentity.migrationDefinitionDigest" in deployment
        and ".realmIdentity.overlayDigest" in deployment
        and ".realmIdentity.renderedRealmDigest" in deployment
        and "realm-render-evidence.json" in deployment
        and "candidateRealmDefinitionMatched:true" in deployment
        and "environmentRealmRenderStable:true" in deployment
        and "firstOwnerBootstrapRequired:true" in deployment
        and "ownerInvitationCreated:false" in deployment,
        "persistent deployment does not prove semantic realm identity and environment render convergence",
    )
    require(
        '--runtime-image-evidence "$WEAVE_LIVE_UPLOAD_ROOT/runtime-image-evidence.json"' in live,
        "live automated evidence does not consume runtime-verified realm artifacts",
    )

    for required in (
        "runs-on: [self-hosted, macOS, ARM64, weave-live]",
        "group: weave-live-mac-mini-exclusive",
        "Reverify exact running images and collect current provider health",
        "dogfood_provider_health_evidence.py",
        "--provider-health-evidence",
        "dogfood-provider-health-evidence.json",
    ):
        require(required in readiness, f"final readiness is missing fresh dogfood proof {required!r}")
    require(
        '"providerHealth": require_object(provider_health, "providerHealth", "provider health")' in readiness_assembler,
        "final readiness still trusts the original deployment provider-health snapshot",
    )

    require("workflow_run:" in ios and "- Test Stack Deploy" in ios, "iOS distribution is not downstream of deployment")
    require('gh run download "$deployment_run_id" --name weave-test-stack-evidence' in ios, "iOS candidate is not read from deployment evidence")
    require("name: ios-dogfood" in ios and "cancel-in-progress: true" in ios, "iOS environment/supersession policy is incomplete")
    require("WEAVE_CANDIDATE_COMMIT=${SOURCE_CANDIDATE_SHA}" in ios, "iOS build does not embed its protected dev source")

    require("physical-iphone" in physical and "environment:" in physical, "physical iPhone evidence is not environment-gated")
    require("main-promotion-gate" in promotion or "Main Promotion Gate" in promotion, "main promotion workflow missing")
    require("Candidate Cut" in docs, "iOS dogfood docs do not explain Candidate Cut")

    print("candidate-pipeline-check: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
