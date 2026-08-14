#!/usr/bin/env python3
"""Validate bounded self-hosted execution for the lean Compose delivery loop."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"live-stack-runner-hygiene: {message}")


def main() -> int:
    e2e = (ROOT / ".github/workflows/live-stack-e2e.yml").read_text(encoding="utf-8")
    prepare = (ROOT / ".github/workflows/ios-dogfood.yml").read_text(encoding="utf-8")
    owner = (ROOT / ".github/workflows/dogfood-owner-bootstrap.yml").read_text(encoding="utf-8")
    test_app = (ROOT / "gradle/tasks/test-app.sh").read_text(encoding="utf-8")

    for document in (e2e, prepare, owner):
        require("group:" in document, "dogfood mutations must have bounded concurrency")
        require("EXPECTED_RUNNER_NAME: weave-live-mac-mini" in document, "runner identity is not explicit")
        require("tools/runner_capacity_preflight.py" in document, "shared capacity preflight is missing")
        require("--minimum-free-gib 20" in document, "capacity floor is not 20 GiB")

    require("runs-on: [self-hosted, macOS, ARM64, weave-live]" in e2e, "E2E is not pinned to the live runner")
    require("cancel-in-progress: ${{ github.event_name == 'pull_request' }}" in e2e, "only superseded PR E2E runs may be cancelled")
    require("./gradlew --no-daemon specCorpusConformance testApp" in e2e, "E2E does not use the authoritative product-flow task")
    require("trap cleanup EXIT" in test_app and "cleanup_test_app_runtime.py" in test_app, "testApp does not own exact cleanup")

    for forbidden in ("docker system prune", "docker volume prune", "unlock-keychain", "destructive_uninstall"):
        require(forbidden not in e2e + prepare + owner, f"workflow contains broad or identity-breaking operation {forbidden!r}")

    print("live-stack-runner-hygiene: ok lifecycle=bounded cleanup=testApp-owned")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
