#!/usr/bin/env python3
"""Contract tests for branch-independent dev/test/prod Compose profiles."""

from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys

sys.path.insert(0, str(ROOT / "scripts"))
from compose_env import ContractError, load_context  # noqa: E402
from compose_runtime import test_user_volume  # noqa: E402
from render_config import _image_digest, _render_desired  # noqa: E402


def materialize_example(profile: str, destination: Path) -> Path:
    source = ROOT / f"environments/{profile}.env.example"
    value = re.sub(r"sha256:replace-with-[a-zA-Z0-9.-]+", "sha256:" + "a" * 64, source.read_text())
    destination.write_text(value, encoding="utf-8")
    os.chmod(destination, 0o600)
    return destination


def main() -> None:
    dev = load_context("dev", ROOT)
    assert dev.profile == "dev"
    assert dev.env["WEAVE_DEPLOYMENT_CONTEXT"] == "developer"
    assert dev.compose_files[1].name == "compose.dev.yaml"
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
        "clientPolicies": [],
        "provenance": {"overlayRevision": ""},
        "realm": {"adminPermissionsEnabled": True, "frontendUrl": "", "smtp": {}},
        "organizations": [{"key": "organization:weave-primary", "alias": "weave"}],
        "organizationGroups": [
            {
                "key": f"organization-group:{name}",
                "organizationRef": "organization:weave-primary",
                "path": f"/{name}",
                "parentGroupRef": None,
                "roleRefs": [f"role:{name.removesuffix('s')}"],
            }
            for name in ("owners", "admins", "members", "guests")
        ],
        "fineGrainedAdminPermissions": {
            "enabled": True,
            "subjectPolicies": [],
            "permissions": [],
        },
        "serviceAccountRoleGrants": [
            {
                "clientKey": "client:weave-identity-admin",
                "roleRefs": ["builtin-role:realm-management:query-organizations"],
            }
        ],
    }
    overlay = {
        "publicUrls": {
            "api": "https://api.weave.local/api",
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
    try:
        _render_desired({**canonical, "clientPolicies": [{"executors": ["custom"]}]}, overlay)
    except ContractError:
        pass
    else:
        raise AssertionError("renderer silently accepted unmanaged custom clientPolicies")
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        test = load_context("test", ROOT, str(materialize_example("test", root / "test.env")))
        prod = load_context("prod", ROOT, str(materialize_example("prod", root / "prod.env")))
        assert test.env["WEAVE_DEPLOYMENT_CONTEXT"] == "persistent-adoption"
        assert prod.env["WEAVE_DEPLOYMENT_CONTEXT"] == "production"
        assert test.compose_files[1].name == "compose.test.yaml"
        assert prod.compose_files[1].name == "compose.prod.yaml"
        assert _image_digest(test) == "sha256:" + "a" * 64
        assert _image_digest(prod) == "sha256:" + "a" * 64
        runtime_source = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
        assert 'context.root / ".generated/test/test-users.json"' in runtime_source
        invalid = root / "invalid.env"
        invalid.write_text((root / "test.env").read_text().replace("WEAVE_ENVIRONMENT=test", "WEAVE_ENVIRONMENT=dogfood"))
        try:
            load_context("test", ROOT, str(invalid))
        except ContractError:
            pass
        else:
            raise AssertionError("legacy profile value was accepted")
        private_users = root / "users.json"
        private_users.write_text("[]\n", encoding="utf-8")
        os.chmod(private_users, 0o600)
        test.env["WEAVE_TEST_USERS_FILE"] = str(private_users)
        assert test_user_volume(test)[0] == "--volume"
        symlink = root / "users-link.json"
        symlink.symlink_to(private_users)
        test.env["WEAVE_TEST_USERS_FILE"] = str(symlink)
        try:
            test_user_volume(test)
        except ContractError:
            pass
        else:
            raise AssertionError("symlinked test-user file was accepted")
        prod.env["WEAVE_TEST_USERS_FILE"] = ""
        try:
            test_user_volume(prod)
        except ContractError:
            pass
        else:
            raise AssertionError("prod accepted an explicitly configured test-user file input")
    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert "\n  - dogfood\n" not in compose
    assert "\n  - main\n" not in compose
    assert "\n  - test\n" in compose and "\n  - prod\n" in compose
    print("compose profile contract tests passed")


if __name__ == "__main__":
    main()
