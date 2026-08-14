#!/usr/bin/env python3
"""Project the canonical semantic IAM baseline into Keycloak 26.7 import JSON.

This module is deliberately a renderer, not a reconciler.  It accepts the
secret-free semantic desired state plus the private JWKs owned by their
workloads, derives public JWKS projections, and emits one deterministic
``RealmRepresentation``.  No private credential is retained in the result.
"""

from __future__ import annotations

import hashlib
import json
import uuid
from typing import Any


class RealmProjectionError(RuntimeError):
    """The semantic baseline cannot be represented safely by Keycloak 26.7."""


KEYCLOAK_VERSION = "26.7.1"
PRIVATE_JWK_MEMBERS = frozenset({"d", "p", "q", "dp", "dq", "qi", "oth", "k"})
ORGANIZATION_ID_NAMESPACE = uuid.UUID("b72cdb67-84a6-5aaa-a30c-70c1f10f76c8")
WORKLOAD_POLICY_EXECUTOR = "weave-workload-client-registration-enforcer"
FRESH_START_MIGRATION_SCHEMA = "weave.keycloak-realm-migration-bundle/v1"
MACHINE_KEY_PROJECTIONS = {
    "secretref:keycloak/weave-backend-jwk": (
        "keycloak-weave-backend-jwk.json",
        "weave-backend.json",
    ),
    "secretref:keycloak/weave-mcp-server-jwk": (
        "keycloak-weave-mcp-server-jwk.json",
        "weave-mcp-server.json",
    ),
    "secretref:keycloak/weave-identity-admin-jwk": (
        "keycloak-weave-identity-admin-jwk.json",
        "weave-identity-admin.json",
    ),
    "secretref:keycloak/weave-agent-runtime-admin-jwk": (
        "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin",
        "weave-agent-runtime-admin.json",
    ),
}


def canonical_json(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def pretty_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
        "utf-8"
    )


def sha256_digest(payload: bytes) -> str:
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def deterministic_organization_id(semantic_key: str) -> str:
    """Return an artifact-only ID needed by Keycloak's FGAP import format."""
    if not semantic_key.startswith("organization:"):
        raise RealmProjectionError("organization has no canonical semantic identifier")
    return str(uuid.uuid5(ORGANIZATION_ID_NAMESPACE, semantic_key))


def public_jwks(private_jwk_value: object, *, owner: str) -> dict[str, object]:
    """Validate an owned signing JWK/JWKS and derive the public verification set."""
    if not isinstance(private_jwk_value, dict):
        raise RealmProjectionError(f"{owner} private JWK is not a JSON object")
    raw_keys = (
        private_jwk_value.get("keys")
        if "keys" in private_jwk_value
        else [private_jwk_value]
    )
    if not isinstance(raw_keys, list) or len(raw_keys) != 1:
        raise RealmProjectionError(f"{owner} must own exactly one active signing JWK")
    raw_key = raw_keys[0]
    if not isinstance(raw_key, dict):
        raise RealmProjectionError(f"{owner} private JWK is malformed")
    required = {"kty": "RSA", "use": "sig", "alg": "PS256"}
    if any(raw_key.get(name) != value for name, value in required.items()):
        raise RealmProjectionError(f"{owner} must use an RSA PS256 signing JWK")
    if not isinstance(raw_key.get("kid"), str) or not raw_key["kid"]:
        raise RealmProjectionError(f"{owner} private JWK has no key identifier")
    if not isinstance(raw_key.get("n"), str) or not raw_key["n"]:
        raise RealmProjectionError(f"{owner} private JWK has no RSA modulus")
    if raw_key.get("e") != "AQAB":
        raise RealmProjectionError(f"{owner} private JWK must use exponent AQAB")
    if not PRIVATE_JWK_MEMBERS.intersection(raw_key):
        raise RealmProjectionError(f"{owner} input is not an owned private JWK")
    operations = raw_key.get("key_ops")
    if operations is not None and operations != ["sign"]:
        raise RealmProjectionError(f"{owner} private JWK must be signing-only")
    return {
        "keys": [
            {
                "alg": "PS256",
                "e": "AQAB",
                "key_ops": ["verify"],
                "kid": raw_key["kid"],
                "kty": "RSA",
                "n": raw_key["n"],
                "use": "sig",
            }
        ]
    }


def validate_public_jwks(value: object, *, owner: str) -> dict[str, object]:
    """Validate the public-only projection consumed by the realm renderer."""
    if not isinstance(value, dict) or set(value) != {"keys"}:
        raise RealmProjectionError(f"{owner} public JWKS is malformed")
    keys = value.get("keys")
    if not isinstance(keys, list) or len(keys) != 1 or not isinstance(keys[0], dict):
        raise RealmProjectionError(f"{owner} must expose exactly one public verification JWK")
    key = keys[0]
    if PRIVATE_JWK_MEMBERS.intersection(key):
        raise RealmProjectionError(f"{owner} public JWKS contains private key material")
    expected = {
        "alg": "PS256",
        "e": "AQAB",
        "key_ops": ["verify"],
        "kty": "RSA",
        "use": "sig",
    }
    if any(key.get(name) != expected_value for name, expected_value in expected.items()):
        raise RealmProjectionError(f"{owner} public JWKS is not an RSA PS256 verification key")
    if not isinstance(key.get("kid"), str) or not key["kid"]:
        raise RealmProjectionError(f"{owner} public JWKS has no key identifier")
    if not isinstance(key.get("n"), str) or not key["n"]:
        raise RealmProjectionError(f"{owner} public JWKS has no RSA modulus")
    allowed = {"alg", "e", "key_ops", "kid", "kty", "n", "use"}
    if set(key) != allowed:
        raise RealmProjectionError(f"{owner} public JWKS contains unsupported members")
    return value


def _mapper(mapper: dict[str, Any], client_ids: dict[str, str]) -> dict[str, object]:
    mapper_type = mapper.get("mapperType")
    mapper_provider = {
        "organization-group-membership": "oidc-organization-group-membership-mapper",
        "audience": "oidc-audience-mapper",
        "role": "oidc-usermodel-realm-role-mapper",
        "user-session-note": "oidc-usersessionmodel-note-mapper",
    }.get(str(mapper_type))
    if mapper_provider is None:
        raise RealmProjectionError(f"unsupported protocol mapper type: {mapper_type}")
    if mapper_type == "organization-group-membership":
        config: dict[str, str] = {
            "addGroupRoleMappings": str(mapper.get("addGroupRoleMappings", False)).lower()
        }
    elif mapper_type == "audience":
        client_ref = mapper.get("includedClientKey")
        if client_ref is not None:
            audience = client_ids.get(str(client_ref))
            if audience is None:
                raise RealmProjectionError(
                    f"audience mapper references unavailable client: {client_ref}"
                )
            config = {"included.client.audience": audience}
        else:
            audience = mapper.get("includedCustomAudience")
            if not isinstance(audience, str) or not audience:
                raise RealmProjectionError("custom audience mapper has no audience")
            config = {"included.custom.audience": audience}
    elif mapper_type == "role":
        if (
            mapper.get("roleRef") != "role:weaver-runtime"
            or mapper.get("claimName") != "realm_access.roles"
        ):
            raise RealmProjectionError("workload role mapper violates the pinned contract")
        config = {
            "claim.name": "realm_access.roles",
            "introspection.token.claim": "false",
            "jsonType.label": "String",
            "multivalued": "true",
        }
    else:
        config = {
            "access.tokenResponse.claim": "false",
            "claim.name": str(mapper["claimName"]),
            "introspection.token.claim": "false",
            "jsonType.label": str(mapper["claimValueType"]),
            "lightweight.claim": "false",
            "user.session.note": str(mapper["sessionNote"]),
        }
    config.update(
        {
            "access.token.claim": str(mapper.get("addToAccessToken", False)).lower(),
            "id.token.claim": str(mapper.get("addToIdToken", False)).lower(),
            "userinfo.token.claim": str(mapper.get("addToUserInfo", False)).lower(),
        }
    )
    return {
        "config": config,
        "name": str(mapper["name"]),
        "protocol": "openid-connect",
        "protocolMapper": mapper_provider,
    }


def _client(
    client: dict[str, Any],
    scope_names: dict[str, str],
    public_keys: dict[str, dict[str, object]],
) -> dict[str, object]:
    result: dict[str, object] = {
        field: client[field]
        for field in (
            "clientId",
            "name",
            "description",
            "protocol",
            "enabled",
            "publicClient",
            "serviceAccountsEnabled",
            "standardFlowEnabled",
            "implicitFlowEnabled",
            "directAccessGrantsEnabled",
            "fullScopeAllowed",
            "redirectUris",
            "webOrigins",
        )
        if field in client
    }
    result["defaultClientScopes"] = [
        scope_names.get(str(ref), str(ref).removeprefix("builtin-scope:"))
        for ref in client.get("defaultClientScopes", [])
    ]
    result["optionalClientScopes"] = [
        scope_names.get(str(ref), str(ref).removeprefix("builtin-scope:"))
        for ref in client.get("optionalClientScopes", [])
    ]
    authentication = client.get("authenticationMethod")
    if authentication == "none":
        if client.get("publicClient") is not True:
            raise RealmProjectionError(f"{client['key']} has inconsistent public authentication")
        attributes: dict[str, str] = {}
    elif authentication == "private_key_jwt":
        key_ref = str(client.get("keyRef", ""))
        jwks = public_keys.get(key_ref)
        if jwks is None:
            raise RealmProjectionError(f"{client['key']} has no derived public JWKS")
        result["clientAuthenticatorType"] = "client-jwt"
        # An explicit empty value prevents Keycloak generating an unused shared
        # secret while retaining no credential material in the baseline.
        result["secret"] = ""
        attributes = {
            "jwks.string": canonical_json(jwks).decode("utf-8"),
            "token.endpoint.auth.method": "private_key_jwt",
            "token.endpoint.auth.signing.alg": "PS256",
            "use.jwks.string": "true",
            "use.jwks.url": "false",
        }
    else:
        raise RealmProjectionError(
            f"{client['key']} uses unsupported first-party authentication: {authentication}"
        )
    attribute_mappings = {
        "pkceMethod": "pkce.code.challenge.method",
        "postLogoutRedirectUris": "post.logout.redirect.uris",
        "standardTokenExchangeEnabled": "standard.token.exchange.enabled",
        "allowRefreshTokenInTokenExchange": "allow.refresh.token.in.standard.token.exchange",
        "useRefreshTokensForClientCredentials": "client_credentials.use_refresh_token",
        "rfc9068AccessToken": "access.token.header.type.rfc9068",
        "accessTokenLifespanSeconds": "access.token.lifespan",
    }
    for source, target in attribute_mappings.items():
        if source not in client:
            continue
        value = client[source]
        if isinstance(value, bool):
            value = str(value).lower()
        elif isinstance(value, list):
            value = "##".join(str(item) for item in value)
        else:
            value = str(value)
        attributes[target] = value
    if attributes:
        result["attributes"] = attributes
    return result


def _organization_groups(
    groups: list[dict[str, Any]], roles: dict[str, dict[str, Any]]
) -> list[dict[str, object]]:
    by_key = {str(group["key"]): group for group in groups}

    def representation(group: dict[str, Any]) -> dict[str, object]:
        path = str(group["path"])
        name = path.rsplit("/", 1)[-1]
        result: dict[str, object] = {"name": name}
        realm_roles: list[str] = []
        client_roles: dict[str, list[str]] = {}
        for role_ref in group.get("roleRefs", []):
            role = roles.get(str(role_ref))
            if role is None:
                raise RealmProjectionError(f"organization group references unknown role: {role_ref}")
            if role.get("scope", "realm") == "realm":
                realm_roles.append(str(role["name"]))
            else:
                client_roles.setdefault(str(role["clientId"]), []).append(str(role["name"]))
        if realm_roles:
            result["realmRoles"] = sorted(realm_roles)
        if client_roles:
            result["clientRoles"] = {
                key: sorted(values) for key, values in sorted(client_roles.items())
            }
        children = [
            child
            for child in groups
            if child.get("parentGroupRef") == group.get("key")
        ]
        if children:
            result["subGroups"] = [representation(child) for child in sorted(children, key=lambda x: str(x["path"]))]
        return result

    roots = [group for group in groups if group.get("parentGroupRef") is None]
    for group in groups:
        parent_ref = group.get("parentGroupRef")
        if parent_ref is not None and str(parent_ref) not in by_key:
            raise RealmProjectionError(f"organization group has unknown parent: {parent_ref}")
    return [representation(group) for group in sorted(roots, key=lambda x: str(x["path"]))]


def _client_policy(policies: object) -> tuple[dict[str, object], dict[str, object]]:
    if policies == []:
        return ({"profiles": []}, {"policies": []})
    if not isinstance(policies, list) or len(policies) != 1 or not isinstance(policies[0], dict):
        raise RealmProjectionError("baseline must declare one workload registration policy")
    policy = policies[0]
    required = {
        "conditionProvider": "any-client",
        "executorProvider": WORKLOAD_POLICY_EXECUTOR,
        "executorVersion": "1",
        "keycloakVersion": KEYCLOAK_VERSION,
        "runtimeAdminClientKey": "client:weave-agent-runtime-admin",
        "registrationProvider": "openid-connect",
        "identifierMetadata": "client_name",
        "workloadRoleRef": "role:weaver-runtime",
    }
    if any(policy.get(name) != value for name, value in required.items()):
        raise RealmProjectionError("workload registration policy differs from the pinned contract")
    name = str(policy.get("name", ""))
    if not name or policy.get("enabled") is not True:
        raise RealmProjectionError("workload registration policy must be named and enabled")
    profile_name = f"{name}-profile"
    return (
        {
            "profiles": [
                {
                    "description": "Weave-owned, version-pinned per-Cell workload registration policy.",
                    "executors": [
                        {"configuration": {}, "executor": WORKLOAD_POLICY_EXECUTOR}
                    ],
                    "name": profile_name,
                }
            ]
        },
        {
            "policies": [
                {
                    "conditions": [{"condition": "any-client", "configuration": {}}],
                    "description": "Restrict authenticated OIDC Dynamic Client Registration to Weave per-Cell workloads.",
                    "enabled": True,
                    "name": name,
                    "profiles": [profile_name],
                }
            ]
        },
    )


def _validate_deferred_fgap(
    desired: dict[str, Any],
    organization_ids: dict[str, str],
    client_ids: dict[str, str],
) -> None:
    fgap = desired.get("fineGrainedAdminPermissions")
    if not isinstance(fgap, dict) or fgap.get("enabled") is not True:
        raise RealmProjectionError("fine-grained admin permissions must be enabled")
    subject_names: dict[str, str] = {}
    for subject in fgap.get("subjectPolicies", []):
        if not isinstance(subject, dict) or subject.get("policyType") != "user":
            raise RealmProjectionError("only Keycloak user FGAP subject policies are supported")
        client_ref = str(subject.get("subjectServiceAccountClientKey", ""))
        client_id = client_ids.get(client_ref)
        if client_id is None:
            raise RealmProjectionError("FGAP subject references an unknown service account")
        if client_ref != "client:weave-identity-admin":
            raise RealmProjectionError("FGAP may grant only the identity-admin service account")
        if subject.get("logic") != "POSITIVE":
            raise RealmProjectionError("FGAP subject policy must use positive logic")
        subject_names[str(subject["key"])] = str(subject["name"])
    if set(subject_names) != {"admin-policy:identity-admin"}:
        raise RealmProjectionError("FGAP must declare only the identity-admin subject policy")
    observed_permissions: set[tuple[object, ...]] = set()
    for permission in fgap.get("permissions", []):
        if not isinstance(permission, dict):
            raise RealmProjectionError("FGAP permission is malformed")
        resource_type = str(permission.get("resourceType", ""))
        resource_refs = [str(value) for value in permission.get("resourceRefs", [])]
        all_resources = permission.get("allResources") is True
        if resource_type == "Organizations":
            if all_resources or len(resource_refs) != 1:
                raise RealmProjectionError("organization FGAP must target one canonical organization")
            resource_id = organization_ids.get(resource_refs[0])
            if resource_id is None:
                raise RealmProjectionError("organization FGAP references an unknown organization")
            expected_scopes = {"view", "manage"}
        elif resource_type == "Users":
            if not all_resources or resource_refs:
                raise RealmProjectionError("user FGAP must target the built-in all-users resource")
            expected_scopes = {"view", "manage", "manage-group-membership"}
        else:
            raise RealmProjectionError(f"unsupported FGAP resource type: {resource_type}")
        if set(str(scope) for scope in permission.get("scopes", [])) != expected_scopes:
            raise RealmProjectionError(f"{resource_type} FGAP scopes violate the pinned contract")
        policy_refs = tuple(sorted(str(value) for value in permission.get("policyRefs", [])))
        for policy_ref in policy_refs:
            if policy_ref not in subject_names:
                raise RealmProjectionError("FGAP permission references an unknown subject policy")
        observed_permissions.add(
            (resource_type, tuple(resource_refs), all_resources, policy_refs)
        )
    expected_permissions = {
        (
            "Organizations",
            ("organization:weave-primary",),
            False,
            ("admin-policy:identity-admin",),
        ),
        (
            "Users",
            (),
            True,
            ("admin-policy:identity-admin",),
        ),
    }
    if observed_permissions != expected_permissions:
        raise RealmProjectionError("FGAP permissions differ from the pinned lifecycle boundary")


def project_realm(
    desired: dict[str, Any],
    public_keys: dict[str, dict[str, object]],
) -> dict[str, object]:
    """Return a Keycloak 26.7 ``RealmRepresentation`` for a Fresh Start."""
    if desired.get("apiVersion") != "weave.keycloak-desired-state/v3":
        raise RealmProjectionError("renderer accepts only canonical desired-state v3")
    if desired.get("keycloakVersion") != KEYCLOAK_VERSION:
        raise RealmProjectionError(f"renderer targets Keycloak {KEYCLOAK_VERSION} only")
    realm = desired.get("realm")
    if not isinstance(realm, dict) or realm.get("name") != "weave":
        raise RealmProjectionError("canonical Weave realm is missing")
    client_values = desired.get("clients")
    scope_values = desired.get("clientScopes")
    role_values = desired.get("roles")
    organization_values = desired.get("organizations")
    group_values = desired.get("organizationGroups")
    if not all(isinstance(value, list) for value in (client_values, scope_values, role_values, organization_values, group_values)):
        raise RealmProjectionError("canonical realm collections are malformed")
    clients = [value for value in client_values if isinstance(value, dict)]
    scopes = [value for value in scope_values if isinstance(value, dict)]
    roles = [value for value in role_values if isinstance(value, dict)]
    organizations = [value for value in organization_values if isinstance(value, dict)]
    groups = [value for value in group_values if isinstance(value, dict)]
    if any(len(values) != len(source) for values, source in ((clients, client_values), (scopes, scope_values), (roles, role_values), (organizations, organization_values), (groups, group_values))):
        raise RealmProjectionError("canonical realm collection contains a malformed entry")
    client_ids = {str(client["key"]): str(client["clientId"]) for client in clients}
    scope_names = {str(scope["key"]): str(scope["name"]) for scope in scopes}
    role_by_key: dict[str, dict[str, Any]] = {}
    realm_roles: list[dict[str, object]] = []
    client_roles: dict[str, list[dict[str, object]]] = {}
    for role in roles:
        projected_role = {"name": str(role["name"])}
        if role.get("description") is not None:
            projected_role["description"] = str(role["description"])
        if role.get("scope", "realm") == "realm":
            realm_roles.append(projected_role)
            role_by_key[str(role["key"])] = {**role, "clientId": None}
        elif role.get("scope") == "client":
            client_id = client_ids.get(str(role.get("clientKey", "")))
            if client_id is None:
                raise RealmProjectionError("client role references an unknown client")
            client_roles.setdefault(client_id, []).append(projected_role)
            role_by_key[str(role["key"])] = {**role, "clientId": client_id}
        else:
            raise RealmProjectionError("role has unsupported scope")
    projected_scopes: list[dict[str, object]] = []
    scope_mappings: list[dict[str, object]] = []
    for scope in scopes:
        projected_scopes.append(
            {
                "attributes": {
                    "include.in.token.scope": str(scope.get("includeInTokenScope", False)).lower()
                },
                "name": str(scope["name"]),
                "protocol": str(scope.get("protocol", "openid-connect")),
                "protocolMappers": [
                    _mapper(mapper, client_ids)
                    for mapper in scope.get("mappers", [])
                    if isinstance(mapper, dict)
                ],
            }
        )
        role_refs = scope.get("roleScopeRefs", [])
        if role_refs:
            names: list[str] = []
            for role_ref in role_refs:
                role = role_by_key.get(str(role_ref))
                if role is None or role.get("scope", "realm") != "realm":
                    raise RealmProjectionError("client-scope role mapping must reference a realm role")
                names.append(str(role["name"]))
            scope_mappings.append({"clientScope": str(scope["name"]), "roles": sorted(names)})
    organization_ids = {
        str(organization["key"]): deterministic_organization_id(str(organization["key"]))
        for organization in organizations
    }
    projected_organizations: list[dict[str, object]] = []
    for organization in organizations:
        semantic_key = str(organization["key"])
        organization_groups = [
            group for group in groups if group.get("organizationRef") == semantic_key
        ]
        projected_organizations.append(
            {
                "alias": str(organization["alias"]),
                "description": str(organization.get("description", "")),
                "enabled": True,
                "groups": _organization_groups(organization_groups, role_by_key),
                "id": organization_ids[semantic_key],
                "name": str(organization["name"]),
                "redirectUrl": str(organization["redirectUri"]),
            }
        )
    smtp = realm.get("smtp")
    if not isinstance(smtp, dict):
        raise RealmProjectionError("realm SMTP contract is missing")
    smtp_server: dict[str, str] = {
        "from": str(smtp.get("fromAddress", "")),
        "fromDisplayName": str(smtp.get("fromDisplayName", "")),
        "host": str(smtp["host"]),
        "port": str(smtp["port"]),
        "ssl": str(smtp.get("ssl", False)).lower(),
        "starttls": str(smtp.get("startTls", False)).lower(),
    }
    if "username" in smtp:
        smtp_server["user"] = str(smtp["username"])
        if smtp.get("passwordVaultRef") != "${vault.smtp-password}":
            raise RealmProjectionError("SMTP password must use the canonical Keycloak File Vault alias")
        smtp_server["password"] = "${vault.smtp-password}"
    elif "passwordVaultRef" in smtp:
        raise RealmProjectionError("anonymous SMTP must not declare a password")
    profiles, policies = _client_policy(desired.get("clientPolicies"))
    projected_clients = [_client(client, scope_names, public_keys) for client in clients]
    # Validate the complete semantic FGAP contract, but do not embed the
    # organization-specific policies. Keycloak 26.7 imports authorization
    # settings before organizations and rejects its own exported shape for a
    # specific organization. The realm switch still creates the default-deny
    # FGAP schema. The versioned migration bundle records the mandatory,
    # post-import operation and its exact blocker instead of widening the
    # permission to every organization.
    _validate_deferred_fgap(desired, organization_ids, client_ids)
    users: list[dict[str, object]] = []
    client_scope_mappings: dict[str, list[dict[str, object]]] = {}
    for grant in desired.get("serviceAccountRoleGrants", []):
        if not isinstance(grant, dict):
            raise RealmProjectionError("service-account role grant is malformed")
        client_ref = str(grant.get("clientKey", ""))
        client_id = client_ids.get(client_ref)
        if client_id is None:
            raise RealmProjectionError("service-account grant references an unknown client")
        realm_management_roles: list[str] = []
        for role_ref in grant.get("roleRefs", []):
            prefix = "builtin-role:realm-management:"
            if not str(role_ref).startswith(prefix):
                raise RealmProjectionError("service-account grant references an unsupported role")
            realm_management_roles.append(str(role_ref).removeprefix(prefix))
        users.append(
            {
                "clientRoles": {"realm-management": sorted(realm_management_roles)},
                "enabled": True,
                "serviceAccountClientId": client_id,
                "username": f"service-account-{client_id}",
            }
        )
        # fullScopeAllowed=false is mandatory. Keycloak therefore intersects
        # the service-account assignment above with this client's allowed role
        # scope before issuing tokens or evaluating Admin REST authority. In
        # Keycloak's realm-import shape the map key owns the role, while the
        # nested `client` identifies the scope container receiving it.
        client_scope_mappings.setdefault("realm-management", []).append(
            {
                "client": client_id,
                "roles": sorted(realm_management_roles),
            }
        )
    actions = desired.get("requiredActions")
    expected_actions = {
        ("VERIFY_EMAIL", "Verify Email", True, False),
        ("webauthn-register-passwordless", "Register a passkey", True, False),
    }
    observed_actions = {
        (
            str(action.get("alias")),
            str(action.get("name")),
            action.get("enabled"),
            action.get("defaultAction"),
        )
        for action in actions
        if isinstance(action, dict)
    } if isinstance(actions, list) else set()
    if observed_actions != expected_actions:
        raise RealmProjectionError(
            "required actions differ from Keycloak 26.7 stock actions; explicit projection is unsupported"
        )
    representation: dict[str, object] = {
        "accessTokenLifespan": int(realm.get("accessTokenLifespanSeconds", 300)),
        "adminPermissionsEnabled": bool(realm.get("adminPermissionsEnabled", False)),
        "attributes": {
            # Preserve all Keycloak 26.7 built-in client scopes while importing
            # the explicitly declared Weave scopes.
            "CreateDefaultClientScopes": "true",
            "frontendUrl": str(realm["frontendUrl"]),
        },
        "clientPolicies": policies,
        "clientProfiles": profiles,
        "clientScopeMappings": {
            role_client: sorted(
                mappings,
                key=lambda mapping: str(mapping["client"]),
            )
            for role_client, mappings in sorted(client_scope_mappings.items())
        },
        "clientScopes": projected_scopes,
        "clients": projected_clients,
        "duplicateEmailsAllowed": bool(realm.get("duplicateEmailsAllowed", False)),
        "enabled": bool(realm.get("enabled", True)),
        "eventsListeners": list(realm.get("eventListeners", ["jboss-logging"])),
        "loginWithEmailAllowed": bool(realm.get("loginWithEmailAllowed", True)),
        "organizations": projected_organizations,
        "organizationsEnabled": bool(realm.get("organizationsEnabled", False)),
        "realm": str(realm["name"]),
        "registrationAllowed": bool(realm.get("registrationAllowed", False)),
        "roles": {
            "client": {
                key: sorted(values, key=lambda value: str(value["name"]))
                for key, values in sorted(client_roles.items())
            },
            "realm": sorted(realm_roles, key=lambda value: str(value["name"])),
        },
        "scopeMappings": scope_mappings,
        "smtpServer": smtp_server,
        "sslRequired": str(realm.get("sslRequired", "external")),
        "users": users,
        "verifyEmail": bool(realm.get("verifyEmail", True)),
    }
    assert_secret_free(representation)
    return representation


def assert_secret_free(value: object, path: str = "$") -> None:
    """Reject private key material or non-vault shared secrets in an artifact."""
    if isinstance(value, list):
        for index, item in enumerate(value):
            assert_secret_free(item, f"{path}[{index}]")
        return
    if not isinstance(value, dict):
        return
    for key, item in value.items():
        item_path = f"{path}.{key}"
        if key in PRIVATE_JWK_MEMBERS:
            raise RealmProjectionError(f"private JWK material reached realm artifact at {item_path}")
        if key == "secret" and item != "":
            raise RealmProjectionError(f"shared client secret reached realm artifact at {item_path}")
        if key == "password" and item != "${vault.smtp-password}":
            raise RealmProjectionError(f"non-vault password reached realm artifact at {item_path}")
        assert_secret_free(item, item_path)


def fresh_start_migration_bundle(
    desired: dict[str, Any], baseline_digest: str
) -> dict[str, object]:
    """Describe the honest empty-realm migration accompanying the full baseline."""
    if not baseline_digest.startswith("sha256:"):
        raise RealmProjectionError("baseline artifact digest is malformed")
    baseline_revision = desired.get("provenance", {}).get("baselineRevision")
    if not isinstance(baseline_revision, str) or not baseline_revision.startswith("sha256:"):
        raise RealmProjectionError("semantic baseline revision is missing")
    fgap = desired.get("fineGrainedAdminPermissions")
    if not isinstance(fgap, dict):
        raise RealmProjectionError("semantic FGAP contract is missing")
    fgap_digest = "sha256:" + hashlib.sha256(canonical_json(fgap)).hexdigest()
    return {
        "apiVersion": FRESH_START_MIGRATION_SCHEMA,
        "applicability": "after-fresh-start-realm-import",
        "baselineArtifactDigest": baseline_digest,
        "containsSecretValues": False,
        "fromBaselineRevision": None,
        "keycloakVersion": KEYCLOAK_VERSION,
        "operations": [
            {
                "blockedBy": "keycloak-26.7-imports-client-authorization-before-organizations",
                "desiredStateDigest": fgap_digest,
                "desiredStatePointer": "/fineGrainedAdminPermissions",
                "id": "fgap-v2-primary-organization-post-import",
                "phase": "post-realm-import",
                "status": "requires-qualified-admin-rest-executor",
                "type": "keycloak-fgap-v2",
            }
        ],
        "reason": (
            "Keycloak 26.7 cannot import a specific-organization FGAP permission "
            "in the same RealmRepresentation because authorization settings are "
            "processed before organizations. The baseline remains default-deny; "
            "an exact post-import Admin REST executor is required."
        ),
        "status": "blocked-post-import-operation",
        "toBaselineRevision": baseline_revision,
    }
