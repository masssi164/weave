#!/usr/bin/env python3
"""Contract tests for branch-independent dev/test/prod Compose profiles."""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys

sys.path.insert(0, str(ROOT / "scripts"))
import compose_runtime as compose_runtime_module  # noqa: E402
from compose_env import ContractError, load_context  # noqa: E402
from compose_runtime import (  # noqa: E402
    AGENT_RUNTIME_ROOT,
    PROFILE_SIGNING_TARGET,
    RESOURCE_METADATA,
    STATE_WRAPPING_TARGET,
    WORKLOADS_TARGET,
    compose,
    labels,
    normalized_mount_graph,
    preflight_protected_sources,
    resource_labels_match,
    validate_mount_contract,
)
from render_config import _backend_env, _image_digest, _render_desired  # noqa: E402


def materialize_example(profile: str, destination: Path) -> Path:
    source = ROOT / f"environments/{profile}.env.example"
    value = re.sub(r"sha256:replace-with-[a-zA-Z0-9.-]+", "sha256:" + "a" * 64, source.read_text())
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


def resolved_model(context) -> dict[str, object]:
    for name in ("backend", "mcp"):
        root = context.generated_root / name
        root.mkdir(parents=True, exist_ok=True)
        (root / "public.env").touch(mode=0o600)
    result = compose(context, "config", "--format", "json", capture=True)
    return json.loads(result.stdout)


def assert_long_running_services_reap_child_processes(model: dict[str, object]) -> None:
    services = model["services"]
    long_running = {
        name
        for name, service in services.items()
        if service.get("restart") != "no"
        and service.get("labels", {}).get("com.massimotter.weave.one-shot") != "true"
    }
    assert long_running
    assert {
        service
        for service in long_running
        if services[service].get("init") is not True
    } == set()


def expect_contract_rejection(action, message: str) -> None:
    try:
        action()
    except ContractError:
        return
    raise AssertionError(message)


def assert_identity_bootstrap_lifecycle(context) -> None:
    original_prepare = compose_runtime_module.prepare
    original_compose = compose_runtime_module.compose
    original_probe = compose_runtime_module.bootstrap_authority_available
    calls: list[tuple[str, ...]] = []

    def record_compose(_context, *arguments: str, **_kwargs):
        calls.append(arguments)
        return None

    compose_runtime_module.prepare = lambda _context: None
    compose_runtime_module.compose = record_compose
    compose_runtime_module.bootstrap_authority_available = lambda _context: True
    try:
        compose_runtime_module.identity_ops(context, "identity-plan")
    finally:
        compose_runtime_module.prepare = original_prepare
        compose_runtime_module.compose = original_compose
        compose_runtime_module.bootstrap_authority_available = original_probe
    assert calls == [
        ("up", "-d", "--wait", "keycloak"),
        ("run", "--rm", "--no-deps", "identity-ops", "plan"),
    ]

    calls.clear()
    probe_results = iter((False, True))
    compose_runtime_module.prepare = lambda _context: None
    compose_runtime_module.compose = record_compose
    compose_runtime_module.bootstrap_authority_available = (
        lambda _context: next(probe_results)
    )
    try:
        compose_runtime_module.identity_ops(context, "identity-plan")
    finally:
        compose_runtime_module.prepare = original_prepare
        compose_runtime_module.compose = original_compose
        compose_runtime_module.bootstrap_authority_available = original_probe
    assert calls == [
        ("up", "-d", "--wait", "keycloak"),
        ("stop", "keycloak"),
        (
            "run",
            "--rm",
            "--no-deps",
            "keycloak",
            "bootstrap-admin",
            "service",
            "--client-id",
            "weave-identity-ops-bootstrap",
            "--client-secret:env=WEAVE_IDENTITY_OPS_BOOTSTRAP_SECRET",
            "--no-prompt",
        ),
        ("up", "-d", "--wait", "keycloak"),
        ("run", "--rm", "--no-deps", "identity-ops", "plan"),
    ]


def assert_identity_bootstrap_authority_probe(context, root: Path) -> None:
    secret_root = root / "bootstrap-probe"
    secret_root.mkdir(mode=0o700)
    credential = secret_root / "keycloak-bootstrap-admin-password"
    credential.write_text("test-bootstrap-secret\n", encoding="utf-8")
    os.chmod(credential, 0o600)
    probe_context = replace(
        context,
        env={
            **context.env,
            "WEAVE_SECRET_ROOT": str(secret_root),
            "WEAVE_KEYCLOAK_HOST_PORT": "49181",
        },
    )
    original_urlopen = compose_runtime_module.urllib.request.urlopen
    captured: dict[str, str] = {}

    class Response:
        status = 200

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

        def read(self, _limit: int) -> bytes:
            return b'{"access_token":"test-only"}'

    def accepted(request, **_kwargs):
        captured.update(
            urllib.parse.parse_qsl(request.data.decode("ascii"))
        )
        return Response()

    compose_runtime_module.urllib.request.urlopen = accepted
    try:
        assert compose_runtime_module.bootstrap_authority_available(probe_context)
    finally:
        compose_runtime_module.urllib.request.urlopen = original_urlopen
    assert captured == {
        "grant_type": "client_credentials",
        "client_id": "weave-identity-ops-bootstrap",
        "client_secret": "test-bootstrap-secret",
    }

    def rejected(_request, **_kwargs):
        raise urllib.error.HTTPError(
            "http://127.0.0.1:49181/token",
            401,
            "Unauthorized",
            {},
            io.BytesIO(b'{"error":"invalid_client"}'),
        )

    compose_runtime_module.urllib.request.urlopen = rejected
    try:
        assert not compose_runtime_module.bootstrap_authority_available(
            probe_context
        )
    finally:
        compose_runtime_module.urllib.request.urlopen = original_urlopen

    os.chmod(credential, 0o640)
    expect_contract_rejection(
        lambda: compose_runtime_module.bootstrap_authority_available(
            probe_context
        ),
        "mode-0640 bootstrap SecretRef was accepted",
    )


def assert_collaboration_control_is_bounded(context) -> None:
    original_run = compose_runtime_module.run_bounded
    private_coordinate = "/private/runner/secret/docker.sock"

    def timed_out(*_args, **_kwargs):
        raise compose_runtime_module.BoundedProcessTimeout(
            "fixture output must not escape"
        )

    compose_runtime_module.run_bounded = timed_out
    try:
        try:
            compose_runtime_module._bounded_collaboration_run(
                ["docker", "container", "inspect", private_coordinate],
                context,
                time.monotonic() + 5,
                capture=True,
            )
        except ContractError as error:
            message = str(error)
            assert message == (
                "collaboration service control Docker operation exceeded its bounded timeout"
            )
            assert "docker" not in message
            assert private_coordinate not in message
        else:
            raise AssertionError("timed-out collaboration Docker call was accepted")
    finally:
        compose_runtime_module.run_bounded = original_run

    original_snapshot = compose_runtime_module._service_snapshot
    original_compose = compose_runtime_module._bounded_collaboration_compose
    original_healthy = compose_runtime_module._await_healthy
    deadlines: list[float] = []

    def snapshot(_context, service, *, include_stopped=False, deadline=None):
        del include_stopped
        assert service in {"synapse", "backend"}
        assert deadline is not None
        deadlines.append(deadline)
        return {
            "containerId": "a" * 64 if service == "synapse" else "b" * 64,
            "startedAt": "before",
            "restartCount": 0,
            "running": True,
            "health": "healthy",
        }

    def bounded_compose(_context, deadline, *arguments, capture=False):
        del capture
        assert arguments[0] == "restart"
        deadlines.append(deadline)
        return subprocess.CompletedProcess(arguments, 0)

    def healthy(_context, service, deadline=None):
        assert deadline is not None
        deadlines.append(deadline)
        return {
            "containerId": "a" * 64 if service == "synapse" else "b" * 64,
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
    assert len(deadlines) == 6
    assert len(set(deadlines)) == 1
    assert time.monotonic() < deadlines[0] <= (
        time.monotonic()
        + compose_runtime_module.COLLABORATION_CONTROL_BUDGET_SECONDS
    )


def assert_agent_runtime_mount_boundary(model: dict[str, object]) -> None:
    graph = validate_mount_contract(model)
    backend = {
        entry["target"]: entry
        for entry in graph
        if entry["service"] == "backend"
        and (
            entry["target"] == str(AGENT_RUNTIME_ROOT)
            or str(AGENT_RUNTIME_ROOT) + "/" in entry["target"]
        )
    }
    assert set(backend) == {
        str(WORKLOADS_TARGET),
        str(PROFILE_SIGNING_TARGET),
        str(STATE_WRAPPING_TARGET),
    }
    assert backend[str(WORKLOADS_TARGET)]["access"] == "read-write"
    assert backend[str(PROFILE_SIGNING_TARGET)]["access"] == "read-only"
    assert backend[str(STATE_WRAPPING_TARGET)]["access"] == "read-only"
    assert {
        entry["target"]
        for entry in graph
        if entry["service"] == "backend"
        and "weave-identity-admin" in (entry["source"] + entry["target"])
    } == {
        "/run/secrets/weave/spring.security.oauth2.client.registration.weave-identity-admin.client-secret"
    }
    initializer = {
        entry["target"]: entry
        for entry in graph
        if entry["service"] == "agent-runtime-keys-init"
    }
    assert set(initializer) == {
        str(PROFILE_SIGNING_TARGET),
        str(STATE_WRAPPING_TARGET),
    }
    assert {entry["access"] for entry in initializer.values()} == {"read-write"}
    runtime_admin_entries = []
    for entry in graph:
        coordinate = entry["source"] + entry["target"]
        if "weave-agent-runtime-admin" in coordinate:
            runtime_admin_entries.append(entry)
        if entry["service"] in {
            "mcp",
            "mcp-secret-check",
            "mcp-keycloak-connectivity-check",
        }:
            assert "weave-identity-admin" not in coordinate
            assert "weave-backend-jwk" not in coordinate
            assert "/agent-runtime/workloads/" not in coordinate
    assert {
        (entry["service"], entry["target"], entry["access"])
        for entry in runtime_admin_entries
    } == {
        (
            "identity-ops",
            "/run/secrets/agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin",
            "read-only",
        )
    }


def assert_schema_init_boundary(model: dict[str, object]) -> None:
    services = model["services"]
    initializer = services["schema-init"]
    verifier = services["schema-receipt-check"]
    backend = services["backend"]
    assert initializer["image"] == backend["image"] == verifier["image"]
    assert initializer["command"] == ["schema-init"]
    assert verifier["command"] == ["schema-receipt-check"]
    assert initializer["restart"] == "no"
    assert verifier["restart"] == "no"
    assert set(initializer["networks"]) == {"weave"}
    assert verifier["network_mode"] == "none"
    assert initializer["depends_on"]["postgres-reconcile"]["condition"] == (
        "service_completed_successfully"
    )
    assert verifier["depends_on"]["schema-init"]["condition"] == (
        "service_completed_successfully"
    )
    assert backend["depends_on"]["schema-receipt-check"]["condition"] == (
        "service_completed_successfully"
    )
    graph = normalized_mount_graph(model)
    receipt_writers = {
        entry["service"]
        for entry in graph
        if entry["target"].startswith("/run/weave-schema-init")
        and entry["access"] == "read-write"
    }
    assert receipt_writers == {"schema-init"}
    assert {
        entry["service"]
        for entry in graph
        if entry["target"].startswith("/run/weave-schema-init")
        and entry["access"] == "read-only"
    } == {"schema-receipt-check"}
    assert {
        secret["source"]
        for secret in initializer.get("secrets", [])
    } == {"backend-db-password"}
    assert not verifier.get("secrets")


def assert_runtime_state_boundary(model: dict[str, object], runtime_uid: str, runtime_gid: str) -> None:
    services = model["services"]
    initializer = services["runtime-state-volume-init"]
    runtime = services["runtime-state"]
    bucket_initializer = services["runtime-state-init"]
    assert initializer["image"] == runtime["image"]
    assert initializer["user"] == "0:0"
    assert initializer["restart"] == "no"
    assert initializer["read_only"] is True
    assert initializer["network_mode"] == "none"
    assert initializer["cap_drop"] == ["ALL"]
    assert initializer["cap_add"] == ["CHOWN"]
    assert not initializer.get("secrets")
    assert runtime["user"] == f"{runtime_uid}:{runtime_gid}"
    assert bucket_initializer["user"] == f"{runtime_uid}:{runtime_gid}"
    assert bucket_initializer["cap_drop"] == ["ALL"]
    assert not bucket_initializer.get("cap_add")
    assert runtime["cap_drop"] == ["ALL"]
    assert not runtime.get("cap_add")
    assert runtime["depends_on"]["runtime-state-volume-init"]["condition"] == (
        "service_completed_successfully"
    )
    graph = normalized_mount_graph(model)
    volume_writers = {
        (entry["service"], entry["target"])
        for entry in graph
        if entry["source"] == "runtime-state-data"
        and entry["access"] == "read-write"
    }
    assert volume_writers == {
        ("runtime-state-volume-init", "/data"),
        ("runtime-state", "/data"),
    }


def assert_fresh_start_target_graph(
    model: dict[str, object], context: object
) -> None:
    def enabled(value: object) -> bool:
        return str(value).lower() == "true"

    allowlist = json.loads(
        (ROOT / "fresh-start-targets.json").read_text(encoding="utf-8")
    )
    identities = [
        (target["kind"], target["name"]) for target in allowlist["targets"]
    ]
    assert len(identities) == len(set(identities)), "duplicate Fresh Start target"
    actual = {
        (target["kind"], target["name"]): (
            target["component"],
            target["dataClass"],
        )
        for target in allowlist["targets"]
    }
    expected: dict[tuple[str, str], tuple[str, str]] = {}
    for service in model["services"].values():
        labels = service.get("labels", {})
        if (
            enabled(labels.get("com.massimotter.weave.managed"))
            and enabled(
                labels.get("com.massimotter.weave.fresh-start-eligible")
            )
        ):
            expected[("container", service["container_name"])] = (
                labels["com.massimotter.weave.component"],
                labels["com.massimotter.weave.data-class"],
            )
    for variable, (component, data_class) in RESOURCE_METADATA.items():
        expected[("volume", context.env[variable])] = (
            component,
            data_class,
        )
    expected[("network", context.env["WEAVE_DOCKER_NETWORK"])] = (
        "network",
        "connectivity",
    )
    assert actual == expected, {
        "missing": sorted(set(expected) - set(actual)),
        "unexpected": sorted(set(actual) - set(expected)),
        "metadataDrift": sorted(
            identity
            for identity in set(actual) & set(expected)
            if actual[identity] != expected[identity]
        ),
    }


def assert_protected_source_preflight(dev, root: Path) -> None:
    root.mkdir(mode=0o700)
    secret_root = root / "secrets"
    tls_root = root / "tls"
    secret_root.mkdir(mode=0o700)
    tls_root.mkdir(mode=0o700)
    protected = secret_root / "probe-secret"
    protected.write_text("withheld\n", encoding="utf-8")
    os.chmod(protected, 0o600)
    env = {
        **dev.env,
        "WEAVE_SECRET_ROOT": str(secret_root),
        "WEAVE_TLS_ROOT": str(tls_root),
        "WEAVE_RUNTIME_UID": str(os.getuid()),
        "WEAVE_RUNTIME_GID": str(os.getgid()),
    }
    context = replace(dev, env=env)

    def model_for(source: Path) -> dict[str, object]:
        return {
            "services": {
                "probe": {
                    "user": f"{os.getuid()}:{os.getgid()}",
                    "secrets": [{"source": "probe-secret", "target": "probe-secret"}],
                }
            },
            "secrets": {"probe-secret": {"file": str(source)}},
        }

    valid_model = model_for(protected)
    graph = validate_mount_contract(valid_model)
    preflight_protected_sources(context, valid_model, graph)

    os.chmod(protected, 0o640)
    weak_model = model_for(protected)
    expect_contract_rejection(
        lambda: preflight_protected_sources(
            context, weak_model, normalized_mount_graph(weak_model)
        ),
        "mode-0640 protected source was accepted",
    )
    os.chmod(protected, 0o600)

    symlink = secret_root / "probe-symlink"
    symlink.symlink_to(protected)
    symlink_model = model_for(symlink)
    expect_contract_rejection(
        lambda: preflight_protected_sources(
            context, symlink_model, normalized_mount_graph(symlink_model)
        ),
        "symlink protected source was accepted",
    )


def main() -> None:
    dev = load_context("dev", ROOT)
    assert_identity_bootstrap_lifecycle(dev)
    with tempfile.TemporaryDirectory() as temporary:
        assert_identity_bootstrap_authority_probe(dev, Path(temporary))
    keycloak_launcher = (ROOT / "scripts/run-keycloak.sh").read_text(
        encoding="utf-8"
    )
    assert (
        "read_secret KC_BOOTSTRAP_ADMIN_CLIENT_SECRET "
        "/run/secrets/keycloak-bootstrap-admin-password"
        in keycloak_launcher
    )
    assert (
        "export KC_BOOTSTRAP_ADMIN_CLIENT_ID=weave-identity-ops-bootstrap"
        in keycloak_launcher
    )
    assert (
        'if [[ "${1:-}" == "bootstrap-admin" ]]'
        in keycloak_launcher
    )
    assert dev.profile == "dev"
    assert dev.env["WEAVE_DEPLOYMENT_CONTEXT"] == "developer"
    assert dev.compose_files[1].name == "compose.dev.yaml"
    dev_model = resolved_model(dev)
    validate_mount_contract(dev_model)
    assert_long_running_services_reap_child_processes(dev_model)
    assert "backend" not in dev_model["services"]
    assert dev_model["services"]["keycloak"]["user"] == f"{dev.env['WEAVE_RUNTIME_UID']}:0"
    historical = labels(dev, "network", dev.env["WEAVE_DOCKER_NETWORK"])
    historical.update(
        {
            "com.massimotter.weave.spec-commit": "1" * 40,
            "com.massimotter.weave.spec-digest": "sha256:" + "2" * 64,
            "com.massimotter.weave.candidate-commit": "3" * 40,
            "com.massimotter.weave.candidate-manifest-digest": "sha256:" + "4" * 64,
        }
    )
    assert resource_labels_match(
        dev, "network", dev.env["WEAVE_DOCKER_NETWORK"], historical
    )
    wrong_generation = {**historical, "com.massimotter.weave.generation": "retired"}
    assert not resource_labels_match(
        dev, "network", dev.env["WEAVE_DOCKER_NETWORK"], wrong_generation
    )
    incomplete_provenance = dict(historical)
    incomplete_provenance.pop("com.massimotter.weave.candidate-commit")
    assert not resource_labels_match(
        dev, "network", dev.env["WEAVE_DOCKER_NETWORK"], incomplete_provenance
    )
    try:
        _image_digest(dev)
    except ContractError:
        pass
    else:
        raise AssertionError("dev renderer invented a digest from the reviewed version tag")
    canonical = {
        "apiVersion": "weave.keycloak-desired-state/v2",
        "keycloakVersion": "26.7.0",
        "environment": "test",
        "revision": "",
        "clientPolicies": [
            {
                "key": "policy:weaver-cell-registration",
                "name": "weaver-cell-registration",
                "enabled": True,
                "conditionProvider": "any-client",
                "executorProvider": "weave-workload-client-registration-enforcer",
                "executorVersion": "1",
                "keycloakVersion": "26.7.0",
                "runtimeAdminClientKey": "client:weave-agent-runtime-admin",
                "registrationProvider": "openid-connect",
                "identifierMetadata": "client_name",
                "workloadRoleRef": "role:weaver-runtime",
            }
        ],
        "provenance": {"overlayRevision": ""},
        "realm": {"adminPermissionsEnabled": True, "frontendUrl": "", "smtp": {}},
        "organizations": [{"key": "organization:weave-primary", "alias": "weave"}],
        "clientScopes": [
            {
                "key": "scope:audience-contract",
                "mappers": [
                    {
                        "includedCustomAudience": "https://api.weave.test/api",
                    },
                    {
                        "includedCustomAudience": "https://api.weave.test/mcp",
                    },
                    {
                        "includedCustomAudience": (
                            "https://api.weave.test/api/v1/agent-runtime"
                        ),
                    },
                ],
            },
        ],
        "organizationGroups": [
            {
                "key": f"organization-group:{name}",
                "organizationRef": "organization:weave-primary",
                "path": f"/{name}",
                "parentGroupRef": None,
                "roleRefs": [f"role:{name.removesuffix('s')}"],
            }
            for name in ("owners", "admins", "members", "guests")
        ] + [
            {
                "key": "organization-group:weave-primary:capabilities",
                "organizationRef": "organization:weave-primary",
                "path": "/capabilities",
                "parentGroupRef": None,
                "roleRefs": [],
            },
            {
                "key": "organization-group:weave-primary:capabilities-weaver",
                "organizationRef": "organization:weave-primary",
                "path": "/capabilities/weaver",
                "parentGroupRef": "organization-group:weave-primary:capabilities",
                "roleRefs": [],
            },
        ],
        "fineGrainedAdminPermissions": {
            "enabled": True,
            "subjectPolicies": [],
            "permissions": [],
        },
        "serviceAccountRoleGrants": [
            {
                "clientKey": "client:weave-identity-admin",
                "roleRefs": [
                    "builtin-role:realm-management:query-organizations",
                    "builtin-role:realm-management:query-users",
                ],
            },
            {
                "clientKey": "client:weave-agent-runtime-admin",
                "roleRefs": [
                    "builtin-role:realm-management:create-client",
                ],
            }
        ],
    }
    overlay = {
        "publicUrls": {
            "api": "https://api.weave.test:9443/api",
            "auth": "https://auth.weave.local",
            "matrix": "https://matrix.weave.local",
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
    rendered = _render_desired(canonical, overlay)
    assert "groups" not in rendered
    assert "externalContractAssignments" not in rendered
    assert "identityOpsManagedSurface" not in rendered
    assert "organizationInvitationLifecycle" not in rendered
    try:
        _render_desired({**canonical, "groups": []}, overlay)
    except ContractError:
        pass
    else:
        raise AssertionError("renderer accepted a legacy desired-state groups field")
    assert rendered["clientPolicies"] == canonical["clientPolicies"]
    assert rendered["serviceAccountRoleGrants"] == canonical["serviceAccountRoleGrants"]
    assert [
        mapper["includedCustomAudience"]
        for mapper in rendered["clientScopes"][0]["mappers"]
    ] == [
        "https://api.weave.test:9443/api",
        "https://api.weave.test:9443/mcp",
        "https://api.weave.test:9443/api/v1/agent-runtime",
    ]
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        test = load_context("test", ROOT, str(materialize_example("test", root / "test.env")))
        prod = load_context("prod", ROOT, str(materialize_example("prod", root / "prod.env")))
        assert test.env["WEAVE_DEPLOYMENT_CONTEXT"] == "persistent-dogfood"
        assert prod.env["WEAVE_DEPLOYMENT_CONTEXT"] == "production"
        assert test.compose_files[1].name == "compose.test.yaml"
        assert prod.compose_files[1].name == "compose.prod.yaml"
        test_overlay = (ROOT / "compose.test.yaml").read_text(encoding="utf-8")
        assert "  keycloak:\n" in test_overlay
        assert "    command:\n      - start\n" in test_overlay
        assert "--optimized" not in test_overlay
        assert _image_digest(test) == "sha256:" + "a" * 64
        assert _image_digest(prod) == "sha256:" + "a" * 64
        backend_env = _backend_env(test)
        assert (
            f"WEAVE_API_BASE_URL={test.env['WEAVE_API_URL']}\n"
            in backend_env
        )
        assert "WEAVE_AGENT_RUNTIME_WORKLOAD_IDENTITY_ENABLED=true\n" in backend_env
        assert (
            "WEAVE_AGENT_RUNTIME_ADMIN_CLIENT_ID=weave-agent-runtime-admin\n"
            in backend_env
        )
        assert (
            "WEAVE_AGENT_RUNTIME_ADMIN_CREDENTIAL_REF="
            "credentialref://weave/keycloak/weave-agent-runtime-admin\n"
            in backend_env
        )
        for rendered_backend_env in (backend_env, _backend_env(prod)):
            assert (
                "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ENABLED=true\n"
                in rendered_backend_env
            )
            assert (
                "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ORGANIZATION_REF="
                "tenant-default\n"
                in rendered_backend_env
            )
            assert (
                "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ADAPTER_KEY="
                "nextcloud-webdav\n"
                in rendered_backend_env
            )
            assert (
                "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_CONFIGURATION_REF="
                "secretref:files:nextcloud\n"
                in rendered_backend_env
            )
        test_model = resolved_model(test)
        prod_model = resolved_model(prod)
        assert_long_running_services_reap_child_processes(test_model)
        assert_long_running_services_reap_child_processes(prod_model)
        assert test_model["services"]["keycloak"]["user"] == (
            f"{test.env['WEAVE_RUNTIME_UID']}:0"
        )
        assert prod_model["services"]["keycloak"]["user"] == (
            f"{prod.env['WEAVE_RUNTIME_UID']}:0"
        )
        assert_agent_runtime_mount_boundary(test_model)
        assert_agent_runtime_mount_boundary(prod_model)
        assert_schema_init_boundary(test_model)
        assert_schema_init_boundary(prod_model)
        assert_fresh_start_target_graph(test_model, test)
        assert_runtime_state_boundary(
            test_model,
            test.env["WEAVE_RUNTIME_UID"],
            test.env["WEAVE_RUNTIME_GID"],
        )
        regression = json.loads(
            (
                ROOT
                / "tests/fixtures/compose/readonly-agent-runtime-parent.json"
            ).read_text(encoding="utf-8")
        )
        expect_contract_rejection(
            lambda: validate_mount_contract(regression),
            "former read-only Agent Runtime parent topology was accepted",
        )
        assert_protected_source_preflight(dev, root / "protected-source-preflight")
        local_image_id = "sha256:" + "b" * 64
        isolated_overrides = {
            "WEAVE_E2E_STACK_SCOPE": "isolated",
            "WEAVE_E2E_RUN_ID": "compose-profile-contract",
            "WEAVE_BACKEND_IMAGE": local_image_id,
            "WEAVE_MCP_IMAGE": local_image_id,
            "WEAVE_IDENTITY_OPS_IMAGE": local_image_id,
            "WEAVE_KEYCLOAK_IMAGE": local_image_id,
        }
        previous_overrides = {
            name: os.environ.get(name) for name in isolated_overrides
        }
        try:
            os.environ.update(isolated_overrides)
            isolated = load_context("test", ROOT, str(root / "test.env"))
            assert isolated.env["WEAVE_STACK_SCOPE"] == "isolated"
            assert isolated.compose_files[2].name == "compose.isolated-e2e.yaml"
            assert isolated.env["WEAVE_KEYCLOAK_IMAGE"] == local_image_id
            assert isolated.env["WEAVE_RUNTIME_STATE_VOLUME"].endswith(
                "_runtime_state"
            )
            assert isolated.env["WEAVE_RUNTIME_STATE_IMAGE"].startswith(
                "minio/minio@sha256:"
            )
            assert _image_digest(isolated) == local_image_id
            isolated_backend_env = _backend_env(isolated)
            assert (
                f"WEAVE_API_BASE_URL={isolated.env['WEAVE_API_URL']}\n"
                in isolated_backend_env
            )
            assert (
                "WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_CLAIM=preferred_username\n"
                in isolated_backend_env
            )
            assert (
                "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF="
                not in isolated_backend_env
            )
            assert (
                isolated.compose_files[2].read_text(encoding="utf-8").count(
                    "context-authorization-memberships.json"
                )
                == 3
            )
            assert (
                "WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF="
                not in backend_env
            )
            assert_collaboration_control_is_bounded(isolated)
            os.environ["WEAVE_E2E_STACK_SCOPE"] = "persistent"
            try:
                load_context("test", ROOT, str(root / "test.env"))
            except ContractError as error:
                assert "WEAVE_KEYCLOAK_IMAGE" in str(error)
            else:
                raise AssertionError(
                    "persistent test accepted a local Keycloak image ID"
                )
        finally:
            for name, value in previous_overrides.items():
                if value is None:
                    os.environ.pop(name, None)
                else:
                    os.environ[name] = value
        runtime_source = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
        assert "WEAVE_TEST_USERS_FILE" not in runtime_source
        assert "test-users.json" not in runtime_source
        assert '"dev": ("caddy", "mailpit")' in runtime_source
        assert '"test": ("caddy", "mailpit", "mcp")' in runtime_source
        assert '"prod": ("caddy", "mcp")' in runtime_source
        assert "HOST_APPLICATION_SERVICES" in runtime_source
        assert '"rm",\n                "--stop",\n                "--force",' in runtime_source
        assert 'elif command == "down":\n        if context.profile == "dev":' in runtime_source
        assert '"--wait-timeout",\n            "600",' in runtime_source
        assert 'script(context, "nextcloud_reconcile.py")' in runtime_source
        invalid = root / "invalid.env"
        invalid.write_text((root / "test.env").read_text().replace("WEAVE_ENVIRONMENT=test", "WEAVE_ENVIRONMENT=dogfood"))
        try:
            load_context("test", ROOT, str(invalid))
        except ContractError:
            pass
        else:
            raise AssertionError("legacy profile value was accepted")
    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert "\n  - dogfood\n" not in compose
    assert "\n  - main\n" not in compose
    assert "\n  - test\n" in compose and "\n  - prod\n" in compose
    assert "/run/secrets/agent-runtime:ro" not in compose
    assert "${WEAVE_TLS_ROOT:-./.generated/dev/tls}:/certs:ro" not in compose
    print("compose profile contract tests passed")


if __name__ == "__main__":
    main()
