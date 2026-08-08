#!/usr/bin/env python3
"""Executable contract checks for the public Compose environment boundary."""

from __future__ import annotations

import os
import re
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import (  # noqa: E402
    ContractError,
    OPERATOR_ENVIRONMENTS,
    load_context,
)
from compose_runtime import RUNTIME_ROOT_SERVICES, runtime_root_services  # noqa: E402


@contextmanager
def process_environment(**updates: str):
    preserved = {
        name: os.environ[name]
        for name in ("HOME", "PATH", "TMPDIR")
        if name in os.environ
    }
    preserved.update(updates)
    with mock.patch.dict(os.environ, preserved, clear=True):
        yield


def materialize_example(environment: str, destination: Path) -> Path:
    source = ROOT / "environments" / f"{environment}.env.example"
    rendered = re.sub(
        r"@sha256:replace-with-[^\n]+",
        "@sha256:" + ("a" * 64),
        source.read_text(encoding="utf-8"),
    )
    destination.write_text(rendered, encoding="utf-8")
    return destination


def expect_contract_error(operation, expected: str) -> None:
    try:
        operation()
    except ContractError as error:
        assert expected in str(error), (expected, str(error))
    else:
        raise AssertionError(f"expected ContractError containing {expected!r}")


def main() -> int:
    assert OPERATOR_ENVIRONMENTS == ("dev", "dogfood", "prod", "e2e")
    assert "test" not in OPERATOR_ENVIRONMENTS

    expect_contract_error(
        lambda: load_context("feature/compose-cleanup", ROOT),
        "environment must be one of: dev, dogfood, prod, e2e",
    )

    with tempfile.TemporaryDirectory() as directory:
        temporary = Path(directory)
        dogfood_env = materialize_example("dogfood", temporary / "dogfood.env")
        prod_env = materialize_example("prod", temporary / "prod.env")
        e2e_env = materialize_example("e2e", temporary / "e2e.env")

        with process_environment():
            dogfood = load_context("dogfood", ROOT, str(dogfood_env))
        assert dogfood.environment == "dogfood"
        assert dogfood.profile == "dogfood"
        assert [path.name for path in dogfood.compose_files] == [
            "compose.yaml",
            "compose.dogfood.yaml",
        ]
        assert dogfood.env["WEAVE_RESOURCE_ENVIRONMENT"] == "dogfood"
        assert dogfood.env["WEAVE_DEPLOYMENT_SCOPE"] == "dogfood"
        assert dogfood.env["WEAVE_COMPOSE_PROJECT"] == "weave-dogfood"
        assert dogfood.compose_base_command.count("--env-file") == 2
        assert str(dogfood_env.resolve()) in dogfood.compose_base_command

        for environment, source in (("dogfood", dogfood_env), ("prod", prod_env)):
            invalid_optional = temporary / f"{environment}-dev-tools.env"
            invalid_optional.write_text(
                source.read_text(encoding="utf-8").replace(
                    f"COMPOSE_PROFILES={environment}",
                    f"COMPOSE_PROFILES={environment},dev-tools",
                ),
                encoding="utf-8",
            )
            with process_environment():
                expect_contract_error(
                    lambda environment=environment, invalid_optional=invalid_optional: load_context(
                        environment, ROOT, str(invalid_optional)
                    ),
                    "optional selections must exactly match",
                )

        expect_contract_error(
            lambda: load_context("e2e", ROOT, str(e2e_env)),
            "e2e requires WEAVE_E2E_STACK_SCOPE=isolated",
        )

        with process_environment(
            WEAVE_E2E_STACK_SCOPE="isolated",
            WEAVE_E2E_RUN_ID="contract-run-001",
        ):
            e2e = load_context("e2e", ROOT, str(e2e_env))
        assert e2e.environment == "e2e"
        assert e2e.profile == "e2e"
        assert [path.name for path in e2e.compose_files] == [
            "compose.yaml",
            "compose.e2e.yaml",
        ]
        assert e2e.env["WEAVE_RESOURCE_ENVIRONMENT"] == "e2e"
        assert e2e.env["WEAVE_DEPLOYMENT_SCOPE"] == "isolated-e2e"
        assert e2e.env["WEAVE_STACK_SCOPE"] == "isolated"
        assert e2e.env["WEAVE_COMPOSE_PROJECT"].startswith("weave-e2e-")
        assert e2e.env["WEAVE_COMPOSE_PROJECT"] != dogfood.env["WEAVE_COMPOSE_PROJECT"]
        assert e2e.generated_root != dogfood.generated_root

        # Runtime helpers receive the already-normalized public context and
        # re-enter through the explicit E2E selector. Docker-assigned zero
        # ports must remain valid across that second normalization.
        with process_environment(**e2e.env):
            inner_e2e = load_context("e2e", ROOT, str(e2e_env))
        assert inner_e2e.environment == "e2e"
        assert inner_e2e.env["WEAVE_PROXY_HTTPS_HOST_PORT"] == "0"

        with process_environment(
            WEAVE_E2E_STACK_SCOPE="isolated",
            WEAVE_E2E_RUN_ID="contract-run-002",
        ):
            second_e2e = load_context("e2e", ROOT, str(e2e_env))
        assert second_e2e.env["WEAVE_COMPOSE_PROJECT"] != e2e.env["WEAVE_COMPOSE_PROJECT"]
        assert second_e2e.generated_root != e2e.generated_root

        with process_environment(
            WEAVE_E2E_STACK_SCOPE="isolated",
            WEAVE_E2E_RUN_ID="contract-run-003",
        ):
            expect_contract_error(
                lambda: load_context("dogfood", ROOT, str(dogfood_env)),
                "isolated E2E uses the e2e environment",
            )

        legacy_value = temporary / "mismatched-environment.env"
        legacy_value.write_text(
            dogfood_env.read_text(encoding="utf-8").replace(
                "WEAVE_ENVIRONMENT=dogfood", "WEAVE_ENVIRONMENT=e2e"
            ),
            encoding="utf-8",
        )
        with process_environment():
            expect_contract_error(
                lambda: load_context("dogfood", ROOT, str(legacy_value)),
                "expected dogfood",
            )

    dev = load_context("dev", ROOT)
    assert dev.environment == "dev"
    assert dev.profile == "dev"
    assert RUNTIME_ROOT_SERVICES["dev"] == ("keycloak",)
    assert RUNTIME_ROOT_SERVICES["dogfood"] == ("caddy", "mailpit", "mcp")
    assert runtime_root_services(dev) == ("keycloak",)
    with tempfile.TemporaryDirectory() as directory:
        dev_tools_env = Path(directory) / "dev-tools.env"
        dev_tools_env.write_text(
            dev.profile_env_file.read_text(encoding="utf-8").replace(
                "COMPOSE_PROFILES=dev", "COMPOSE_PROFILES=dev,dev-tools"
            ),
            encoding="utf-8",
        )
        dev_tools = load_context("dev", ROOT, str(dev_tools_env))
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

    shell_source = (ROOT / "compose.sh").read_text(encoding="utf-8")
    assert "<dev|dogfood|prod|e2e>" in shell_source
    assert "deprecated CI-only compatibility selector" not in shell_source
    assert "test" not in shell_source

    repository = ROOT.parents[1]
    for workflow_name in ("test-stack-deploy.yml", "human-testing-readiness.yml"):
        workflow = (repository / ".github/workflows" / workflow_name).read_text(
            encoding="utf-8"
        )
        assert 'load_context("test"' not in workflow
        assert "./compose.sh test " not in workflow
        assert "./operator-check.sh test" not in workflow
        assert "./install.sh test" not in workflow
    for module in ("server", "weave-mcp-server"):
        resource_root = repository / module / "src/main/resources"
        for environment in ("dogfood", "e2e"):
            profile = (resource_root / f"application-{environment}.yml").read_text(
                encoding="utf-8"
            )
            assert f"on-profile: {environment}" in profile
            assert f"profile: {environment}" in profile
            assert "org.postgresql.Driver" in profile

    dogfood_example = (ROOT / "environments/dogfood.env.example").read_text(
        encoding="utf-8"
    )
    assert "WEAVE_MAILPIT_IMAGE=" in dogfood_example
    assert "WEAVE_MAILPIT_DATA_VOLUME=" in dogfood_example
    assert "WEAVE_MAILPIT_REQUIRE_TLS=true" in dogfood_example
    assert "WEAVE_SMTP_" not in dogfood_example

    print("operator environment contract: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
