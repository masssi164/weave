#!/usr/bin/env python3
"""Validate Compose/Spring profile ownership after renderer modularization."""

from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
import time
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = ROOT.parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import ContractError, load_context  # noqa: E402
import compose_runtime as compose_runtime_module  # noqa: E402
from compose_runtime import active_volume_keys  # noqa: E402
from render_config import _reset_provider_configtree, _runtime_policy  # noqa: E402
from rendering.gateway import _site, render_caddy  # noqa: E402
from rendering.keycloak import (  # noqa: E402
    _desired,
    _image_digest,
    _overlay,
    _realm_definition_identity,
    _receipt_check_environment,
)


def materialize_example(profile: str, destination: Path) -> Path:
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
    if profile in {"dogfood", "e2e"} and "WEAVE_MAILPIT_URL=" not in value:
        value += "\nWEAVE_MAILPIT_URL=https://mail.weave.test\n"
    destination.write_text(value, encoding="utf-8")
    os.chmod(destination, 0o600)
    return destination


def assert_spring_profile_contract(profile: str) -> None:
    server = (
        REPOSITORY_ROOT / f"server/src/main/resources/application-{profile}.yml"
    ).read_text(encoding="utf-8")
    mcp = (
        REPOSITORY_ROOT
        / f"weave-mcp-server/src/main/resources/application-{profile}.yml"
    ).read_text(encoding="utf-8")
    assert f"on-profile: {profile}" in server
    assert f"on-profile: {profile}" in mcp
    assert "issuer-uri:" in server
    assert "issuer-uri:" in mcp
    assert "datasource:" not in mcp
    assert "jpa:" not in mcp


def assert_dogfood_application_image_platform_contract() -> None:
    lifecycle = (ROOT / "scripts/dogfood_lifecycle.py").read_text(encoding="utf-8")
    assert 'DOGFOOD_APPLICATION_PLATFORM = "linux/amd64"' in lifecycle
    assert lifecycle.count('"--platform",\n            DOGFOOD_APPLICATION_PLATFORM,') == 2
    assert '["git", "show", "-s", "--format=%cI", commit]' in lifecycle
    assert "datetime.now" not in lifecycle
    server_dependencies = (
        REPOSITORY_ROOT / "server/gradle/scripts/java-and-dependencies.gradle"
    ).read_text(encoding="utf-8")
    assert "requestedTask.tokenize(':').last() in ['dogfoodUp', 'dogfoodReset']" in server_dependencies
    assert "dogfoodLifecycleRequested ? 'linux-x86_64' : null" in server_dependencies


def assert_realm_definition_identity_contract() -> None:
    revision = "sha256:" + "1" * 64
    baseline = {"provenance": {"baselineRevision": revision}}
    compact = {"schemaVersion": "v1", "operations": [{"id": "one"}]}
    reordered = {"operations": [{"id": "one"}], "schemaVersion": "v1"}

    semantic_digest, migration_digest = _realm_definition_identity(baseline, compact)
    reordered_semantic, reordered_migration = _realm_definition_identity(
        baseline, reordered
    )

    assert semantic_digest == revision
    assert reordered_semantic == revision
    assert migration_digest == reordered_migration


def assert_native_collaboration_restart_is_bounded(context) -> None:
    original_snapshot = compose_runtime_module._service_snapshot
    original_compose = compose_runtime_module._bounded_collaboration_compose
    original_healthy = compose_runtime_module._await_healthy
    deadlines: list[float] = []
    restarted_services: list[str] = []

    def snapshot(_context, service, *, include_stopped=False, deadline=None):
        del include_stopped
        assert service in {"postgres", "keycloak", "backend"}
        assert deadline is not None
        deadlines.append(deadline)
        container_ids = {
            "postgres": "a" * 64,
            "keycloak": "c" * 64,
            "backend": "b" * 64,
        }
        return {
            "containerId": container_ids[service],
            "startedAt": "before",
            "restartCount": 0,
            "running": True,
            "health": "healthy",
        }

    def bounded_compose(_context, deadline, *arguments, capture=False):
        del capture
        assert arguments[0] == "restart"
        deadlines.append(deadline)
        restarted_services.append(arguments[-1])
        return subprocess.CompletedProcess(arguments, 0)

    def healthy(_context, service, deadline=None):
        assert deadline is not None
        deadlines.append(deadline)
        container_ids = {
            "postgres": "a" * 64,
            "keycloak": "c" * 64,
            "backend": "b" * 64,
        }
        return {
            "containerId": container_ids[service],
            "startedAt": "after",
            "restartCount": 1,
            "running": True,
            "health": "healthy",
        }

    compose_runtime_module._service_snapshot = snapshot
    compose_runtime_module._bounded_collaboration_compose = bounded_compose
    compose_runtime_module._await_healthy = healthy
    try:
        compose_runtime_module.isolated_collaboration_control(
            context, "restart-collaboration"
        )
    finally:
        compose_runtime_module._service_snapshot = original_snapshot
        compose_runtime_module._bounded_collaboration_compose = original_compose
        compose_runtime_module._await_healthy = original_healthy
    assert len(deadlines) == 9
    assert restarted_services == ["postgres", "keycloak", "backend"]
    assert len(set(deadlines)) == 1
    assert time.monotonic() < deadlines[0] <= (
        time.monotonic()
        + compose_runtime_module.COLLABORATION_CONTROL_BUDGET_SECONDS
    )


def assert_dogfood_reset_is_exact(context) -> None:
    original_compose = compose_runtime_module.compose
    original_remove = compose_runtime_module._remove_exact_dogfood_resource
    original_retired_cleanup = compose_runtime_module.cleanup_retired_dogfood
    original_preflight = compose_runtime_module.preflight_dogfood_reset
    original_execute = compose_runtime_module.execute
    compose_calls: list[tuple[str, ...]] = []
    removed: list[tuple[str, str]] = []
    execute_calls: list[tuple[str, tuple[str, ...]]] = []
    retired_cleanup_calls = 0
    preflight_calls = 0

    def fake_compose(_context, *arguments, capture=False):
        del capture
        compose_calls.append(arguments)
        return subprocess.CompletedProcess(arguments, 0)

    def fake_remove(kind, name):
        removed.append((kind, name))

    def fake_execute(_context, command, extra):
        execute_calls.append((command, tuple(extra)))

    def fake_retired_cleanup():
        nonlocal retired_cleanup_calls
        retired_cleanup_calls += 1

    def fake_preflight(_context):
        nonlocal preflight_calls
        preflight_calls += 1

    compose_runtime_module.compose = fake_compose
    compose_runtime_module._remove_exact_dogfood_resource = fake_remove
    compose_runtime_module.cleanup_retired_dogfood = fake_retired_cleanup
    compose_runtime_module.preflight_dogfood_reset = fake_preflight
    compose_runtime_module.execute = fake_execute
    try:
        compose_runtime_module.reset_dogfood(context)
        invalid = replace(
            context,
            env={**context.env, "WEAVE_COMPOSE_PROJECT": "unexpected-project"},
        )
        try:
            compose_runtime_module.reset_dogfood(invalid)
        except ContractError:
            pass
        else:
            raise AssertionError("dogfood reset accepted an unexpected Compose project")
    finally:
        compose_runtime_module.compose = original_compose
        compose_runtime_module._remove_exact_dogfood_resource = original_remove
        compose_runtime_module.cleanup_retired_dogfood = original_retired_cleanup
        compose_runtime_module.preflight_dogfood_reset = original_preflight
        compose_runtime_module.execute = original_execute

    assert compose_calls == [("down", "--volumes", "--remove-orphans")]
    assert removed == [
        ("volume", context.env["WEAVE_DB_DATA_VOLUME"]),
        ("volume", context.env["WEAVE_NATIVE_FILES_DATA_VOLUME"]),
        ("volume", context.env["WEAVE_MAILPIT_DATA_VOLUME"]),
        ("network", context.env["WEAVE_DOCKER_NETWORK"]),
    ]
    assert all(str(context.tls_root) not in name for _, name in removed)
    assert retired_cleanup_calls == 1
    assert preflight_calls == 1
    assert execute_calls == [("up", ())]


def assert_retired_cleanup_is_bounded() -> None:
    original_inspect = compose_runtime_module._inspect_retired_resource
    original_run = compose_runtime_module.subprocess.run
    mutations: list[tuple[str, ...]] = []

    def fake_inspect(kind, name):
        if (kind, name) == ("container", "weave-backend"):
            return {
                "Config": {"Labels": {}},
                "NetworkSettings": {"Networks": {"weave_network": {}}},
                "Mounts": [],
            }
        if (kind, name) == ("volume", "weave_db_data"):
            return {"Labels": {}}
        if (kind, name) == ("network", "weave_network"):
            return {"Labels": {}, "Containers": {"id": {"Name": "weave-backend"}}}
        return None

    def fake_run(command, **kwargs):
        del kwargs
        command_tuple = tuple(command)
        if command_tuple[:3] == ("docker", "container", "ls"):
            return subprocess.CompletedProcess(command, 0, stdout="weave-backend\n")
        mutations.append(command_tuple)
        return subprocess.CompletedProcess(command, 0, stdout="")

    compose_runtime_module._inspect_retired_resource = fake_inspect
    compose_runtime_module.subprocess.run = fake_run
    try:
        compose_runtime_module.cleanup_retired_dogfood()
    finally:
        compose_runtime_module._inspect_retired_resource = original_inspect
        compose_runtime_module.subprocess.run = original_run

    assert mutations == [
        ("docker", "container", "rm", "--force", "weave-backend"),
        ("docker", "volume", "rm", "weave_db_data"),
        ("docker", "network", "rm", "weave_network"),
    ]
    serialized = repr(mutations)
    assert "tls" not in serialized and "/Users/" not in serialized


def main() -> int:
    assert compose_runtime_module.COLLABORATION_CONTROL_BUDGET_SECONDS == 240
    assert compose_runtime_module.COLLABORATION_SUBPROCESS_TIMEOUT_SECONDS == 30
    assert_dogfood_application_image_platform_contract()
    assert_realm_definition_identity_contract()
    assert_retired_cleanup_is_bounded()
    compose_source = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert compose_source.count(
        "SPRING_PROFILES_ACTIVE: ${WEAVE_ENVIRONMENT:?environment required}"
    ) == 3
    assert "/backend/public.env" not in compose_source
    assert "/backend/host.env" not in compose_source
    assert "/mcp/public.env" not in compose_source
    assert "/mcp/host.env" not in compose_source
    dogfood_overlay = (ROOT / "compose.dogfood.yaml").read_text(encoding="utf-8")
    assert "runtime-state-volume-init:" not in dogfood_overlay
    assert "runtime-state-init:" not in dogfood_overlay
    assert "runtime-state-s3-access-key" not in dogfood_overlay
    assert "runtime-state-s3-secret-key" not in dogfood_overlay
    assert "${WEAVE_TLS_ROOT:?reviewed external TLS root required}" in dogfood_overlay
    assert "caddy-data:/data" not in dogfood_overlay
    assert "keycloak-data:/opt/keycloak/data" not in dogfood_overlay
    assert "x-weave-dogfood-session-labels" in dogfood_overlay
    assert "com.massimotter.weave.scope: resettable-session" in dogfood_overlay
    session_resources = dogfood_overlay.split("volumes:\n", 1)[1]
    assert "WEAVE_CANDIDATE_COMMIT" not in session_resources
    assert "WEAVE_CANDIDATE_MANIFEST_DIGEST" not in session_resources

    for profile in ("dev", "dogfood", "e2e", "prod"):
        assert_spring_profile_contract(profile)

    # Dev deliberately runs Server/MCP as host processes after provider convergence.
    dev = load_context("dev", ROOT)
    try:
        _image_digest(dev)
    except ContractError:
        pass
    else:
        raise AssertionError("dev renderer invented a digest from a mutable version tag")

    assert _site("https://api.weave.test:44443/api") == "https://api.weave.test"
    try:
        _site("http://api.weave.test")
    except ContractError:
        pass
    else:
        raise AssertionError("gateway accepted an insecure public origin")

    canonical = {
        "apiVersion": "weave.keycloak-desired-state/v3",
        "keycloakVersion": "26.7.1",
        "environment": "test",
        "revision": "",
        "clientPolicies": [{"key": "policy:weaver-cell-registration"}],
        "provenance": {"overlayRevision": ""},
        "realm": {"adminPermissionsEnabled": True, "frontendUrl": "", "smtp": {}},
        "organizations": [{"key": "organization:weave-primary", "alias": "weave"}],
        "clientScopes": [],
        "organizationGroups": [
            {"key": "owner", "organizationRef": "organization:weave-primary", "path": "/owners", "parentGroupRef": None, "roleRefs": ["role:owner"]},
            {"key": "admin", "organizationRef": "organization:weave-primary", "path": "/admins", "parentGroupRef": None, "roleRefs": ["role:admin"]},
            {"key": "member", "organizationRef": "organization:weave-primary", "path": "/members", "parentGroupRef": None, "roleRefs": ["role:member"]},
            {"key": "guest", "organizationRef": "organization:weave-primary", "path": "/guests", "parentGroupRef": None, "roleRefs": ["role:guest"]},
            {"key": "capabilities", "organizationRef": "organization:weave-primary", "path": "/capabilities", "parentGroupRef": None, "roleRefs": []},
            {"key": "weaver", "organizationRef": "organization:weave-primary", "path": "/capabilities/weaver", "parentGroupRef": "organization-group:weave-primary:capabilities", "roleRefs": []},
        ],
        "fineGrainedAdminPermissions": {"enabled": True},
        "serviceAccountRoleGrants": [
            {
                "clientKey": "client:weave-identity-admin",
                "roleRefs": [
                    "builtin-role:realm-management:query-organizations",
                    "builtin-role:realm-management:query-users",
                ],
            }
        ],
    }
    overlay = {
        "publicUrls": {
            "api": "https://api.weave.test:9443/api",
            "auth": "https://auth.weave.local",
            "weave": "https://weave.local",
        },
        "environment": "dev",
        "revision": "sha256:overlay",
        "smtpEndpoints": {"host": "mailpit", "port": 1025},
        "organizationMetadata": {
            "name": "Weave",
            "alias": "weave",
            "description": "Local",
            "redirectUri": "https://weave.local",
        },
    }
    rendered = _desired(canonical, overlay)
    assert "groups" not in rendered
    assert rendered["realm"]["smtp"] == {"host": "mailpit", "port": 1025}
    assert "smtpServer" not in rendered["realm"]
    try:
        _desired({**canonical, "groups": []}, overlay)
    except ContractError:
        pass
    else:
        raise AssertionError("renderer accepted legacy realm groups")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        provider_configtree = root / "provider-configtree"
        provider_configtree.mkdir()
        for name in (
            "matrix-as-token",
            "matrix-hs-token",
            "weave.nextcloud.files.actor-token",
        ):
            (provider_configtree / name).write_text("stale\n", encoding="utf-8")
        _reset_provider_configtree(provider_configtree)
        assert not tuple(provider_configtree.iterdir())

        dogfood = load_context(
            "dogfood", ROOT, str(materialize_example("dogfood", root / "dogfood.env"))
        )
        prod = load_context(
            "prod", ROOT, str(materialize_example("prod", root / "prod.env"))
        )
        assert _image_digest(dogfood) == "sha256:" + "a" * 64
        assert active_volume_keys(dogfood) == (
            "WEAVE_DB_DATA_VOLUME",
            "WEAVE_NATIVE_FILES_DATA_VOLUME",
            "WEAVE_MAILPIT_DATA_VOLUME",
        )
        assert_dogfood_reset_is_exact(dogfood)
        assert _image_digest(prod) == "sha256:" + "a" * 64
        assert _overlay(dogfood, "sha256:" + "b" * 64)["smtpEndpoints"]["host"] == "mailpit"
        dogfood_caddy = render_caddy(dogfood)
        assert "@internal path /api/internal/* /actuator/*" in dogfood_caddy
        assert "reverse_proxy mailpit:8025" in dogfood_caddy
        assert "reverse_proxy mailpit:8025" not in render_caddy(prod)

        isolated_overrides = {
            "WEAVE_E2E_STACK_SCOPE": "isolated",
            "WEAVE_E2E_RUN_ID": "compose-profile-contract",
            "WEAVE_BACKEND_IMAGE": "sha256:" + "b" * 64,
            "WEAVE_MCP_IMAGE": "sha256:" + "b" * 64,
            "WEAVE_KEYCLOAK_IMAGE": "sha256:" + "b" * 64,
        }
        previous = {key: os.environ.get(key) for key in isolated_overrides}
        try:
            os.environ.update(isolated_overrides)
            isolated = load_context(
                "e2e", ROOT, str(materialize_example("e2e", root / "e2e.env"))
            )
            assert isolated.env["WEAVE_STACK_SCOPE"] == "isolated"
            assert _image_digest(isolated) == "sha256:" + "b" * 64
            network_labels = compose_runtime_module.labels(
                isolated, "network", isolated.env["WEAVE_DOCKER_NETWORK"]
            )
            assert network_labels["com.docker.compose.network"] == "weave"
            assert (
                network_labels["com.docker.compose.project"]
                == isolated.env["WEAVE_COMPOSE_PROJECT"]
            )
            assert _runtime_policy(isolated)["sandbox"]["allowedNetworkTargets"] == [
                "api.weave.test"
            ]
            receipt_environment = _receipt_check_environment(
                isolated,
                manifest_digest="sha256:" + "1" * 64,
                baseline_digest="sha256:" + "2" * 64,
                migration_bundle={
                    "toBaselineRevision": "sha256:" + "3" * 64,
                    # This deliberately differs from the migration target. A
                    # desired-state document revision is not the semantic
                    # baseline revision bound by the receipt contract.
                    "desiredStateRevision": "sha256:" + "4" * 64,
                },
            )
            assert set(receipt_environment.splitlines()) == {
                "WEAVE_KEYCLOAK_MIGRATION_BASELINE_DIGEST=sha256:" + "2" * 64,
                "WEAVE_KEYCLOAK_MIGRATION_CANDIDATE_COMMIT="
                + isolated.env["WEAVE_CANDIDATE_COMMIT"],
                "WEAVE_KEYCLOAK_MIGRATION_COMPOSE_PROJECT="
                + isolated.env["WEAVE_COMPOSE_PROJECT"],
                "WEAVE_KEYCLOAK_MIGRATION_ENVIRONMENT=e2e",
                "WEAVE_KEYCLOAK_MIGRATION_MANIFEST_DIGEST=sha256:" + "1" * 64,
                "WEAVE_KEYCLOAK_MIGRATION_TARGET_REVISION=sha256:" + "3" * 64,
            }
            assert_native_collaboration_restart_is_bounded(isolated)
        finally:
            for key, value in previous.items():
                if value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = value

    print("compose profile modular contract tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
