#!/usr/bin/env python3
"""Validate bounded self-hosted execution for the lean Compose delivery loop."""

from __future__ import annotations

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"live-stack-runner-hygiene: {message}")


def check(e2e: str, prepare: str, owner: str, test_app: str, native: str) -> None:
    for document in (e2e, prepare, owner):
        require("group:" in document, "dogfood mutations must have bounded concurrency")
        require("EXPECTED_RUNNER_NAME: weave-live-mac-mini" in document, "runner identity is not explicit")
        require("tools/runner_capacity_preflight.py" in document, "shared capacity preflight is missing")
        require("--minimum-free-gib 20" in document, "capacity floor is not 20 GiB")

    require("runs-on: [self-hosted, macOS, ARM64, weave-live]" in e2e, "E2E is not pinned to the live runner")
    require("cancel-in-progress: false" in e2e, "exact-candidate runtime execution must not be cancelled by a later candidate")
    require("./gradlew --no-daemon specCorpusConformance testApp" in e2e, "E2E does not use the authoritative product-flow task")
    require("trap cleanup EXIT" in test_app and "cleanup_test_app_runtime.py" in test_app, "testApp does not own exact cleanup")
    require("needs: hosted-artifacts" in e2e and "uses: ./.github/workflows/build-artifacts.yml" in e2e,
            "runtime qualification must wait for hosted artifact resolution")
    require("WEAVE_TEST_APP_MODE: member-collaboration" in e2e
            and 'WEAVE_TEST_APP_RELEASE_MCP: "false"' in e2e,
            "ordinary member collaboration must be independently selected")
    for workflow, components in ((e2e, "runtime-linux-amd64,ios-simulator"),
                                 (prepare, "runtime-linux-amd64,ios-device")):
        require("WEAVE_BUILD_ARTIFACT_ROOT:" in workflow and "WEAVE_CANDIDATE_COMPOSITION:" in workflow
                and "tools/build_artifacts.py consume" in workflow and "--components " + components in workflow,
                "live runtime and physical paths require verified hosted components")
        for forbidden in ("flutter build", "build-for-testing", ":bootJar", "buildx build"):
            require(forbidden not in workflow, "live workflow must not compile delivery packages")
    require(e2e.index("name: Consume verified hosted runtime and iOS Simulator artifacts")
            < e2e.index("name: Run fresh collaboration between two ordinary iOS members"),
            "artifact verification must precede actual member execution")
    branches = re.findall(r'if \[\[ -n "\$\{WEAVE_BUILD_ARTIFACT_ROOT:-\}" \]\]; then\n(.*?)\nfi', test_app, re.S)
    driver = [branch for branch in branches if "product_flow_command=" in branch]
    require(len(driver) == 1 and 'product_flow_command=(java -cp "${WEAVE_TEST_APP_DRIVER_CLASSPATH}")' in driver[0]
            and "product_flow_target=(com.massimotter.weave.e2e.FreshProductFlow)" in driver[0]
            and 'fail "hosted integration driver is missing"' in driver[0]
            and "gradlew" not in driver[0],
            "artifact-backed testApp must execute the hosted JVM driver without a compile fallback")
    require("'macos': {'status': 'manual-only'}" in native
            and "run_macos" not in native,
            "desktop macOS must remain explicitly manual in the iPhone lane")
    require("tools/prepare_member_scenario.py --environment dogfood" in e2e
            and "tools/dogfood_functional_smoke.py --stage deployment" in e2e,
            "persistent deployment still requires prepared ordinary members and real collaboration")

    for forbidden in ("docker system prune", "docker volume prune", "unlock-keychain", "destructive_uninstall"):
        require(forbidden not in e2e + prepare + owner, f"workflow contains broad or identity-breaking operation {forbidden!r}")


def main() -> int:
    e2e = (ROOT / ".github/workflows/live-stack-e2e.yml").read_text(encoding="utf-8")
    prepare = (ROOT / ".github/workflows/ios-dogfood.yml").read_text(encoding="utf-8")
    owner = (ROOT / ".github/workflows/dogfood-owner-bootstrap.yml").read_text(encoding="utf-8")
    test_app = (ROOT / "gradle/tasks/test-app.sh").read_text(encoding="utf-8")
    native = (ROOT / "client/tool/run_native_qualification.py").read_text(encoding="utf-8")
    check(e2e, prepare, owner, test_app, native)
    variants = [
        (e2e.replace("needs: hosted-artifacts", "needs: []"), prepare, test_app, native),
        (e2e.replace("WEAVE_TEST_APP_MODE: member-collaboration", "WEAVE_TEST_APP_MODE: full"), prepare, test_app, native),
        (e2e.replace("--components runtime-linux-amd64,ios-simulator", "--components runtime-linux-amd64"), prepare, test_app, native),
        (e2e, prepare + "\n# flutter build ios\n", test_app, native),
        (e2e, prepare, test_app.replace('product_flow_command=(java -cp "${WEAVE_TEST_APP_DRIVER_CLASSPATH}")',
                                     'product_flow_command=("${REPOSITORY_ROOT}/gradlew")'), native),
        (e2e, prepare, test_app, native.replace("'macos': {'status': 'manual-only'}", "'macos': {'status': 'passed'}")),
        (e2e.replace("tools/dogfood_functional_smoke.py --stage deployment", "echo skipped behavior"), prepare, test_app, native),
    ]
    for changed_e2e, changed_prepare, changed_test_app, changed_native in variants:
        try:
            check(changed_e2e, changed_prepare, owner, changed_test_app, changed_native)
        except SystemExit:
            pass
        else:
            raise SystemExit("live-stack-runner-hygiene: incomplete artifact or member qualification was accepted")
    print("live-stack-runner-hygiene: ok lifecycle=bounded cleanup=testApp-owned")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
