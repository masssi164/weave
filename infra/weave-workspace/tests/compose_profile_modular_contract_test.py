#!/usr/bin/env python3
"""Validate Compose/Spring profile ownership after renderer modularization."""

from __future__ import annotations

import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = ROOT.parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from compose_env import ContractError, load_context  # noqa: E402
from render_config import _reset_provider_configtree  # noqa: E402
from rendering.gateway import _site, render_caddy  # noqa: E402
from rendering.keycloak import _desired, _image_digest, _overlay  # noqa: E402


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


def main() -> int:
    compose_source = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert compose_source.count(
        "SPRING_PROFILES_ACTIVE: ${WEAVE_ENVIRONMENT:?environment required}"
    ) == 3
    assert "/backend/public.env" not in compose_source
    assert "/backend/host.env" not in compose_source
    assert "/mcp/public.env" not in compose_source
    assert "/mcp/host.env" not in compose_source

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
