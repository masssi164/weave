#!/usr/bin/env python3
"""Validate the direct Compose development-to-human-test delivery loop."""

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
    e2e = read(".github/workflows/live-stack-e2e.yml")
    prepare = read(".github/workflows/ios-dogfood.yml")
    owner = read(".github/workflows/dogfood-owner-bootstrap.yml")
    capacity = read("tools/runner_capacity_preflight.py")
    test_app = read("gradle/tasks/test-app.sh")

    for obsolete in (
        ".github/workflows/candidate-images.yml",
        ".github/workflows/human-testing-readiness.yml",
        ".github/workflows/physical-iphone-human-test.yml",
        ".github/workflows/test-stack-deploy.yml",
    ):
        require(not (ROOT / obsolete).exists(), f"retired workflow remains active: {obsolete}")

    require("name: Full Compose E2E" in e2e, "the E2E workflow has no stable check name")
    require("branches: [dogfood]" in e2e and "  pull_request:" not in e2e
            and "  merge_group:" not in e2e and "  workflow_dispatch:" not in e2e, "integrated E2E must qualify protected Dogfood only")
    require("run_native_qualification.py" in e2e, "mandatory independent native qualification is missing")
    require("--qualification-only" in prepare and "verify_main_promotion_evidence.py" in prepare,
            "physical preparation must verify exact deployment and both native results")
    verifier = read("tools/verify_main_promotion_evidence.py")
    for marker in ("native-qualification.json", "aarch64-apple-ios-sim", "teardown-evidence.json",
                   "preparation-result.json", "specificationCommit", "sourceTree"):
        require(marker in verifier, f"promotion evidence misses {marker}")
    require("name: Full Compose E2E" in e2e, "the required E2E check context is missing")
    exact_source = "${{ github.sha }}"
    require(
        e2e.count(exact_source) >= 3
        and "ref: ${{ env.WEAVE_CANDIDATE_COMMIT }}" in e2e,
        "Dogfood qualification must run against the exact protected commit",
    )
    require(
        "permissions:\n  actions: read\n  contents: read" in e2e,
        "qualification must read exact GitHub workflow evidence",
    )
    node_setup = "actions/setup-node@a0853c24544627f65ddf259abe73b1d18a591444"
    require(
        e2e.count(node_setup) == 2 and prepare.count(node_setup) == 1,
        "every exact-source Server build must install the pinned Node runtime",
    )
    ordered(
        e2e,
        (
            "Verify bounded runner",
            "Verify capacity and Compose runtime",
            "Resolve pinned specification corpus",
            "Run full isolated product flow",
            "./gradlew --no-daemon specCorpusConformance testApp",
            "Upload support-safe E2E evidence",
        ),
        "Full Compose E2E",
    )
    require("Candidate Cut" not in e2e and "candidate-manifest" not in e2e, "E2E still depends on an image candidate")

    require("workflow_call:" not in prepare, "physical preparation must be explicitly dispatched")
    require("needs: full-compose-e2e" in e2e, "dogfood deployment is not downstream of E2E")
    require("github.ref == 'refs/heads/dogfood'" in e2e, "automatic deployment is not dogfood-only")
    require("./gradlew --no-daemon dogfoodUp" in e2e, "dogfood is not started through the root Compose lifecycle")
    require("deployment_mode" not in e2e and "Fresh Start" not in e2e, "retired Fresh Start mode remains active")
    require("candidate-manifest" not in e2e and "Candidate Cut" not in e2e, "delivery still consumes candidate artifacts")

    ordered(
        prepare,
        (
            "Verify successful Full Compose E2E for the exact commit",
            "Verify capacity and Compose runtime",
            "Start dogfood twice and prove TLS identity is stable",
            "Create first owner invitation in private Mailpit",
            "Build, sign, install in place, and launch on physical iPhone",
        ),
        "Prepare Human Test",
    )
    for marker in (
        "workflow_dispatch:",
        "WEAVE_IOS_RESET_MODE: update_in_place",
        "WEAVE_IOS_INSTALL_TRANSPORT: wifi",
        "secrets.WEAVE_IOS_DEVICE_ID",
        "tools/dogfood_cert_persistence_smoke.py",
        "tools/dogfood_iphone_entry.sh",
        "./gradlew --no-daemon dogfoodUp",
        "load_context('dogfood'",
        "env['WEAVE_MAILPIT_URL']",
    ):
        require(marker in prepare, f"Prepare Human Test is missing {marker!r}")
    for step_name in (
        "Verify current protected dogfood candidate",
        "Start dogfood twice and prove TLS identity is stable",
        "Build, sign, install in place, and launch on physical iPhone",
    ):
        step = prepare.split(f"      - name: {step_name}\n", 1)[1].split("      - name:", 1)[0]
        require('[[ "$(git rev-parse origin/dogfood)" == "$DOGFOOD_COMMIT" ]]' in step,
                f"{step_name} must reject a superseded candidate")
    for forbidden in ("TestFlight", "upload_to_testflight", "candidateManifestDigest", "environment:"):
        require(forbidden not in prepare, f"Prepare Human Test retains the retired gate {forbidden!r}")
    require("infra/weave-workspace/compose.sh dogfood bootstrap-owner" in owner, "first-owner invitation does not use the Server-owned Compose boundary")
    require("environment:" not in owner, "first-owner invitation still waits for an environment approval")

    require(
        capacity.count("subprocess.run(") == 1
        and '["docker", "info"]' in capacity
        and '["docker", "compose", "version"]' in capacity
        and "docker system prune" not in capacity
        and "docker volume" not in capacity,
        "runner capacity preflight must remain shared and read-only",
    )
    require("tools/runner_capacity_preflight.py" in test_app, "testApp bypasses the shared capacity preflight")

    print("candidate-pipeline-check: passed delivery=direct-compose qualification=compose-macos-ios hil=explicit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
