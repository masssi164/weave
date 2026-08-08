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
sys.path.insert(0, str(ROOT / "keycloak"))
MODULE_PATH = ROOT / "keycloak/identity_ops.py"
SPEC = importlib.util.spec_from_file_location("identity_ops", MODULE_PATH)
assert SPEC and SPEC.loader
identity_ops = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = identity_ops
SPEC.loader.exec_module(identity_ops)
KEY_MODULE_PATH = ROOT / "keycloak/identity_admin_key_init.py"
KEY_SPEC = importlib.util.spec_from_file_location("identity_admin_key_init", KEY_MODULE_PATH)
assert KEY_SPEC and KEY_SPEC.loader
identity_admin_key_init = importlib.util.module_from_spec(KEY_SPEC)
sys.modules[KEY_SPEC.name] = identity_admin_key_init
KEY_SPEC.loader.exec_module(identity_admin_key_init)


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
    assert realm["id"] == "weave"
    assert "frontendUrl" not in realm
    assert realm["attributes"]["frontendUrl"] == "https://auth.weave.local"
    assert realm["verifyEmail"] is True
    assert realm["smtpServer"]["port"] == "1025"
    external_realm = identity_ops.realm_payload(
        {
            "name": "weave",
            "verifyEmail": True,
            "smtp": {
                "host": "smtp.example.invalid",
                "port": 465,
                "fromAddress": "noreply@example.invalid",
                "fromDisplayName": "Weave",
                "ssl": True,
                "startTls": False,
                "username": "weave-smtp",
                "passwordVaultRef": "${vault.smtp-password}",
            },
        }
    )
    assert external_realm["smtpServer"]["user"] == "weave-smtp"
    assert external_realm["smtpServer"]["auth"] == "true"
    assert external_realm["smtpServer"]["password"] == "${vault.smtp-password}"
    try:
        identity_ops.realm_payload(
            {
                "name": "weave",
                "verifyEmail": True,
                "smtp": {
                    "host": "smtp.example.invalid",
                    "port": 465,
                    "username": "weave-smtp",
                    "passwordVaultRef": "plaintext-is-forbidden",
                },
            }
        )
    except identity_ops.IdentityOpsError:
        pass
    else:
        raise AssertionError("realm payload accepted a non-Vault SMTP password")
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
    assert identity_ops.access_token_role_projection(
        f"test.{token_claims}.signature"
    ) == (set(), {"realm-management": {"create-client"}})
    try:
        identity_ops.access_token_role_projection("malformed")
        raise AssertionError("malformed role projection was accepted")
    except identity_ops.IdentityOpsError as error:
        assert "token withheld" in str(error)

    broad_token_claims = base64.urlsafe_b64encode(
        json.dumps(
            {
                "realm_access": {"roles": ["manage-realm"]},
                "resource_access": {
                    "realm-management": {"roles": ["create-client"]},
                    "other-client": {"roles": ["create-client"]},
                },
            },
            separators=(",", ":"),
        ).encode("utf-8")
    ).rstrip(b"=").decode("ascii")
    assert identity_ops.access_token_role_projection(
        f"test.{broad_token_claims}.signature"
    ) == (
        {"manage-realm"},
        {
            "realm-management": {"create-client"},
            "other-client": {"create-client"},
        },
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
        runtime_admin_contract = {
            "key": "client:weave-agent-runtime-admin",
            "clientId": "weave-agent-runtime-admin",
            "authenticationMethod": "private_key_jwt",
            "keyRef": "secretref:keycloak/weave-agent-runtime-admin-jwk",
        }
        runtime_admin = identity_ops.client_payload(runtime_admin_contract)
        runtime_admin_create = identity_ops.client_creation_payload(
            runtime_admin_contract
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
    assert runtime_admin_create["secret"] == ""
    assert runtime_admin_create["attributes"]["weave.desired-digest"] == (
        identity_ops.marker(
            "client:weave-agent-runtime-admin",
            runtime_admin,
            list_values=False,
        )["weave.desired-digest"]
    )
    assert "secret" not in runtime_admin

    identity_public_jwks = {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "PS256",
                "kid": "identity-admin-test",
                "key_ops": ["verify"],
                "n": "public-modulus",
                "e": "AQAB",
            }
        ]
    }
    identity_ops.private_value = lambda _path: json.dumps(identity_public_jwks)
    identity_admin_contract = {
        "key": "client:weave-identity-admin",
        "clientId": "weave-identity-admin",
        "authenticationMethod": "private_key_jwt",
        "keyRef": "secretref:keycloak/weave-identity-admin-jwk",
    }
    try:
        identity_admin_payload = identity_ops.client_payload(identity_admin_contract)
        assert (
            json.loads(identity_admin_payload["attributes"]["jwks.string"])
            == identity_public_jwks
        )
        identity_ops.private_value = lambda _path: json.dumps(runtime_private_jwk)
        try:
            identity_ops.client_payload(identity_admin_contract)
            raise AssertionError("Identity Ops accepted identity-admin private key material")
        except identity_ops.IdentityOpsError as error:
            assert "public JWKS only" in str(error)
    finally:
        identity_ops.private_value = original_private_value

    captured_identity_secret_probe: list[tuple[str, str]] = []
    original_client_credentials = identity_ops.client_credentials_token_response
    identity_ops.client_credentials_token_response = (
        lambda _server, _realm, client_id, secret: (
            captured_identity_secret_probe.append((client_id, secret))
            or (401, {"error": "invalid_client"})
        )
    )
    try:
        identity_ops.probe_client_credentials(
            "http://keycloak:8080",
            "weave",
            [{**identity_admin_contract, "serviceAccountsEnabled": True}],
            "https://auth.weave.local/realms/weave",
        )
    finally:
        identity_ops.client_credentials_token_response = original_client_credentials
    assert captured_identity_secret_probe == [
        ("weave-identity-admin", "deliberately-invalid-no-shared-secret")
    ]

    class PrivateKeyJwtSecretKcadm:
        def __init__(self, value: object) -> None:
            self.value = value
            self.calls: list[tuple[str, ...]] = []

        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            self.calls.append(arguments)
            return {"value": self.value}

    stale_secret_kcadm = PrivateKeyJwtSecretKcadm("withheld-fixture-secret")
    clear_secret = identity_ops.private_key_jwt_secret_reconciliation(
        stale_secret_kcadm,
        "weave",
        {
            "key": "client:weave-agent-runtime-admin",
            "authenticationMethod": "private_key_jwt",
        },
        {"id": "runtime-admin-id"},
    )
    assert clear_secret == identity_ops.Operation(
        "clear-unused-secret",
        "client:weave-agent-runtime-admin:unused-client-secret",
        "clients",
        "runtime-admin-id",
        {"secret": ""},
    )
    assert stale_secret_kcadm.calls == [
        (
            "get",
            "clients/runtime-admin-id/client-secret",
            "-r",
            "weave",
        )
    ]
    assert identity_ops.private_key_jwt_secret_reconciliation(
        PrivateKeyJwtSecretKcadm(""),
        "weave",
        {
            "key": "client:weave-agent-runtime-admin",
            "authenticationMethod": "private_key_jwt",
        },
        {"id": "runtime-admin-id"},
    ) is None

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

    class RuntimeRoleInventoryKcadm:
        def call(self, *arguments: str, payload: object = None) -> object:
            assert payload is None
            endpoint = arguments[1]
            if endpoint == "clients":
                assert arguments == (
                    "get",
                    "clients",
                    "-r",
                    "weave",
                    "-q",
                    "max=10000",
                )
                return [
                    {"id": "realm-management-id", "clientId": "realm-management"},
                    {"id": "other-client-id", "clientId": "other-client"},
                ]
            roles = {
                "users/runtime-account/role-mappings/realm": [
                    {
                        "name": "unexpected-realm-role",
                        "containerId": "weave",
                        "clientRole": False,
                    }
                ],
                "users/runtime-account/role-mappings/realm/composite": [
                    {
                        "name": "unexpected-realm-role",
                        "containerId": "weave",
                        "clientRole": False,
                    }
                ],
                (
                    "users/runtime-account/role-mappings/clients/"
                    "realm-management-id"
                ): [
                    {
                        "name": "create-client",
                        "containerId": "realm-management-id",
                        "clientRole": True,
                    }
                ],
                (
                    "users/runtime-account/role-mappings/clients/"
                    "realm-management-id/composite"
                ): [
                    {
                        "name": "create-client",
                        "containerId": "realm-management-id",
                        "clientRole": True,
                    }
                ],
                (
                    "users/runtime-account/role-mappings/clients/other-client-id"
                ): [
                    {
                        "name": "create-client",
                        "containerId": "other-client-id",
                        "clientRole": True,
                    }
                ],
                (
                    "users/runtime-account/role-mappings/clients/"
                    "other-client-id/composite"
                ): [
                    {
                        "name": "create-client",
                        "containerId": "other-client-id",
                        "clientRole": True,
                    }
                ],
            }
            return roles[endpoint]

    direct_roles, effective_roles = identity_ops.runtime_admin_role_inventory(
        RuntimeRoleInventoryKcadm(),
        "weave",
        "runtime-account",
    )
    assert identity_ops.RoleIdentity(
        "client", "realm-management-id", "create-client"
    ) in effective_roles
    assert identity_ops.DirectRoleMapping(
        identity_ops.RoleIdentity(
            "client", "other-client-id", "create-client"
        ),
        "other-client",
    ) in direct_roles
    assert identity_ops.DirectRoleMapping(
        identity_ops.RoleIdentity(
            "realm", "weave", "unexpected-realm-role"
        ),
        None,
    ) in direct_roles
    expected_runtime_role = identity_ops.RoleIdentity(
        "client", "realm-management-id", "create-client"
    )
    direct_extras, missing_expected = (
        identity_ops.runtime_admin_role_reconciliation(
            direct_roles,
            effective_roles,
            expected_runtime_role,
        )
    )
    assert direct_extras
    assert not missing_expected
    assert identity_ops.runtime_admin_role_reconciliation(
        set(),
        set(),
        expected_runtime_role,
    ) == (set(), True)
    try:
        identity_ops.runtime_admin_role_reconciliation(
            {
                identity_ops.DirectRoleMapping(
                    expected_runtime_role,
                    "realm-management",
                )
            },
            {
                expected_runtime_role,
                identity_ops.RoleIdentity(
                    "client", "other-client-id", "manage-clients"
                ),
            },
            expected_runtime_role,
        )
    except identity_ops.IdentityOpsError as error:
        assert "effective role expansion" in str(error)
    else:
        raise AssertionError("unexplained effective runtime-admin role was accepted")

    class RemoveRealmRoleKcadm:
        def __init__(self) -> None:
            self.arguments: tuple[str, ...] = ()

        def call(self, *arguments: str, payload: object = None) -> None:
            assert payload is None
            self.arguments = arguments

    remove_realm = RemoveRealmRoleKcadm()
    identity_ops.apply_operation(
        remove_realm,
        "weave",
        identity_ops.Operation(
            "remove-role",
            "realm-role:test",
            "remove-roles",
            None,
            {
                "username": "service-account-weave-agent-runtime-admin",
                "clientId": None,
                "roleName": "unexpected-realm-role",
            },
        ),
    )
    assert "--cclientid" not in remove_realm.arguments
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
    identity_ops_service = compose.split("\n  identity-ops:\n", 1)[1].split("\n  mailpit:\n", 1)[0]
    backend_service = compose.split("\n  backend:\n", 1)[1].split("\n  mcp-secret-check:\n", 1)[0]
    key_init_service = compose.split("\n  identity-admin-key-init:\n", 1)[1].split("\n  identity-ops:\n", 1)[0]
    assert "weave-identity-admin-private-jwk.json" not in identity_ops_service
    assert "keycloak-weave-identity-admin:/" not in identity_ops_service
    assert "identity-admin-public-jwks.json" in identity_ops_service
    assert "weave-identity-admin-private-jwk.json" in backend_service
    assert "/authority/output" in key_init_service
    assert "/authority/private/weave-identity-admin-private-jwk.json" in key_init_service
    assert "identity-admin-client-secret" not in compose
    assert "keycloak-weave-identity-admin:/" not in compose
    runtime = (ROOT / "scripts/compose_runtime.py").read_text(encoding="utf-8")
    dockerfile = (ROOT / "keycloak/Dockerfile.identity-ops").read_text(encoding="utf-8")
    assert "FROM ${WEAVE_KEYCLOAK_BASE}" in dockerfile
    assert "FROM ${WEAVE_UBI9_BASE}" in dockerfile
    assert "FROM registry.access.redhat.com/ubi9" not in dockerfile
    builder = (ROOT / "scripts/build_identity_ops_image.py").read_text(encoding="utf-8")
    assert "keycloakBaseResolved" in builder and "ubi9BaseResolved" in builder
    assert "specs/weave-specs.lock.json" in builder and "WEAVE_SPEC_DIGEST" in builder
    assert '"specDigest": spec_digest' in builder
    assert "pinned_base" in builder and "must declare one exact OCI digest" in builder
    assert "build inputs differ from the selected candidate commit" in builder
    assert "user: \"${WEAVE_RUNTIME_UID:-1000}:${WEAVE_RUNTIME_GID:-1000}\"" in compose
    assert "no-new-privileges:true" in compose and "cap_drop:" in compose
    assert "/var/run/docker.sock" not in compose
    assert "sudo" not in runtime
    assert "WEAVE_TEST_USERS_FILE" not in runtime
    assert "test-users.json" not in runtime
    source = MODULE_PATH.read_text(encoding="utf-8")
    key_source = KEY_MODULE_PATH.read_text(encoding="utf-8")
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
    assert "identity administration realm input must contain public JWKS only" in source
    assert "deliberately-invalid-no-shared-secret" in source
    assert "private JWK; Fresh Start or explicit rotation is required" in key_source
    assert "values withheld" in key_source
    assert "Authorization" not in key_source
    assert "private_value" not in key_source
    assert '"set-password"' not in source
    assert 'kcadm.call("reset-password"' not in source
    assert "verify_identity_admin_public_key_boundary(" in source
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

    with tempfile.TemporaryDirectory() as temporary:
        authority = Path(temporary)
        desired_source = authority / "desired.json"
        desired_source.write_text(json.dumps({
            "revision": "old",
            "clients": [
                {
                    "key": "client:weave-identity-admin",
                    "clientId": "weave-identity-admin",
                    "serviceAccountsEnabled": True,
                    "authenticationMethod": "client_secret_basic",
                    "secretRef": "secretref:keycloak/weave-identity-admin",
                }
            ],
            "serviceAccountRoleGrants": [{
                "clientKey": "client:weave-identity-admin",
                "roleRefs": ["query-organizations", "query-users"],
            }],
            "fineGrainedAdminPermissions": identity_ops.IDENTITY_ADMIN_FGAP_CONTRACT,
        }), encoding="utf-8")
        upgraded = identity_admin_key_init.upgraded_desired_state(desired_source)
        upgraded_client = upgraded["clients"][0]
        assert upgraded_client["authenticationMethod"] == "private_key_jwt"
        assert upgraded_client["keyRef"] == identity_admin_key_init.IDENTITY_ADMIN_KEY_REF
        assert "secretRef" not in upgraded_client
        assert upgraded["serviceAccountRoleGrants"] == json.loads(
            desired_source.read_text(encoding="utf-8")
        )["serviceAccountRoleGrants"]
        assert upgraded["fineGrainedAdminPermissions"] == identity_ops.IDENTITY_ADMIN_FGAP_CONTRACT
        invalid_private = authority / "weave-identity-admin-private-jwk.json"
        invalid_private.write_text("legacy-shared-secret", encoding="utf-8")
        invalid_private.chmod(0o600)
        try:
            identity_admin_key_init.load_private(invalid_private)
            raise AssertionError("legacy shared secret was accepted as a private JWK")
        except identity_admin_key_init.KeyPreparationError as error:
            assert "Fresh Start or explicit rotation is required" in str(error)
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
