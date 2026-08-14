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

from compose_env import ContractError, load_context  # noqa: E402
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


def _materialize_dev_tools(destination: Path) -> Path:
    value = (ROOT / "environments/dev.env").read_text(encoding="utf-8")
    value = re.sub(
        r"^COMPOSE_PROFILES=.*$",
        "COMPOSE_PROFILES=dev,dev-tools",
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
        legacy_dogfood_path = _materialize_example(
            "dogfood", temporary_root / "dogfood-without-mailpit-url.env"
        )
        legacy_dogfood_path.write_text(
            re.sub(
                r"^WEAVE_MAILPIT_URL=.*\n?",
                "",
                legacy_dogfood_path.read_text(encoding="utf-8"),
                flags=re.MULTILINE,
            ),
            encoding="utf-8",
        )
        legacy_dogfood = load_context("dogfood", ROOT, str(legacy_dogfood_path))
        assert legacy_dogfood.env["WEAVE_MAILPIT_URL"] == "https://mail.weave.test:44443"
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
        dev_tools = load_context(
            "dev",
            ROOT,
            str(_materialize_dev_tools(temporary_root / "dev-tools.env")),
        )
        prod = load_context(
            "prod",
            ROOT,
            str(_materialize_example("prod", temporary_root / "prod.env")),
        )

        local_images = {
            "WEAVE_BACKEND_IMAGE": "sha256:" + "b" * 64,
            "WEAVE_MCP_IMAGE": "sha256:" + "c" * 64,
            "WEAVE_KEYCLOAK_IMAGE": "sha256:" + "d" * 64,
        }
        previous_local_images = {key: os.environ.get(key) for key in local_images}
        try:
            os.environ.update(local_images)
            local_dogfood = load_context(
                "dogfood",
                ROOT,
                str(_materialize_example("dogfood", temporary_root / "dogfood-local.env")),
            )
            assert local_dogfood.env["WEAVE_BACKEND_IMAGE"] == local_images["WEAVE_BACKEND_IMAGE"]
        finally:
            for key, value in previous_local_images.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value

        mutable_path = _materialize_example(
            "dogfood", temporary_root / "dogfood-mutable.env"
        )
        mutable_path.write_text(
            re.sub(
                r"^WEAVE_BACKEND_IMAGE=.*$",
                "WEAVE_BACKEND_IMAGE=weave-backend:dogfood",
                mutable_path.read_text(encoding="utf-8"),
                flags=re.MULTILINE,
            ),
            encoding="utf-8",
        )
        try:
            load_context("dogfood", ROOT, str(mutable_path))
        except ContractError:
            pass
        else:
            raise AssertionError("dogfood accepted a mutable application image tag")

        wrong_reset_boundary = _materialize_example(
            "dogfood", temporary_root / "dogfood-wrong-reset-boundary.env"
        )
        wrong_reset_boundary.write_text(
            re.sub(
                r"^WEAVE_DB_DATA_VOLUME=.*$",
                "WEAVE_DB_DATA_VOLUME=some_other_volume",
                wrong_reset_boundary.read_text(encoding="utf-8"),
                flags=re.MULTILINE,
            ),
            encoding="utf-8",
        )
        try:
            load_context("dogfood", ROOT, str(wrong_reset_boundary))
        except ContractError as error:
            assert "fixed reset boundary" in str(error)
        else:
            raise AssertionError("dogfood accepted an arbitrary reset volume")

    assert dogfood.environment == "dogfood"
    assert e2e.environment == "e2e"
    assert e2e.env["WEAVE_STACK_SCOPE"] == "isolated"
    assert dev.environment == "dev"
    assert prod.environment == "prod"
    assert dev_tools.active_profiles == ("dev", "dev-tools")
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
    assert (
        'if context.environment == "prod":\n'
        '            require_completed_migration(context)'
        in runtime_source
    )
    assert 'if context.environment in {"e2e", "prod"}:' not in runtime_source

    shell_source = (ROOT / "compose.sh").read_text(encoding="utf-8")
    assert "<dev|dogfood|prod|e2e>" in shell_source
    assert "deprecated CI-only compatibility selector" not in shell_source
    assert "test" not in shell_source

    repository = ROOT.parents[1]
    for workflow_name in ("test-stack-deploy.yml", "human-testing-readiness.yml"):
        workflow_path = repository / ".github/workflows" / workflow_name
        if not workflow_path.is_file():
            continue
        workflow = workflow_path.read_text(encoding="utf-8")
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
    assert "WEAVE_MAILPIT_URL=https://mail.weave.test:44443" in dogfood_example
    assert "WEAVE_MAILPIT_REQUIRE_TLS=true" in dogfood_example
    assert "WEAVE_SMTP_" not in dogfood_example

    print("operator environment contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
