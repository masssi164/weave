#!/usr/bin/env python3
"""Static and semantic tests for rootless one-shot Keycloak Identity Ops."""

from __future__ import annotations

import base64
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import urllib.parse
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
            "verifyEmail": True,
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
    assert realm["verifyEmail"] is True
    assert realm["smtpServer"]["port"] == "1025"
    try:
        identity_ops.realm_payload({"name": "weave"})
        raise AssertionError("realm payload accepted missing native email verification policy")
    except KeyError as error:
        assert error.args == ("verifyEmail",)
    assert '"emailVerified"' not in MODULE_PATH.read_text(encoding="utf-8")
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

    calls.clear()
    timestamps = iter((10.0, 41.0, 42.0))
    original_monotonic = identity_ops.time.monotonic
    identity_ops.time.monotonic = lambda: next(timestamps)
    identity_ops.subprocess.run = fake_run
    try:
        client = identity_ops.Kcadm("/opt/keycloak/bin/kcadm.sh", Path("/tmp/test.config"))
        client.authenticate("http://keycloak:8080", "bootstrap", "withheld")
        client.call("get", "clients", "-r", "weave")
    finally:
        identity_ops.subprocess.run = original_run
        identity_ops.time.monotonic = original_monotonic
    assert [call[1:3] for call in calls] == [
        ["config", "credentials"],
        ["config", "credentials"],
        ["get", "clients"],
    ]

    original_urlopen = identity_ops.urllib.request.urlopen

    class TokenResponse:
        status = 200

        def __enter__(self) -> "TokenResponse":
            return self

        def __exit__(self, *_args: object) -> None:
            return None

        def read(self) -> bytes:
            return b'{"access_token":"test-only"}'

    identity_ops.urllib.request.urlopen = lambda *_args, **_kwargs: TokenResponse()
    try:
        assert identity_ops.client_credentials_token_response(
            "http://keycloak:8080",
            "weave",
            "service-client",
            "test-secret",
        ) == (200, {"access_token": "test-only"})
    finally:
        identity_ops.urllib.request.urlopen = original_urlopen

    captured_private_key_jwt: dict[str, str] = {}

    def private_key_jwt_urlopen(request: object, **_kwargs: object) -> TokenResponse:
        captured_private_key_jwt.update(
            urllib.parse.parse_qsl(request.data.decode("ascii"))
        )
        return TokenResponse()

    private_integer = lambda value: identity_ops.base64url_bytes(
        value.to_bytes((value.bit_length() + 7) // 8, "big")
    )
    runtime_probe_jwk = {
        "kty": "RSA",
        "use": "sig",
        "alg": "PS256",
        "kid": "runtime-probe",
        "key_ops": ["sign"],
        "n": private_integer((1 << 2048) - 159),
        "e": private_integer(65537),
        "d": private_integer(1),
    }
    identity_ops.urllib.request.urlopen = private_key_jwt_urlopen
    try:
        assert identity_ops.private_key_jwt_token_response(
            "http://keycloak:8080",
            "weave",
            "weave-agent-runtime-admin",
            runtime_probe_jwk,
            "https://auth.weave.local/realms/weave",
        ) == (200, {"access_token": "test-only"})
    finally:
        identity_ops.urllib.request.urlopen = original_urlopen
    assert captured_private_key_jwt["grant_type"] == "client_credentials"
    assert captured_private_key_jwt["client_id"] == "weave-agent-runtime-admin"
    assertion_parts = captured_private_key_jwt["client_assertion"].split(".")
    assert len(assertion_parts) == 3
    assertion_header = json.loads(
        base64.urlsafe_b64decode(assertion_parts[0] + "==")
    )
    assertion_claims = json.loads(
        base64.urlsafe_b64decode(assertion_parts[1] + "==")
    )
    assert assertion_header == {
        "alg": "PS256",
        "kid": "runtime-probe",
        "typ": "JWT",
    }
    assert assertion_claims["iss"] == "weave-agent-runtime-admin"
    assert assertion_claims["sub"] == "weave-agent-runtime-admin"
    assert (
        assertion_claims["aud"] == "https://auth.weave.local/realms/weave"
    )
    assert (
        identity_ops.oauth_probe_failure_category(
            {
                "error": "invalid_client",
                "error_description": "Signature on JWT token failed validation",
            }
        )
        == "invalid-client-signature"
    )
    assert (
        identity_ops.oauth_probe_failure_category(
            {
                "error": "invalid_client",
                "error_description": "Unable to load public key",
            }
        )
        == "invalid-client-public-key"
    )
    token_claims = base64.urlsafe_b64encode(
        json.dumps(
            {
                "resource_access": {
                    "realm-management": {"roles": ["create-client"]}
                }
            },
            separators=(",", ":"),
        ).encode("utf-8")
    ).rstrip(b"=").decode("ascii")
    assert identity_ops.access_token_client_roles(
        f"test.{token_claims}.signature",
        "realm-management",
    ) == {"create-client"}
    assert not identity_ops.access_token_client_roles(
        "malformed",
        "realm-management",
    )

    def rejected_urlopen(request: object, **_kwargs: object) -> None:
        raise identity_ops.urllib.error.HTTPError(
            request.full_url,
            400,
            "Bad Request",
            {},
            io.BytesIO(b'{"error":"unauthorized_client"}'),
        )

    identity_ops.urllib.request.urlopen = rejected_urlopen
    try:
        assert identity_ops.client_credentials_token_response(
            "http://keycloak:8080",
            "weave",
            "human-client",
            "test-secret",
        ) == (400, {"error": "unauthorized_client"})
    finally:
        identity_ops.urllib.request.urlopen = original_urlopen

    class FailedResult:
        returncode = 1
        stdout = ""
        stderr = "HTTP 409 Conflict: sensitive provider detail"

    identity_ops.subprocess.run = lambda *_args, **_kwargs: FailedResult()
    try:
        client.call("create", "organizations/org/groups/parent/children", "-r", "weave")
    except identity_ops.IdentityOpsError as error:
        assert "httpStatus=409" in str(error)
        assert "failureCode=unclassified" in str(error)
        assert "sensitive provider detail" not in str(error)
    else:
        raise AssertionError("kcadm failure was silently accepted")
    finally:
        identity_ops.subprocess.run = original_run
    assert (
        identity_ops.classify_kcadm_failure(
            "HTTP 400 Scope [reset-password] was not found for resource type Users"
        )
        == "reset-password-scope-rejected"
    )
    assert (
        identity_ops.classify_kcadm_failure(
            "HTTP 400 at least one positive policy is required"
        )
        == "positive-policy-required"
    )
    assert (
        identity_ops.classify_kcadm_failure(
            "Conflicting policy [Policy with name [duplicate] already exists]"
        )
        == "authorization-name-conflict"
    )

    class ReadProbeResponse:
        status = 200

        def __enter__(self) -> "ReadProbeResponse":
            return self

        def __exit__(self, *_args: object) -> None:
            return None

        def read(self, _limit: int) -> bytes:
            return b'{"id":"withheld"}'

    def allowed_read_probe(request: object, **_kwargs: object) -> ReadProbeResponse:
        assert request.method == "GET"
        assert request.data is None
        assert request.headers["Authorization"] == "Bearer test-only"
        assert request.full_url.endswith(
            "/admin/realms/weave/organizations/primary-organization-id"
        )
        return ReadProbeResponse()

    identity_ops.urllib.request.urlopen = allowed_read_probe
    try:
        assert identity_ops.administration_read_probe_status(
            "http://keycloak:8080",
            "weave",
            "organizations/primary-organization-id",
            "test-only",
        ) == 200
    finally:
        identity_ops.urllib.request.urlopen = original_urlopen

    class RejectedKcadm:
        def call(self, *_arguments: str, payload: object = None) -> object:
            assert payload == {"secret": "must-not-leak"}
            raise identity_ops.IdentityOpsError(
                "kcadm operation failed, httpStatus=400, output withheld"
            )

    try:
        identity_ops.apply_operations(
            RejectedKcadm(),
            "weave",
            [
                identity_ops.Operation(
                    "create",
                    "admin-permission:identity-users",
                    "clients/admin/authz/resource-server/permission/scope",
                    None,
                    {"secret": "must-not-leak"},
                )
            ],
        )
    except identity_ops.IdentityOpsError as error:
        assert "action=create" in str(error)
        assert "key=admin-permission:identity-users" in str(error)
        assert "must-not-leak" not in str(error)
    else:
        raise AssertionError("failed semantic operation lost its support-safe context")
    observed = identity_ops.marked_payload("client:weave-app", payload, list_values=False)
    assert identity_ops.is_current("client:weave-app", payload, observed, list_values=False)
    assert not identity_ops.is_current("client:other", payload, observed, list_values=False)
    group = identity_ops.marked_payload("group:members", {"name": "members"}, list_values=True)
    assert group["attributes"]["weave.semantic-key"] == ["group:members"]
    wanted_mapper = identity_ops.mapper_payload(
        {
            "name": "weave-api-audience",
            "mapperType": "audience",
            "includedCustomAudience": "weave-api",
        }
    )
    observed_mapper = {
        **wanted_mapper,
        "id": "mapper-id",
        "config": {
            **wanted_mapper["config"],
            "lightweight.claim": "false",
        },
    }
    assert identity_ops.mapper_is_current(wanted_mapper, observed_mapper)
    assert not identity_ops.mapper_is_current(
        wanted_mapper,
        {
            **observed_mapper,
            "config": {
                **observed_mapper["config"],
                "included.custom.audience": "wrong",
            },
        },
    )
    requester_audience_mapper = identity_ops.mapper_payload(
        {
            "name": "mcp-exchange-requester-audience",
            "mapperType": "audience",
            "includedClientKey": "client:weave-mcp-server",
            "addToAccessToken": True,
        },
        {"client:weave-mcp-server": "weave-mcp-server"},
    )
    assert requester_audience_mapper["config"] == {
        "included.client.audience": "weave-mcp-server",
        "id.token.claim": "false",
        "access.token.claim": "true",
        "userinfo.token.claim": "false",
    }
    organization_group_mapper = identity_ops.mapper_payload(
        {
            "name": "weave-organization-groups",
            "mapperType": "organization-group-membership",
            "addGroupRoleMappings": True,
            "addToAccessToken": True,
        }
    )
    assert (
        organization_group_mapper["protocolMapper"]
        == "oidc-organization-group-membership-mapper"
    )
    assert organization_group_mapper["config"] == {
        "addGroupRoleMappings": "true",
        "id.token.claim": "false",
        "access.token.claim": "true",
        "userinfo.token.claim": "false",
    }
    workload_client_id_mapper = identity_ops.mapper_payload(
        {
            "name": "weaver-runtime-client-id",
            "mapperType": "user-session-note",
            "sessionNote": "client_id",
            "claimName": "client_id",
            "claimValueType": "String",
            "addToIdToken": False,
            "addToAccessToken": True,
            "addToUserInfo": False,
        }
    )
    assert (
        workload_client_id_mapper["protocolMapper"]
        == "oidc-usersessionmodel-note-mapper"
    )
    assert workload_client_id_mapper["config"] == {
        "user.session.note": "client_id",
        "claim.name": "client_id",
        "jsonType.label": "String",
        "lightweight.claim": "false",
        "access.tokenResponse.claim": "false",
        "introspection.token.claim": "false",
        "id.token.claim": "false",
        "access.token.claim": "true",
        "userinfo.token.claim": "false",
    }
    workload_role_mapper = identity_ops.mapper_payload(
        {
            "name": "weaver-runtime-realm-role",
            "mapperType": "role",
            "claimName": "realm_access.roles",
            "roleRef": "role:weaver-runtime",
            "addToIdToken": False,
            "addToAccessToken": True,
            "addToUserInfo": False,
        }
    )
    assert (
        workload_role_mapper["protocolMapper"]
        == "oidc-usermodel-realm-role-mapper"
    )
    assert workload_role_mapper["config"] == {
        "claim.name": "realm_access.roles",
        "jsonType.label": "String",
        "multivalued": "true",
        "introspection.token.claim": "false",
        "id.token.claim": "false",
        "access.token.claim": "true",
        "userinfo.token.claim": "false",
    }

    client_without_relationships = identity_ops.client_payload(
        {
            "clientId": "weave-app",
            "enabled": True,
            "defaultClientScopes": ["builtin-scope:basic"],
            "optionalClientScopes": ["scope:calendar-read"],
        }
    )
    assert "defaultClientScopes" not in client_without_relationships
    assert "optionalClientScopes" not in client_without_relationships
    runtime_private_jwk = {
        "kty": "RSA",
        "use": "sig",
        "alg": "PS256",
        "kid": "runtime-test",
        "key_ops": ["sign"],
        "n": "public-modulus",
        "e": "AQAB",
        "d": "private-exponent",
        "p": "private-prime",
        "q": "private-prime",
        "dp": "private-exponent",
        "dq": "private-exponent",
        "qi": "private-coefficient",
    }
    original_private_value = identity_ops.private_value
    identity_ops.private_value = lambda _path: json.dumps(runtime_private_jwk)
    try:
        runtime_admin = identity_ops.client_payload(
            {
                "key": "client:weave-agent-runtime-admin",
                "clientId": "weave-agent-runtime-admin",
                "authenticationMethod": "private_key_jwt",
                "keyRef": "secretref:keycloak/weave-agent-runtime-admin-jwk",
            }
        )
    finally:
        identity_ops.private_value = original_private_value
    runtime_public_jwk = json.loads(runtime_admin["attributes"]["jwks.string"])["keys"][0]
    assert runtime_admin["clientAuthenticatorType"] == "client-jwt"
    assert runtime_admin["attributes"]["token.endpoint.auth.method"] == "private_key_jwt"
    assert runtime_admin["attributes"]["token.endpoint.auth.signing.alg"] == "PS256"
    assert runtime_admin["attributes"]["use.jwks.url"] == "false"
    assert runtime_admin["attributes"]["use.jwks.string"] == "true"
    assert runtime_public_jwk["key_ops"] == ["verify"]
    assert "d" not in runtime_public_jwk
    assert identity_ops.client_scope_payload(
        {
            "name": "weave:workspace",
            "protocol": "openid-connect",
            "includeInTokenScope": True,
        }
    ) == {
        "name": "weave:workspace",
        "protocol": "openid-connect",
        "attributes": {"include.in.token.scope": "true"},
    }
    assert identity_ops.client_scope_payload(
        {
            "name": "weave-api-audience",
            "protocol": "openid-connect",
            "includeInTokenScope": False,
        }
    )["attributes"] == {"include.in.token.scope": "false"}

    class ClientScopeAssociationKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            self.calls.append(arguments)
            endpoint = arguments[1]
            if endpoint == "clients/client-uuid/default-client-scopes":
                return [
                    {"id": "basic-id", "name": "basic"},
                    {"id": "stale-id", "name": "stale"},
                ]
            if endpoint == "clients/client-uuid/optional-client-scopes":
                return []
            raise AssertionError(f"unexpected client scope association read: {endpoint}")

    scope_association_kcadm = ClientScopeAssociationKcadm()
    scope_operations = identity_ops.client_scope_attachment_operations(
        scope_association_kcadm,
        "weave",
        [
            {
                "key": "client:weave-app",
                "defaultClientScopes": [
                    "builtin-scope:basic",
                    "scope:weave-workspace",
                ],
                "optionalClientScopes": ["scope:calendar-read"],
            }
        ],
        {"client:weave-app": {"id": "client-uuid"}},
        [
            {"id": "basic-id", "name": "basic"},
            {"id": "workspace-id", "name": "weave:workspace"},
            {"id": "calendar-id", "name": "calendar.read"},
            {"id": "stale-id", "name": "stale"},
        ],
        {
            "scope:weave-workspace": "weave:workspace",
            "scope:calendar-read": "calendar.read",
        },
    )
    assert [
        (operation.action, operation.endpoint)
        for operation in scope_operations
    ] == [
        (
            "attach-client-scope",
            "clients/client-uuid/default-client-scopes/workspace-id",
        ),
        (
            "detach-client-scope",
            "clients/client-uuid/default-client-scopes/stale-id",
        ),
        (
            "attach-client-scope",
            "clients/client-uuid/optional-client-scopes/calendar-id",
        ),
    ]

    class ClientScopeMutationKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> None:
            assert payload is None
            self.calls.append(arguments)

    scope_mutation_kcadm = ClientScopeMutationKcadm()
    for operation in scope_operations:
        identity_ops.apply_operation(scope_mutation_kcadm, "weave", operation)
    assert [call[0] for call in scope_mutation_kcadm.calls] == [
        "update",
        "delete",
        "update",
    ]
    assert all(call[-2:] == ("-r", "weave") for call in scope_mutation_kcadm.calls)

    class ClientScopeRoleMappingKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            self.calls.append(arguments)
            endpoint = arguments[1]
            if endpoint == "client-scopes/filter-id/scope-mappings/realm":
                return []
            if (
                endpoint
                == "client-scopes/filter-id/scope-mappings/clients/weave-app-uuid"
            ):
                return []
            raise AssertionError(f"unexpected role mapping read: {endpoint}")

    desired_scope_roles = [
        {"key": "role:weaver-runtime", "name": "weaver-runtime"},
        *[
            {
                "key": f"role:{role_name}",
                "name": role_name,
                "scope": "client",
                "clientKey": "client:weave-app",
            }
            for role_name in ("owner", "admin", "member", "guest")
        ],
    ]
    observed_scope_roles = {
        role["key"]: {
            "id": f"{role['name']}-role-id",
            "name": role["name"],
        }
        for role in desired_scope_roles
    }
    all_human_role_refs = [
        "role:owner",
        "role:admin",
        "role:member",
        "role:guest",
    ]
    role_mapping_operations = identity_ops.client_scope_role_mapping_operations(
        ClientScopeRoleMappingKcadm(),
        "weave",
        [
            {
                "key": "scope:test-role-filter",
                "roleScopeRefs": all_human_role_refs,
            }
        ],
        {"scope:test-role-filter": {"id": "filter-id"}},
        desired_scope_roles,
        observed_scope_roles,
        {"client:weave-app": {"id": "weave-app-uuid"}},
    )
    client_role_endpoint = (
        "client-scopes/filter-id/scope-mappings/clients/weave-app-uuid"
    )
    assert [
        (operation.action, operation.key, operation.endpoint, operation.payload)
        for operation in role_mapping_operations
    ] == [
        (
            "map-client-scope-role",
            "scope:test-role-filter:role:admin",
            client_role_endpoint,
            [{"id": "admin-role-id", "name": "admin"}],
        ),
        (
            "map-client-scope-role",
            "scope:test-role-filter:role:guest",
            client_role_endpoint,
            [{"id": "guest-role-id", "name": "guest"}],
        ),
        (
            "map-client-scope-role",
            "scope:test-role-filter:role:member",
            client_role_endpoint,
            [{"id": "member-role-id", "name": "member"}],
        ),
        (
            "map-client-scope-role",
            "scope:test-role-filter:role:owner",
            client_role_endpoint,
            [{"id": "owner-role-id", "name": "owner"}],
        ),
    ]

    class ClientScopeRoleMutationKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[tuple[str, ...], object]] = []

        def call(self, *arguments: str, payload: object = None) -> None:
            self.calls.append((arguments, payload))

    role_mutation_kcadm = ClientScopeRoleMutationKcadm()
    for operation in role_mapping_operations:
        identity_ops.apply_operation(role_mutation_kcadm, "weave", operation)
    assert [call[0][0] for call in role_mutation_kcadm.calls] == [
        "create",
        "create",
        "create",
        "create",
    ]
    assert all(
        call[0][1] == client_role_endpoint for call in role_mutation_kcadm.calls
    )

    class StaleClientScopeRoleMappingKcadm(ClientScopeRoleMappingKcadm):
        def call(self, *arguments: str, payload: object = None) -> object:
            endpoint = arguments[1]
            if endpoint == "client-scopes/filter-id/scope-mappings/realm":
                return []
            if endpoint == client_role_endpoint:
                return [
                    {
                        "id": "guest-role-id",
                        "name": "guest",
                        "clientRole": True,
                    }
                ]
            raise AssertionError(f"unexpected role mapping read: {endpoint}")

    stale_role_operations = identity_ops.client_scope_role_mapping_operations(
        StaleClientScopeRoleMappingKcadm(),
        "weave",
        [
            {
                "key": "scope:test-role-filter",
                "roleScopeRefs": [
                    "role:owner",
                    "role:admin",
                    "role:member",
                ],
            }
        ],
        {"scope:test-role-filter": {"id": "filter-id"}},
        desired_scope_roles,
        observed_scope_roles,
        {"client:weave-app": {"id": "weave-app-uuid"}},
    )
    assert stale_role_operations[0] == identity_ops.Operation(
        "remove-client-scope-role",
        "scope:test-role-filter:managed-role:guest",
        client_role_endpoint,
        None,
        [{"id": "guest-role-id", "name": "guest", "clientRole": True}],
    )

    assert identity_ops.projected_fields_are_current(
        {"alias": "weave", "name": "Weave"},
        {"id": "organization-id", "alias": "weave", "name": "Weave"},
    )
    assert not identity_ops.projected_fields_are_current(
        {"alias": "weave", "name": "Weave"},
        {"id": "organization-id", "alias": "weave", "name": "Drift"},
    )
    hierarchy = identity_ops.flatten_groups(
        [{"id": "parent", "name": "people", "subGroups": [{"id": "child", "name": "members"}]}]
    )
    assert [(item["id"], item["_path"]) for item in hierarchy] == [
        ("parent", "/people"),
        ("child", "/people/members"),
    ]

    class GroupInventoryKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            self.calls.append(arguments)
            endpoint = arguments[1]
            if endpoint == "organizations/organization-id/groups":
                return [{"id": "parent", "name": "capabilities", "path": "/capabilities"}]
            if endpoint.endswith("/parent/children"):
                return [{"id": "child", "name": "weaver", "path": "/capabilities/weaver"}]
            if endpoint.endswith("/child/children"):
                return []
            raise AssertionError(f"unexpected organization group read: {endpoint}")

    inventory_kcadm = GroupInventoryKcadm()
    inventory = identity_ops.organization_group_inventory(
        inventory_kcadm,
        "organizations/organization-id/groups",
        "weave",
    )
    assert [(item["id"], item["_path"]) for item in inventory] == [
        ("parent", "/capabilities"),
        ("child", "/capabilities/weaver"),
    ]
    assert all("populateHierarchy=true" not in call for call in inventory_kcadm.calls)
    assert all("first=0" in call and "max=100" in call for call in inventory_kcadm.calls)

    class PermissionRelationshipsKcadm:
        def __init__(self) -> None:
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            self.calls.append(arguments)
            endpoint = arguments[1]
            if endpoint.endswith("/resources"):
                return [{"_id": "resource-id", "name": "organization-id"}]
            if endpoint.endswith("/scopes"):
                return [
                    {"id": "manage-id", "name": "manage"},
                    {"id": "view-id", "name": "view"},
                ]
            if endpoint.endswith("/associatedPolicies"):
                return [{"id": "policy-id", "name": "identity organization policy"}]
            raise AssertionError(f"unexpected permission relationship read: {endpoint}")

    relationship_kcadm = PermissionRelationshipsKcadm()
    assert identity_ops.permission_relationships(
        relationship_kcadm,
        "clients/admin/authz/resource-server/permission/scope/permission-id",
        "weave",
    ) == (
        {"organization-id"},
        {"manage", "view"},
        {"identity organization policy"},
    )
    assert [call[1].rsplit("/", 1)[-1] for call in relationship_kcadm.calls] == [
        "resources",
        "scopes",
        "associatedPolicies",
    ]

    child_create = identity_ops.organization_group_create_operation(
        {
            "key": "organization-group:weave-primary:capabilities-weaver",
            "path": "/capabilities/weaver",
            "parentGroupRef": "organization-group:weave-primary:capabilities",
        },
        "organizations/organization-id/groups",
        [
            {
                "id": "capabilities-id",
                "attributes": {
                    "weave.semantic-key": [
                        "organization-group:weave-primary:capabilities"
                    ]
                },
            }
        ],
    )
    assert child_create is not None
    assert child_create.endpoint == (
        "organizations/organization-id/groups/capabilities-id/children"
    )
    assert child_create.payload == {"name": "weaver"}
    assert identity_ops.organization_group_create_operation(
        {
            "key": "organization-group:weave-primary:capabilities-weaver",
            "path": "/capabilities/weaver",
            "parentGroupRef": "organization-group:weave-primary:capabilities",
        },
        "organizations/organization-id/groups",
        [],
    ) is None
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
    expected_roles = {"query-organizations", "query-users"}
    missing, remove = identity_ops.identity_admin_role_delta(
        {"manage-realm", "manage-organizations", "view-organizations", "query-groups"},
        expected_roles,
    )
    assert remove == {"manage-realm", "manage-organizations", "view-organizations", "query-groups"}
    assert missing == {"query-organizations", "query-users"}
    missing, remove = identity_ops.identity_admin_role_delta(expected_roles, expected_roles)
    assert not missing and not remove
    try:
        identity_ops.identity_admin_role_delta(expected_roles | {"impersonation"}, expected_roles)
    except identity_ops.IdentityOpsError:
        pass
    else:
        raise AssertionError("unmanaged broad role was silently removed or accepted")
    missing, remove = identity_ops.runtime_admin_role_delta(
        {"query-clients", "manage-clients"},
        {"create-client"},
    )
    assert missing == {"create-client"}
    assert remove == {"query-clients", "manage-clients"}
    workload_policy = {
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
    profiles, policies = identity_ops.workload_client_policy_payloads(
        [workload_policy]
    )
    assert profiles["profiles"][0]["executors"] == [
        {
            "executor": "weave-workload-client-registration-enforcer",
            "configuration": {},
        }
    ]
    assert policies["policies"][0]["conditions"] == [
        {"condition": "any-client", "configuration": {}}
    ]
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
    assert '"resourceType": "Users"' in source
    assert "expected_resource_names = {resource_type}" in source
    assert 'wanted["resources"] = sorted(requested_resource_ids)' in source
    assert '"scopes": ["view", "manage"]' in source
    assert '"scopes": ["view", "manage", "manage-group-membership"]' in source
    assert '"query-organizations"' in source
    assert '"query-users"' in source
    assert "identity_admin_role_delta(observed_names, expected)" in source
    assert '"remove-role"' in source and '"remove-roles"' in source
    assert "scope-mappings/clients/" in source
    assert "identity_admin_role_delta(" in source
    assert '"remove-client-scope-role"' in source
    assert '"Authorization": f"Basic {authorization}"' in source
    assert '"grant_type": "client_credentials"' in source
    assert '"client_secret":' not in source
    assert 'body.get("error") != "unauthorized_client"' in source
    assert 'client.get("serviceAccountsEnabled") is True' in source
    assert '"token.endpoint.auth.method": "client_secret_basic"' in source
    assert '"set-password"' not in source
    assert 'kcadm.call("reset-password"' not in source
    assert "probe_identity_admin_authorization(" in source
    assert "administration_read_probe_status(" in source
    assert '"primary-organization"' in source
    assert '"service-account-user"' in source
    assert "/reset-password" not in source
    assert '"map-org-group-role"' in source
    assert '"attach-client-scope"' in source
    assert '"detach-client-scope"' in source
    assert '"default-client-scopes"' in source
    assert '"optional-client-scopes"' in source
    assert "organization_group_create_operation(group, group_root, flat_groups)" in source
    assert '"id": staged["id"], "name": staged["name"]' not in source
    assert "Stage the managed resource at organization" not in source
    assert "organization_group_inventory(" in source
    assert "client_credentials_are_rejected(server, client_id, secret)" in source
    assert '"add-roles", "-r", realm, "--uusername", item["username"]' not in source
    renderer = (ROOT / "scripts/render_config.py").read_text(encoding="utf-8")
    assert '"weave.keycloak-desired-state/v2"' in renderer
    assert 'if "groups" in desired:' in renderer
    assert 'desired["organizationGroups"] =' not in renderer
    assert 'desired["fineGrainedAdminPermissions"] =' not in renderer
    assert '"externalContractAssignments"' not in renderer
    assert '"identityOpsManagedSurface"' not in renderer
    assert '"organizationInvitationLifecycle"' not in renderer
    assert "len(client_policies) != 1" in renderer
    assert "plan_workload_client_policy(" in source
    assert '"client-policies/profiles"' in source
    assert '"client-policies/policies"' in source
    assert '"create-client"' in source
    assert "runtime_admin_role_delta(" in source
    assert 'choices=("plan", "apply", "verify")' in source
    assert '"verification found a non-empty plan"' in source
    assert '"readback did not converge to an empty second plan; "' in source
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
