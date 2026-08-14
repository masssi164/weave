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
    require("branches: [dev, dogfood]" in e2e, "E2E must run for dev and dogfood")
    require("name: Full Compose E2E" in e2e, "the required E2E check context is missing")
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
        "https://mail.weave.test:44443",
    ):
        require(marker in prepare, f"Prepare Human Test is missing {marker!r}")
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

    print("candidate-pipeline-check: passed delivery=direct-compose e2e=single-gate")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
