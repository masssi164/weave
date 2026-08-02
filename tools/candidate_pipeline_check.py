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
    live = read(".github/workflows/live-stack-e2e.yml")
    deployment = read(".github/workflows/test-stack-deploy.yml")
    ios = read(".github/workflows/ios-dogfood.yml")
    physical = read(".github/workflows/physical-iphone-human-test.yml")
    readiness = read(".github/workflows/human-testing-readiness.yml")
    promotion = read(".github/workflows/main-promotion-gate.yml")
    docs = read("docs/ios-dogfood-distribution.md")
    readiness_assembler = read("tools/human_testing_readiness_assemble.py")

    require("push:\n    branches: [dogfood]" in live, "isolated product flow does not run on the exact dogfood commit")
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
        and "Run the manifest-bound Fresh product proof\n        timeout-minutes: 60"
        in live
        and "Ensure exact isolated Fresh namespace is absent\n        if: always()\n        timeout-minutes: 10"
        in live,
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
        and '{schemaVersion:"weave.test-stack-run-context.v1",phase:"initialized",supportSafe:true,containsSecretValues:false}' in deployment
        and 'path: ${{ env.WEAVE_TEST_STACK_EVIDENCE_DIR }}' in deployment
        and "if-no-files-found: error" in deployment,
        "persistent deployment does not initialize support-safe evidence before fallible verification",
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
        "freshStartBackupRehearsal",
        "freshStartPlan",
        "FreshStartBackupRehearsal.json",
        "DELETE_OLD_WEAVE:$plan_sha",
        "issues/1266/comments",
        "freshStartApply",
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
            "Prove canonical lifecycle convergence and empty second reconciliation",
            "Create a new invitation through the normal identity flow",
            "Verify running image identities and assemble deployment evidence",
            "Upload persistent dogfood evidence",
        ),
        "persistent dogfood deployment",
    )
    require(
        deployment.count('.operationCount == 0') == 2
        and "composeModelStable:true" in deployment
        and "identitySecondPlanEmpty:true" in deployment
        and "newInvitationPending:true" in deployment,
        "persistent deployment does not prove Compose and Identity Ops convergence",
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
        '"providerHealth": require_object(provider_health, "providerHealth", "provider health")'
        in readiness_assembler,
        "final readiness still trusts the original deployment provider-health snapshot",
    )

    require("workflow_run:" in ios and "- Test Stack Deploy" in ios, "iOS distribution is not downstream of deployment")
    require('gh run download "$deployment_run_id" --name weave-test-stack-evidence' in ios, "iOS candidate is not read from deployment evidence")
    require("name: ios-dogfood" in ios and "cancel-in-progress: true" in ios, "iOS environment/supersession policy is incomplete")
    require("WEAVE_CANDIDATE_COMMIT=${SOURCE_CANDIDATE_SHA}" in ios, "iOS build does not embed its protected dev source")
    require(
        "source_candidate_sha:" in ios
        and "laneCandidateCommit:$commit" in ios
        and "sourceCandidateCommit:$sourceCommit" in ios,
        "iOS distribution does not preserve dual source/lane identity",
    )
    require(
        "stable-signing-fallback:" in ios
        and "inputs.upload_to_testflight == false" in ios
        and "github.event_name == 'workflow_run'" in ios
        and "tools/dogfood_ios_development_fallback.sh" in ios
        and "ios-dogfood-distribution.json" in ios,
        "documented stable-signing fallback does not emit protected canonical distribution evidence",
    )
    fallback = ios.split("  stable-signing-fallback:", 1)[1]
    require("- weave-live" in fallback, "physical fallback is not pinned to the dedicated live runner label")

    for value in (
        "protocol_json_base64:",
        "Physical iPhone Human Test",
        "physical_iphone_human_evidence.py",
        "--require-passed",
        "name: ios-dogfood",
    ):
        require(value in physical, f"physical iPhone evidence workflow is missing {value!r}")
    for value in (
        "physical_evidence_run_id:",
        "physical-iphone-human-evidence-${CANDIDATE_SHA}",
        ".github/workflows/physical-iphone-human-test.yml",
        "physicalAcceptance.protocol.testerConfirmed",
        "--require-ready",
    ):
        require(value in readiness, f"physical readiness workflow is missing {value!r}")
    require(
        "PHYSICAL_IPHONE_VOICEOVER_RESULT" in readiness_assembler
        and "HUMAN_TESTING_READINESS_RESULT" in readiness_assembler,
        "readiness assembler does not emit stable physical/final markers",
    )
    require("human_testing_readiness_manifest.py validate" in promotion and "--require-ready" in promotion, "main promotion does not require a ready exact-candidate manifest")
    require("--provider-age-reference generated-at" in promotion, "main promotion does not revalidate immutable readiness freshness at artifact generation")
    require(
        "candidate_source_mapping.py" in promotion
        and "SOURCE_CANDIDATE_SHA" in promotion
        and "sourceCandidateCommit" in promotion,
        "main promotion does not prove the protected dev source behind the dogfood lane",
    )
    require(
        all(value in promotion for value in (
            "test-stack-deploy.yml|dogfood|Weave release owner or dogfood operator",
            "ios-dogfood.yml|ios-dogfood|Weave release owner plus client/iOS release owner",
            "physical-iphone-human-test.yml|ios-dogfood|Weave release owner plus client/iOS release owner",
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
