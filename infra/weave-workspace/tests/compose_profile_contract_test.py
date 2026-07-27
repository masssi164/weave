#!/usr/bin/env python3
"""Contract tests for branch-independent dev/test/prod Compose profiles."""

from __future__ import annotations

import json
import os
import re
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys

sys.path.insert(0, str(ROOT / "scripts"))
from compose_env import ContractError, load_context  # noqa: E402
from compose_runtime import resource_inventory, validate_adoption_receipt  # noqa: E402
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
        test_overlay = (ROOT / "compose.test.yaml").read_text(encoding="utf-8")
        assert "  keycloak:\n" in test_overlay
        assert "    command:\n      - start\n" in test_overlay
        assert "--optimized" not in test_overlay
        assert _image_digest(test) == "sha256:" + "a" * 64
        assert _image_digest(prod) == "sha256:" + "a" * 64
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
            assert isolated.env["WEAVE_KEYCLOAK_IMAGE"] == local_image_id
            assert _image_digest(isolated) == local_image_id
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
        test.env["WEAVE_GENERATED_ROOT"] = str(root / "generated")
        candidate = "b" * 40
        receipt = test.generated_root / "adoption/adoption-receipt.json"
        receipt.parent.mkdir(parents=True)
        receipt_data = {
            "schemaVersion": "weave.compose-adoption-receipt.v1",
            "profile": "test",
            "composeProject": test.env["WEAVE_COMPOSE_PROJECT"],
            "candidateCommit": candidate,
            "backupVerified": True,
            "isolatedRestoreVerified": True,
            "verifiedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "resources": [
                {"kind": kind, "name": name}
                for kind, name in sorted(resource_inventory(test))
            ],
            "supportSafe": True,
            "containsSecretValues": False,
        }

        def write_receipt() -> None:
            receipt.write_text(json.dumps(receipt_data) + "\n", encoding="utf-8")
            os.chmod(receipt, 0o600)

        def require_receipt_rejection(reason: str) -> None:
            try:
                validate_adoption_receipt(test, "volume", test.env["WEAVE_DB_DATA_VOLUME"])
            except ContractError:
                return
            raise AssertionError(f"{reason} adoption receipt was accepted")

        write_receipt()
        previous_receipt = os.environ.get("WEAVE_ADOPTION_RECEIPT")
        previous_candidate = os.environ.get("WEAVE_CANDIDATE_COMMIT")
        try:
            os.environ["WEAVE_CANDIDATE_COMMIT"] = candidate
            os.environ.pop("WEAVE_ADOPTION_RECEIPT", None)
            require_receipt_rejection("missing")
            os.environ["WEAVE_ADOPTION_RECEIPT"] = str(receipt)
            validate_adoption_receipt(test, "volume", test.env["WEAVE_DB_DATA_VOLUME"])
            os.environ["WEAVE_CANDIDATE_COMMIT"] = "c" * 40
            require_receipt_rejection("wrong-candidate")
            os.environ["WEAVE_CANDIDATE_COMMIT"] = candidate
            receipt_data["verifiedAt"] = "2020-01-01T00:00:00Z"
            write_receipt()
            require_receipt_rejection("stale")
            receipt_data["verifiedAt"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            complete_resources = receipt_data["resources"]
            receipt_data["resources"] = complete_resources[:-1]
            write_receipt()
            require_receipt_rejection("resource-incomplete")
            receipt_data["resources"] = complete_resources
            write_receipt()
            os.chmod(receipt, 0o644)
            require_receipt_rejection("weakly-permissioned")
        finally:
            if previous_receipt is None:
                os.environ.pop("WEAVE_ADOPTION_RECEIPT", None)
            else:
                os.environ["WEAVE_ADOPTION_RECEIPT"] = previous_receipt
            if previous_candidate is None:
                os.environ.pop("WEAVE_CANDIDATE_COMMIT", None)
            else:
                os.environ["WEAVE_CANDIDATE_COMMIT"] = previous_candidate
    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    assert "\n  - dogfood\n" not in compose
    assert "\n  - main\n" not in compose
    assert "\n  - test\n" in compose and "\n  - prod\n" in compose
    print("compose profile contract tests passed")


if __name__ == "__main__":
    main()
