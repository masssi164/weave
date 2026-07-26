#!/usr/bin/env python3
"""Static and semantic tests for rootless one-shot Keycloak Identity Ops."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "keycloak/identity_ops.py"
SPEC = importlib.util.spec_from_file_location("identity_ops", MODULE_PATH)
assert SPEC and SPEC.loader
identity_ops = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = identity_ops
SPEC.loader.exec_module(identity_ops)


def main() -> None:
    desired = {
        "keycloakVersion": "26.7.0",
        "organizationInvitationLifecycle": {
            "sinceVersion": "26.5",
            "operations": {"list": "a", "resend": "b", "delete": "c"},
        },
    }
    payload = {"clientId": "weave-app", "enabled": True}
    realm = identity_ops.realm_payload(
        {
            "name": "weave",
            "frontendUrl": "https://auth.weave.local",
            "smtp": {
                "host": "mailpit",
                "port": 1025,
                "fromAddress": "noreply@weave.local",
                "fromDisplayName": "Weave",
                "ssl": False,
                "startTls": False,
            },
        }
    )
    assert "frontendUrl" not in realm
    assert realm["attributes"]["frontendUrl"] == "https://auth.weave.local"
    assert realm["smtpServer"]["port"] == "1025"
    calls: list[list[str]] = []
    original_run = identity_ops.subprocess.run

    class Result:
        returncode = 0
        stdout = "[]"
        stderr = ""

    def fake_run(command: list[str], **_kwargs: object) -> Result:
        calls.append(command)
        return Result()

    identity_ops.subprocess.run = fake_run
    try:
        client = identity_ops.Kcadm("/opt/keycloak/bin/kcadm.sh", Path("/tmp/test.config"))
        client.call("get", "clients", "-r", "weave")
        client.call(
            "config", "credentials", "--server", "http://keycloak:8080",
            "--realm", "master", "--client", "bootstrap", "--secret", "withheld",
        )
    finally:
        identity_ops.subprocess.run = original_run
    assert calls[0][:4] == [
        "/opt/keycloak/bin/kcadm.sh", "get", "clients", "--config"
    ]
    assert calls[1][:4] == [
        "/opt/keycloak/bin/kcadm.sh", "config", "credentials", "--config"
    ]

    class FailedResult:
        returncode = 1
        stdout = ""
        stderr = "HTTP 409 Conflict: sensitive provider detail"

    identity_ops.subprocess.run = lambda *_args, **_kwargs: FailedResult()
    try:
        client.call("create", "organizations/org/groups/parent/children", "-r", "weave")
    except identity_ops.IdentityOpsError as error:
        assert "httpStatus=409" in str(error)
        assert "sensitive provider detail" not in str(error)
    else:
        raise AssertionError("kcadm failure was silently accepted")
    finally:
        identity_ops.subprocess.run = original_run
    observed = identity_ops.marked_payload("client:weave-app", payload, list_values=False)
    assert identity_ops.is_current("client:weave-app", payload, observed, list_values=False)
    assert not identity_ops.is_current("client:other", payload, observed, list_values=False)
    group = identity_ops.marked_payload("group:members", {"name": "members"}, list_values=True)
    assert group["attributes"]["weave.semantic-key"] == ["group:members"]
    hierarchy = identity_ops.flatten_groups(
        [{"id": "parent", "name": "people", "subGroups": [{"id": "child", "name": "members"}]}]
    )
    assert [(item["id"], item["_path"]) for item in hierarchy] == [
        ("parent", "/people"),
        ("child", "/people/members"),
    ]
    owner, mapped_role = identity_ops.role_mapping(
        "role:member",
        {"role:member": {"id": "role-id", "name": "member", "_scope": "client", "_clientKey": "client:app"}},
        {"client:app": {"id": "client-id"}},
    )
    assert owner == "clients/client-id" and mapped_role == {"id": "role-id", "name": "member"}
    try:
        identity_ops.exact([{}, {}], "client:x", "client")
    except identity_ops.IdentityOpsError:
        pass
    else:
        raise AssertionError("ambiguous semantic lookup was accepted")
    assert not identity_ops.requires_rotation("client:x", "same", "same", None)
    for expected in ("stale", None):
        try:
            identity_ops.requires_rotation("client:x", "live", expected, None)
        except identity_ops.IdentityOpsError:
            pass
        else:
            raise AssertionError("routine apply accepted stale or missing SecretRef state")
        assert identity_ops.requires_rotation("client:x", "live", expected, "rotation-2026-07") is True
    expected_roles = {"query-organizations"}
    missing, remove = identity_ops.identity_admin_role_delta(
        {"manage-realm", "manage-organizations", "view-organizations", "query-groups", "query-users"},
        expected_roles,
    )
    assert remove == {"manage-realm", "manage-organizations", "view-organizations", "query-groups", "query-users"}
    assert missing == {"query-organizations"}
    missing, remove = identity_ops.identity_admin_role_delta(expected_roles, expected_roles)
    assert not missing and not remove
    try:
        identity_ops.identity_admin_role_delta(expected_roles | {"impersonation"}, expected_roles)
    except identity_ops.IdentityOpsError:
        pass
    else:
        raise AssertionError("unmanaged broad role was silently removed or accepted")
    contract = json.dumps(desired)
    assert "26.7.0" in contract
    compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
    runtime = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
    dockerfile = (ROOT / "keycloak/Dockerfile.identity-ops").read_text(encoding="utf-8")
    assert "FROM ${WEAVE_KEYCLOAK_BASE}" in dockerfile
    assert "FROM ${WEAVE_UBI9_BASE}" in dockerfile
    assert "FROM registry.access.redhat.com/ubi9" not in dockerfile
    builder = (ROOT / "scripts/build_identity_ops_image.py").read_text(encoding="utf-8")
    assert "keycloakBaseResolved" in builder and "ubi9BaseResolved" in builder
    assert "pinned_base" in builder and "must declare one exact OCI digest" in builder
    assert "build inputs differ from the selected candidate commit" in builder
    assert "user: \"${WEAVE_RUNTIME_UID:-1000}:${WEAVE_RUNTIME_GID:-1000}\"" in compose
    assert "no-new-privileges:true" in compose and "cap_drop:" in compose
    assert "/var/run/docker.sock" not in compose
    assert "sudo" not in runtime
    assert "WEAVE_TEST_USERS_FILE" not in runtime
    assert "test-users.json" not in runtime
    source = MODULE_PATH.read_text(encoding="utf-8")
    assert "/opt/keycloak/bin/kcadm.sh" in source
    assert '"resourceType": "Organizations"' in source
    assert '{"manage", "view"}' in source
    assert '"query-organizations"' in source
    assert "identity_admin_role_delta(observed_names, expected)" in source
    assert '"remove-role"' in source and '"remove-roles"' in source
    assert '"Authorization": f"Basic {authorization}"' in source
    assert '"grant_type": "client_credentials"' in source
    assert '"client_secret":' not in source
    assert '"token.endpoint.auth.method": "client_secret_basic"' in source
    assert '"set-password"' not in source
    assert "reset-password" not in source
    assert '"map-org-group-role"' in source
    assert "if parent is None:" in source and "Parent groups are deliberately created" in source
    assert '"id": staged["id"], "name": staged["name"]' in source
    assert "Stage the managed resource at organization" in source
    assert '"add-roles", "-r", realm, "--uusername", item["username"]' not in source
    renderer = (ROOT / "scripts/render_config.py").read_text(encoding="utf-8")
    assert '"weave.keycloak-desired-state/v2"' in renderer
    assert 'if "groups" in desired:' in renderer
    assert 'desired["organizationGroups"] =' not in renderer
    assert 'desired["fineGrainedAdminPermissions"] =' not in renderer
    assert '"externalContractAssignments"' not in renderer
    assert '"identityOpsManagedSurface"' not in renderer
    assert '"organizationInvitationLifecycle"' not in renderer
    assert 'desired.get("clientPolicies") != []' in renderer
    assert 'desired.get("clientPolicies") != []' in source
    assert 'choices=("plan", "apply", "verify")' in source
    assert '"verification found a non-empty plan"' in source
    assert not (ROOT / "scripts/create_test_users_file.py").exists()
    assert not (ROOT / "keycloak/test-users.schema.json").exists()
    with tempfile.TemporaryDirectory() as temporary:
        credential = Path(temporary) / "credential"
        credential.write_text("not-a-real-secret\n", encoding="utf-8")
        credential.chmod(0o644)
        try:
            identity_ops.private_value(credential)
        except identity_ops.IdentityOpsError:
            pass
        else:
            raise AssertionError("over-readable credential file was accepted")
        credential.chmod(0o600)
        assert identity_ops.private_value(credential) == "not-a-real-secret"
    with tempfile.TemporaryDirectory() as temporary:
        evidence_path = Path(temporary) / "evidence.json"
        evidence = identity_ops.evidence("plan", {"revision": "sha256:x"}, [], None)
        identity_ops.write_evidence(evidence_path, evidence)
        assert evidence_path.stat().st_mode & 0o777 == 0o600
        assert json.loads(evidence_path.read_text())["containsSecretValues"] is False
        assert json.loads(evidence_path.read_text())["temporaryBootstrapAuthorityRemoved"] is True
    print("identity ops contract tests passed")


if __name__ == "__main__":
    main()
