#!/usr/bin/env python3
"""Validate operator-facing environment and profile contracts."""

from __future__ import annotations

import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import load_context  # noqa: E402
from compose_runtime import runtime_root_services  # noqa: E402


def _materialize_example(profile: str, destination: Path) -> Path:
    source = ROOT / f"environments/{profile}.env.example"
    value = re.sub(
        r"sha256:replace-with-[a-zA-Z0-9.-]+",
        "sha256:" + "a" * 64,
        source.read_text(encoding="utf-8"),
    )
    for key, suffix in (
        ("WEAVE_GENERATED_ROOT", f"{profile}-generated"),
        ("WEAVE_SECRET_ROOT", f"{profile}-secrets"),
        ("WEAVE_TLS_ROOT", f"{profile}-tls"),
    ):
        value = re.sub(
            rf"^{key}=.*$",
            f"{key}={destination.parent / suffix}",
            value,
            flags=re.MULTILINE,
        )
    destination.write_text(value, encoding="utf-8")
    os.chmod(destination, 0o600)
    return destination


def main() -> int:
    with tempfile.TemporaryDirectory() as temporary:
        temporary_root = Path(temporary)
        dogfood = load_context(
            "dogfood",
            ROOT,
            str(_materialize_example("dogfood", temporary_root / "dogfood.env")),
        )
        previous_e2e_scope = os.environ.get("WEAVE_E2E_STACK_SCOPE")
        previous_e2e_run_id = os.environ.get("WEAVE_E2E_RUN_ID")
        try:
            os.environ["WEAVE_E2E_STACK_SCOPE"] = "isolated"
            os.environ["WEAVE_E2E_RUN_ID"] = "operator-environment-contract"
            e2e = load_context(
                "e2e",
                ROOT,
                str(_materialize_example("e2e", temporary_root / "e2e.env")),
            )
        finally:
            if previous_e2e_scope is None:
                os.environ.pop("WEAVE_E2E_STACK_SCOPE", None)
            else:
                os.environ["WEAVE_E2E_STACK_SCOPE"] = previous_e2e_scope
            if previous_e2e_run_id is None:
                os.environ.pop("WEAVE_E2E_RUN_ID", None)
            else:
                os.environ["WEAVE_E2E_RUN_ID"] = previous_e2e_run_id
        dev = load_context("dev", ROOT)
        prod = load_context(
            "prod",
            ROOT,
            str(_materialize_example("prod", temporary_root / "prod.env")),
        )

    assert dogfood.environment == "dogfood"
    assert e2e.environment == "e2e"
    assert e2e.env["WEAVE_STACK_SCOPE"] == "isolated"
    assert dev.environment == "dev"
    assert prod.environment == "prod"

    dev_tools = load_context("dev", ROOT)
    dev_tools.active_profiles = ("dev", "dev-tools")
    assert runtime_root_services(dev_tools) == ("keycloak", "mailpit")

    runtime_source = (ROOT / "scripts" / "compose_runtime.py").read_text(encoding="utf-8")
    assert (
        'if context.environment != "dev":\n'
        '            compose(context, "up", "-d", "postgres", "postgres-reconcile")'
        in runtime_source
    )
    assert (
        'if context.environment != "dev" and "provider-nextcloud" in context.active_profiles:'
        in runtime_source
    )

    shell_source = (ROOT / "compose.sh").read_text(encoding="utf-8")
    assert "<dev|dogfood|prod|e2e>" in shell_source
    assert "deprecated CI-only compatibility selector" not in shell_source
    assert "test" not in shell_source

    repository = ROOT.parents[1]
    for workflow_name in ("test-stack-deploy.yml", "human-testing-readiness.yml"):
        workflow = (repository / ".github/workflows" / workflow_name).read_text(encoding="utf-8")
        assert 'load_context("test"' not in workflow
        assert "./compose.sh test " not in workflow
        assert "./operator-check.sh test" not in workflow
        assert "./install.sh test" not in workflow

    backend_resources = repository / "server/src/main/resources"
    mcp_resources = repository / "weave-mcp-server/src/main/resources"
    for environment in ("dogfood", "e2e"):
        backend_profile = (backend_resources / f"application-{environment}.yml").read_text(
            encoding="utf-8"
        )
        assert f"on-profile: {environment}" in backend_profile
        assert f"profile: {environment}" in backend_profile
        assert "org.postgresql.Driver" in backend_profile

        mcp_profile = (mcp_resources / f"application-{environment}.yml").read_text(
            encoding="utf-8"
        )
        assert f"on-profile: {environment}" in mcp_profile
        assert f"profile: {environment}" in mcp_profile
        assert "issuer-uri:" in mcp_profile
        assert "resource-uri:" in mcp_profile
        assert "org.postgresql.Driver" not in mcp_profile
        assert "datasource:" not in mcp_profile
        assert "jpa:" not in mcp_profile

    dogfood_example = (ROOT / "environments/dogfood.env.example").read_text(encoding="utf-8")
    assert "WEAVE_MAILPIT_IMAGE=" in dogfood_example
    assert "WEAVE_MAILPIT_DATA_VOLUME=" in dogfood_example
    assert "WEAVE_MAILPIT_REQUIRE_TLS=true" in dogfood_example
    assert "WEAVE_SMTP_" not in dogfood_example

    print("operator environment contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
