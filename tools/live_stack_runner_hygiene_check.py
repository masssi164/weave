#!/usr/bin/env python3
"""Validate the bounded, credential-free Live Stack product-flow workflow."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/live-stack-e2e.yml"
CANDIDATE_WORKFLOW = ROOT / ".github/workflows/candidate-images.yml"
DOGFOOD_DEPLOY_WORKFLOW = ROOT / ".github/workflows/test-stack-deploy.yml"
DOGFOOD_MEMBER_WORKFLOW = ROOT / ".github/workflows/dogfood-member.yml"
IOS_DOGFOOD_WORKFLOW = ROOT / ".github/workflows/ios-dogfood.yml"
PERSISTENT_RESOURCE_GUARD = ROOT / "tools/persistent_dogfood_resource_guard.sh"
TEST_APP = ROOT / "gradle/tasks/test-app.sh"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"live-stack-runner-hygiene: {message}")


def main() -> int:
    workflow = WORKFLOW.read_text(encoding="utf-8")
    candidate_workflow = CANDIDATE_WORKFLOW.read_text(encoding="utf-8")
    deployment = DOGFOOD_DEPLOY_WORKFLOW.read_text(encoding="utf-8")
    member = DOGFOOD_MEMBER_WORKFLOW.read_text(encoding="utf-8")
    ios = IOS_DOGFOOD_WORKFLOW.read_text(encoding="utf-8")
    guard = PERSISTENT_RESOURCE_GUARD.read_text(encoding="utf-8")
    test_app = TEST_APP.read_text(encoding="utf-8")

    ordered_steps = (
        "- name: Verify bounded runner",
        "- name: Check out exact lane candidate",
        "- name: Resolve protected dev source candidate",
        "- name: Set up Java 21",
        "- name: Set up Gradle",
        "- name: Resolve immutable candidate manifest",
        "- name: Prepare run-scoped Docker client authority",
        "- name: Pull and bind all immutable candidate images",
        "- name: Verify runtime prerequisites",
        "- name: Capture persistent dogfood resources",
        "- name: Run the manifest-bound Fresh product proof",
        "- name: Ensure exact isolated Fresh namespace is absent",
        "- name: Verify product proof and persistent dogfood preservation",
        "- name: Upload support-safe live-stack evidence",
    )
    positions = [workflow.index(step) for step in ordered_steps]
    require(positions == sorted(positions), "product-flow stages are misordered")

    for document in (workflow, deployment, member, ios):
        require(
            "group: weave-live-mac-mini-exclusive" in document,
            "all Mac runner mutators must share the exclusive lock",
        )
    require(
        "cancel-in-progress: false" in workflow,
        "the destructive live workflow must not be cancelled mid-cleanup",
    )
    require(
        "runs-on: [self-hosted, macOS, ARM64, weave-live]" in workflow
        and "needs: isolation-gate" in workflow
        and "Fail closed without explicit isolation" in workflow,
        "the product flow must be pinned to the approved isolated runner",
    )
    require(
        "EXPECTED_RUNNER_NAME: weave-live-mac-mini" in workflow
        and '[[ "${RUNNER_NAME:-}" == "$EXPECTED_RUNNER_NAME" ]]' in workflow,
        "the workflow must bind the exact runner name",
    )
    require(
        "repository: ${{ github.repository_owner }}/weave-specs" in workflow
        and workflow.count(
            "ssh-key: ${{ secrets.WEAVE_SPECS_DEPLOY_KEY }}"
        )
        == 1,
        "the private pinned specification corpus must use its scoped deploy key",
    )
    require(
        "weave-live-docker-auth-${{ github.run_id }}-${{ github.run_attempt }}"
        in workflow
        and 'GHCR_TOKEN: ${{ github.token }}' in workflow
        and 'DOCKER_CONFIG="$DOCKER_AUTH_ROOT"' in workflow
        and 'python3 weave/tools/write_docker_auth_config.py'
        in workflow
        and 'compose_plugin_path="$(docker info --format' in workflow
        and '--cli-plugin-dir "$compose_plugin_dir"' in workflow
        and '.cliPluginsExtraDirs == [$compose_plugin_dir]' in workflow
        and 'DOCKER_CONFIG="$DOCKER_AUTH_ROOT" docker compose version'
        in workflow
        and '(.credsStore? // "") == ""' in workflow
        and '(.credHelpers? // {}) == {}' in workflow
        and 'printf \'DOCKER_CONFIG=%s\\n\' "$DOCKER_AUTH_ROOT" >> "$GITHUB_ENV"'
        in workflow,
        "candidate pulls must use one non-interactive run-scoped Docker authority",
    )
    require(
        "docker login" not in workflow
        and "docker-credential-" not in workflow
        and '"credsStore":' not in workflow
        and '"credHelpers":' not in workflow,
        "candidate pulls must not invoke host credential stores",
    )
    require(
        '("DOCKER_AUTH_ROOT", "weave-live-docker-auth-")' in workflow
        and 'DOCKER_CONFIG="$DOCKER_AUTH_ROOT" docker logout ghcr.io' in workflow
        and "unlock-keychain" not in workflow,
        "run-scoped Docker authority must be removed without unlocking host keychains",
    )
    require(
        "./gradlew --no-daemon testApp" in workflow
        and "WEAVE_TEST_APP_OUTPUT_ROOT" in workflow,
        "the workflow must use the one authoritative Fresh product-flow task",
    )
    require(
        "timeout-minutes: 75" in workflow
        and "Run the manifest-bound Fresh product proof\n        timeout-minutes: 60"
        in workflow
        and "Ensure exact isolated Fresh namespace is absent\n        if: always()\n        timeout-minutes: 10"
        in workflow,
        "the live job must reserve a bounded exact-cleanup window",
    )
    require(
        "WEAVE_EXPECTED_E2E_NAMESPACE" in workflow
        and "infra/weave-workspace/teardown.sh e2e" in workflow
        and "cleanup_test_app_runtime.py" in workflow
        and 'label=com.docker.compose.project=$namespace' in workflow
        and workflow.count('label=com.massimotter.weave.namespace=$namespace') == 2
        and "weave-live-stack-failure-cleanup-evidence" in workflow
        and '("CLEANUP_ROOT", "weave-live-stack-failure-cleanup-")'
        in workflow,
        "workflow-level recovery must teardown and prove the exact isolated namespace absent",
    )
    for marker in (
        '"weave.test-app-product-flow/v1"',
        '"keycloak-required-actions-real-chromium"',
        '"authorization_code_pkce_s256"',
        '"client_credentials_private_key_jwt"',
        '.mcpTool == "files.search"',
        ".credentialsIncluded == false",
        ".actionLinksIncluded == false",
        ".supportSafe == true",
        ".collaboration.repeatCount == 2",
        ".directSynapseVerified == true",
        "human_testing_automated_evidence.py live",
        '--runtime-image-evidence "$WEAVE_LIVE_UPLOAD_ROOT/runtime-image-evidence.json"',
    ):
        require(marker in workflow, f"support-safe evidence gate is missing {marker!r}")
    require(
        'component_ref server' in workflow
        and 'component_ref mcp-server' in workflow
        and 'component_ref keycloak-runtime' in workflow
        and 'component_ref identity-ops' not in workflow
        and '[[ "$image" == *@sha256:* ]]' in workflow,
        "all runtime images must come from the immutable candidate manifest",
    )
    require(
        candidate_workflow.count("platforms: linux/amd64") == 3
        and "DOCKER_DEFAULT_PLATFORM: linux/amd64" in workflow
        and "DOCKER_DEFAULT_PLATFORM: linux/amd64" in deployment
        and 'docker pull --platform "$DOCKER_DEFAULT_PLATFORM" "$image"'
        in workflow
        and "{{.Os}}/{{.Architecture}}" in workflow
        and '"$DOCKER_DEFAULT_PLATFORM" ]]' in workflow,
        "the ARM64 Live runner must pull and run the three AMD64 candidate images explicitly",
    )
    require(
        workflow.count("persistent_dogfood_resource_guard.sh") == 2
        and "^weave[-_]" in guard
        and "^weave[-_]e2e[-_]" in guard
        and "cmp -s" in guard,
        "the run must prove persistent dogfood resources are unchanged",
    )
    for forbidden in (
        "WEAVE_TEST_USERNAME",
        "WEAVE_TEST_PASSWORD",
        "WEAVE_E2E_AUTHOR_PASSWORD",
        "isolated-e2e-identities.sh prepare",
        "isolated-e2e-identities.sh provision",
        "integration-multi-user-e2e",
        "flutter test integration_test",
        "xcrun simctl",
        "reset-password",
    ):
        require(forbidden not in workflow, f"obsolete credential lane remains: {forbidden}")

    require(
        "prepare_test_app_context.py" in test_app
        and "WEAVE_E2E_STACK_SCOPE=isolated" in test_app
        and "cleanup_test_app_runtime.py" in test_app
        and "teardown.sh" in test_app
        and "trap cleanup EXIT" in test_app,
        "testApp must own isolated context creation and exact cleanup",
    )
    print("live-stack-runner-hygiene: ok product-flow=credential-free cleanup=exact")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
