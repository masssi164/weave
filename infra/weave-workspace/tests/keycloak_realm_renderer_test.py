#!/usr/bin/env python3
"""Focused contract tests for the deterministic Keycloak realm projection."""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "keycloak"))

from realm_renderer import (  # noqa: E402
    RealmProjectionError,
    assert_secret_free,
    deterministic_organization_id,
    fresh_start_migration_bundle,
    pretty_json,
    project_realm,
    public_jwks,
    sha256_digest,
    validate_public_jwks,
)


def private_jwk() -> dict[str, object]:
    return {
        "alg": "PS256",
        "d": "test-private-value-never-rendered",
        "e": "AQAB",
        "key_ops": ["sign"],
        "kid": "test-key",
        "kty": "RSA",
        "n": "test-public-modulus",
        "use": "sig",
    }


def desired_state() -> dict[str, object]:
    clients = [
        {
            "key": "client:weave-app",
            "clientId": "weave-app",
            "protocol": "openid-connect",
            "enabled": True,
            "publicClient": True,
            "standardFlowEnabled": True,
            "implicitFlowEnabled": False,
            "serviceAccountsEnabled": False,
            "directAccessGrantsEnabled": False,
            "authenticationMethod": "none",
            "fullScopeAllowed": False,
            "pkceMethod": "S256",
            "redirectUris": ["com.example.weave:/oauthredirect"],
            "postLogoutRedirectUris": ["com.example.weave:/logout"],
            "webOrigins": [],
            "defaultClientScopes": [
                "builtin-scope:basic",
                "scope:weave-workspace",
            ],
            "optionalClientScopes": [],
        }
    ]
    for key, client_id, key_ref in (
        (
            "client:weave-backend",
            "https://api.weave.test/api",
            "secretref:keycloak/weave-backend-jwk",
        ),
        (
            "client:weave-mcp-server",
            "weave-mcp-server",
            "secretref:keycloak/weave-mcp-server-jwk",
        ),
        (
            "client:weave-identity-admin",
            "weave-identity-admin",
            "secretref:keycloak/weave-identity-admin-jwk",
        ),
        (
            "client:weave-agent-runtime-admin",
            "weave-agent-runtime-admin",
            "secretref:keycloak/weave-agent-runtime-admin-jwk",
        ),
    ):
        clients.append(
            {
                "key": key,
                "clientId": client_id,
                "protocol": "openid-connect",
                "enabled": True,
                "publicClient": False,
                "standardFlowEnabled": False,
                "implicitFlowEnabled": False,
                "serviceAccountsEnabled": True,
                "directAccessGrantsEnabled": False,
                "authenticationMethod": "private_key_jwt",
                "fullScopeAllowed": False,
                "redirectUris": [],
                "postLogoutRedirectUris": [],
                "webOrigins": [],
                "defaultClientScopes": [],
                "optionalClientScopes": [],
                "keyRef": key_ref,
            }
        )
    return {
        "apiVersion": "weave.keycloak-desired-state/v3",
        "keycloakVersion": "26.7.0",
        "provenance": {
            "baselineRevision": "sha256:" + "1" * 64,
            "overlayRevision": "sha256:" + "2" * 64,
        },
        "realm": {
            "name": "weave",
            "frontendUrl": "https://auth.weave.test",
            "enabled": True,
            "organizationsEnabled": True,
            "adminPermissionsEnabled": True,
            "registrationAllowed": False,
            "verifyEmail": True,
            "sslRequired": "external",
            "loginWithEmailAllowed": True,
            "duplicateEmailsAllowed": False,
            "accessTokenLifespanSeconds": 300,
            "eventListeners": ["jboss-logging"],
            "smtp": {
                "host": "smtp.weave.test",
                "port": 465,
                "fromAddress": "noreply@weave.test",
                "fromDisplayName": "Weave",
                "ssl": True,
                "startTls": False,
                "username": "weave-smtp",
                "passwordVaultRef": "${vault.smtp-password}",
            },
        },
        "organizations": [
            {
                "key": "organization:weave-primary",
                "name": "Weave",
                "alias": "weave",
                "description": "Primary organization",
                "redirectUri": "https://weave.test",
            }
        ],
        "organizationGroups": [
            {
                "key": f"organization-group:weave-primary:{name}",
                "organizationRef": "organization:weave-primary",
                "path": f"/{name}",
                "parentGroupRef": None,
                "roleRefs": [f"role:{name.removesuffix('s')}"]
                if name in {"owners", "admins", "members", "guests"}
                else [],
            }
            for name in ("owners", "admins", "members", "guests", "capabilities")
        ]
        + [
            {
                "key": "organization-group:weave-primary:capabilities-weaver",
                "organizationRef": "organization:weave-primary",
                "path": "/capabilities/weaver",
                "parentGroupRef": "organization-group:weave-primary:capabilities",
                "roleRefs": [],
            }
        ],
        "roles": [
            {"key": "role:weaver-runtime", "name": "weaver-runtime", "scope": "realm"}
        ]
        + [
            {
                "key": f"role:{name}",
                "name": name,
                "scope": "client",
                "clientKey": "client:weave-app",
            }
            for name in ("owner", "admin", "member", "guest")
        ],
        "clientScopes": [
            {
                "key": "scope:weave-workspace",
                "name": "weave:workspace",
                "protocol": "openid-connect",
                "includeInTokenScope": True,
                "roleScopeRefs": [],
                "mappers": [
                    {
                        "name": "weave-organization-groups",
                        "mapperType": "organization-group-membership",
                        "addGroupRoleMappings": True,
                        "addToIdToken": False,
                        "addToAccessToken": True,
                        "addToUserInfo": False,
                    }
                ],
            },
            {
                "key": "scope:weaver-runtime-workload",
                "name": "weaver-runtime-workload",
                "protocol": "openid-connect",
                "includeInTokenScope": False,
                "roleScopeRefs": ["role:weaver-runtime"],
                "mappers": [],
            },
        ],
        "clients": clients,
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
                "roleRefs": ["builtin-role:realm-management:create-client"],
            },
        ],
        "fineGrainedAdminPermissions": {
            "enabled": True,
            "subjectPolicies": [
                {
                    "key": "admin-policy:identity-admin",
                    "name": "weave-identity-admin user policy",
                    "policyType": "user",
                    "logic": "POSITIVE",
                    "subjectServiceAccountClientKey": "client:weave-identity-admin",
                }
            ],
            "permissions": [
                {
                    "name": "weave-identity-admin primary organization",
                    "resourceType": "Organizations",
                    "resourceRefs": ["organization:weave-primary"],
                    "allResources": False,
                    "scopes": ["view", "manage"],
                    "policyRefs": ["admin-policy:identity-admin"],
                },
                {
                    "name": "weave-identity-admin users",
                    "resourceType": "Users",
                    "resourceRefs": [],
                    "allResources": True,
                    "scopes": ["view", "manage", "manage-group-membership"],
                    "policyRefs": ["admin-policy:identity-admin"],
                },
            ],
        },
        "clientPolicies": [
            {
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
        "requiredActions": [
            {
                "alias": "VERIFY_EMAIL",
                "name": "Verify Email",
                "enabled": True,
                "defaultAction": False,
            },
            {
                "alias": "webauthn-register-passwordless",
                "name": "Register a passkey",
                "enabled": True,
                "defaultAction": False,
            },
        ],
    }


def run() -> None:
    private = private_jwk()
    public = public_jwks(private, owner="test-owner")
    assert public == {
        "keys": [
            {
                "alg": "PS256",
                "e": "AQAB",
                "key_ops": ["verify"],
                "kid": "test-key",
                "kty": "RSA",
                "n": "test-public-modulus",
                "use": "sig",
            }
        ]
    }
    assert validate_public_jwks(public, owner="test-owner") == public
    desired = desired_state()
    public_by_ref = {
        str(client["keyRef"]): public
        for client in desired["clients"]
        if isinstance(client, dict) and "keyRef" in client
    }
    first = project_realm(copy.deepcopy(desired), public_by_ref)
    second = project_realm(copy.deepcopy(desired), public_by_ref)
    assert pretty_json(first) == pretty_json(second)
    assert first["smtpServer"]["user"] == "weave-smtp"
    assert first["smtpServer"]["password"] == "${vault.smtp-password}"
    assert first["organizations"][0]["id"] == deterministic_organization_id(
        "organization:weave-primary"
    )
    assert "admin-permissions" not in {
        client["clientId"] for client in first["clients"]
    }, "blocked organization FGAP must not be projected as a misleading client import"
    for client in first["clients"]:
        if client.get("clientAuthenticatorType") != "client-jwt":
            continue
        assert client["secret"] == ""
        attributes = client["attributes"]
        assert attributes["token.endpoint.auth.method"] == "private_key_jwt"
        jwks = json.loads(attributes["jwks.string"])
        assert jwks == public
        assert not {"d", "p", "q", "dp", "dq", "qi"}.intersection(
            jwks["keys"][0]
        )
    serialized = pretty_json(first)
    assert b"test-private-value-never-rendered" not in serialized
    assert_secret_free(first)
    baseline_digest = sha256_digest(serialized)
    migration = fresh_start_migration_bundle(desired, baseline_digest)
    assert migration["baselineArtifactDigest"] == baseline_digest
    assert migration["containsSecretValues"] is False
    assert migration["status"] == "blocked-post-import-operation"
    assert migration["operations"] == [
        {
            "blockedBy": "keycloak-26.7-imports-client-authorization-before-organizations",
            "desiredStateDigest": migration["operations"][0]["desiredStateDigest"],
            "desiredStatePointer": "/fineGrainedAdminPermissions",
            "id": "fgap-v2-primary-organization-post-import",
            "phase": "post-realm-import",
            "status": "requires-qualified-admin-rest-executor",
            "type": "keycloak-fgap-v2",
        }
    ]
    assert b"test-private-value-never-rendered" not in pretty_json(migration)

    public_only = copy.deepcopy(private)
    public_only.pop("d")
    try:
        public_jwks(public_only, owner="public-only")
    except RealmProjectionError:
        pass
    else:
        raise AssertionError("public-only JWK was accepted as an owned private credential")

    leaked_public = copy.deepcopy(public)
    leaked_public["keys"][0]["d"] = "private"
    try:
        validate_public_jwks(leaked_public, owner="leaked-public")
    except RealmProjectionError:
        pass
    else:
        raise AssertionError("private JWK material was accepted in a public projection")

    leaked = copy.deepcopy(first)
    leaked["clients"][0]["secret"] = "shared-secret"
    try:
        assert_secret_free(leaked)
    except RealmProjectionError:
        pass
    else:
        raise AssertionError("realm artifact accepted a shared client secret")

    wrong_vault = copy.deepcopy(desired)
    wrong_vault["realm"]["smtp"]["passwordVaultRef"] = "plaintext"
    try:
        project_realm(wrong_vault, public_by_ref)
    except RealmProjectionError:
        pass
    else:
        raise AssertionError("realm artifact accepted a non-vault SMTP password")


if __name__ == "__main__":
    run()
    print("keycloak realm renderer contract test: PASS")
