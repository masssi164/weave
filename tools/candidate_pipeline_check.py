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
    physical = read(".github/workflows/human-testing-readiness.yml")
    promotion = read(".github/workflows/main-promotion-gate.yml")
    docs = read("docs/ios-dogfood-distribution.md")
    readiness_assembler = read("tools/human_testing_readiness_assemble.py")

    require("push:\n    branches: [dogfood]" in live, "isolated product flow does not run on the exact dogfood commit")
    ordered(
        live,
        (
            "Capture persistent dogfood resources",
            "Run the credential-free Fresh product proof",
            "./gradlew --no-daemon testApp",
            "Verify persistent dogfood preservation",
            "Upload support-safe product-flow evidence",
        ),
        "isolated Fresh product flow",
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

    require("workflow_run:" in deployment and "- Live Stack Product Flow" in deployment, "persistent deployment is not downstream of the isolated product flow")
    require("ref: ${{ env.CANDIDATE_SHA }}" in deployment, "persistent deployment does not check out exact evidence candidate")
    require(
        'git merge-base --is-ancestor "$CANDIDATE_SHA" origin/dogfood' in deployment,
        "manual persistent deployment can select a commit outside dogfood",
    )
    require("- weave-live" in deployment, "persistent deployment is not pinned to the dedicated live runner label")
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
    require(
        "bash ./smoke-test.sh" not in deployment
        and deployment.count("bash ./operator-check.sh") == 2,
        "persistent dogfood must use non-destructive operator checks without the automation-user smoke suite",
    )
    require(deployment.count("./install.sh") == 2, "persistent candidate must be installed exactly twice")
    ordered(
        deployment,
        (
            "Run requested persistent member operation before baseline",
            "Capture persistent dogfood state before candidate deployment",
            "      - name: Run requested persistent member operation\n",
        ),
        "persistent dogfood member lifecycle",
    )
    require(
        "inputs.dogfood_member_operation != 'status'" in deployment
        and './dogfood-member.sh "$DOGFOOD_MEMBER_OPERATION"' in deployment,
        "explicit persistent member recovery operations do not run before the online baseline gate",
    )
    ordered(
        deployment,
        (
            "persistent-dogfood-observation.sh capture",
            "Apply the same candidate a second time non-destructively",
            "persistent-dogfood-observation.sh compare",
            "collect-provider-health",
            "dogfood_deployment_evidence.py assemble",
        ),
        "persistent dogfood deployment",
    )
    require("/actuator/metrics" in deployment and "providerProbeTriggered=false" not in deployment, "deployment must collect cached metrics through Actuator without fabricating a result")

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
