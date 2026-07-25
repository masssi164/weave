#!/usr/bin/env python3
"""Rootless, one-shot Keycloak desired-state reconciliation through kcadm.sh."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class IdentityOpsError(RuntimeError):
    pass


SECRET_REF_FILES = {
    "secretref:keycloak/weave-backend-jwk": "keycloak-weave-backend-jwk.json",
    "secretref:keycloak/weave-mcp-server-jwk": "keycloak-weave-mcp-server-jwk.json",
    "secretref:keycloak/weave-identity-admin": "keycloak-weave-identity-admin",
    "secretref:keycloak/weave-agent-runtime-admin": "keycloak-weave-agent-runtime-admin",
    "secretref:keycloak/nextcloud": "keycloak-nextcloud",
    "secretref:keycloak/matrix-mas": "keycloak-matrix-mas",
}
SECRET_CLIENT_FILES = {
    "weave-identity-admin": "keycloak-weave-identity-admin",
    "weave-agent-runtime-admin": "keycloak-weave-agent-runtime-admin",
    "nextcloud": "keycloak-nextcloud",
    "matrix-mas": "keycloak-matrix-mas",
}


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def digest(value: object) -> str:
    return "sha256:" + hashlib.sha256(canonical(value)).hexdigest()


def private_value(path: Path) -> str:
    metadata = path.lstat()
    if path.is_symlink() or not path.is_file() or metadata.st_mode & 0o777 != 0o600:
        raise IdentityOpsError(f"credential file is unavailable: {path}")
    value = path.read_text(encoding="utf-8").strip()
    if not value:
        raise IdentityOpsError(f"credential file is empty: {path}")
    return value


@dataclass(frozen=True)
class Operation:
    action: str
    key: str
    endpoint: str
    resource_id: str | None
    payload: Any

    def support_safe(self) -> dict[str, object]:
        return {
            "action": self.action,
            "key": self.key,
            "desiredDigest": digest(self.payload),
        }


class Kcadm:
    def __init__(self, executable: str, config: Path) -> None:
        self.executable = executable
        self.config = config

    def call(self, *arguments: str, payload: Any = None) -> Any:
        if len(arguments) < 2:
            raise IdentityOpsError("kcadm operation requires a command and endpoint")
        if arguments[0] in {"config", "create", "get", "update", "delete"}:
            command = [
                self.executable,
                arguments[0],
                arguments[1],
                "--config",
                str(self.config),
                *arguments[2:],
            ]
        else:
            command = [
                self.executable,
                arguments[0],
                "--config",
                str(self.config),
                *arguments[1:],
            ]
        if payload is not None:
            command.extend(("-b", json.dumps(payload, separators=(",", ":"))))
        result = subprocess.run(command, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode != 0:
            raise IdentityOpsError(f"kcadm operation failed ({arguments[0]} {arguments[1]}), output withheld")
        output = result.stdout.strip()
        return json.loads(output) if output else None

    def authenticate(self, server: str, client: str, secret: str) -> None:
        self.call(
            "config", "credentials", "--server", server, "--realm", "master",
            "--client", client, "--secret", secret,
        )


def marker(key: str, payload: dict[str, Any], *, list_values: bool) -> dict[str, Any]:
    projection = dict(payload)
    attributes = dict(projection.get("attributes") or {})
    attributes.pop("weave.semantic-key", None)
    attributes.pop("weave.desired-digest", None)
    if attributes:
        projection["attributes"] = attributes
    else:
        projection.pop("attributes", None)
    values = {"weave.semantic-key": key, "weave.desired-digest": digest(projection)}
    return {name: [value] for name, value in values.items()} if list_values else values


def marked_payload(key: str, payload: dict[str, Any], *, list_values: bool) -> dict[str, Any]:
    result = dict(payload)
    attributes = dict(result.get("attributes") or {})
    attributes.update(marker(key, result, list_values=list_values))
    result["attributes"] = attributes
    return result


def is_current(key: str, desired: dict[str, Any], observed: dict[str, Any], *, list_values: bool) -> bool:
    attributes = observed.get("attributes") or {}
    expected = marker(key, desired, list_values=list_values)
    return all(attributes.get(name) == value for name, value in expected.items())


def exact(items: list[dict[str, Any]], key: str, description: str) -> dict[str, Any] | None:
    if len(items) > 1:
        raise IdentityOpsError(f"semantic lookup is ambiguous for {description} {key}")
    return items[0] if items else None


def realm_payload(realm: dict[str, Any]) -> dict[str, Any]:
    result = {
        "realm": realm["name"],
        "enabled": realm.get("enabled", True),
        "organizationsEnabled": realm.get("organizationsEnabled", False),
        "adminPermissionsEnabled": realm.get("adminPermissionsEnabled", False),
        "registrationAllowed": realm.get("registrationAllowed", False),
        "loginWithEmailAllowed": realm.get("loginWithEmailAllowed", True),
        "duplicateEmailsAllowed": realm.get("duplicateEmailsAllowed", False),
        "sslRequired": realm.get("sslRequired", "external"),
        "accessTokenLifespan": realm.get("accessTokenLifespanSeconds", 300),
        "eventsListeners": realm.get("eventListeners", ["jboss-logging"]),
    }
    if realm.get("frontendUrl"):
        result["attributes"] = {"frontendUrl": realm["frontendUrl"]}
    smtp = realm.get("smtp")
    if isinstance(smtp, dict):
        smtp_server = {
            "host": str(smtp["host"]),
            "port": str(smtp["port"]),
            "from": str(smtp.get("fromAddress", "")),
            "fromDisplayName": str(smtp.get("fromDisplayName", "")),
            "ssl": str(smtp.get("ssl", False)).lower(),
            "starttls": str(smtp.get("startTls", False)).lower(),
        }
        result["smtpServer"] = smtp_server
    return result


def client_payload(client: dict[str, Any], scope_names: dict[str, str]) -> dict[str, Any]:
    allowed = (
        "clientId", "name", "description", "protocol", "enabled", "publicClient",
        "serviceAccountsEnabled", "standardFlowEnabled", "implicitFlowEnabled",
        "directAccessGrantsEnabled", "fullScopeAllowed",
        "redirectUris", "webOrigins", "defaultClientScopes", "optionalClientScopes",
    )
    result = {name: client[name] for name in allowed if name in client}
    for attachment in ("defaultClientScopes", "optionalClientScopes"):
        if attachment in result:
            result[attachment] = [
                scope_names.get(str(reference), str(reference).removeprefix("builtin-scope:"))
                for reference in result[attachment]
            ]
    if client.get("authenticationMethod") == "client_secret_basic":
        result["clientAuthenticatorType"] = "client-secret"
        attributes = {"token.endpoint.auth.method": "client_secret_basic"}
    elif client.get("authenticationMethod") == "private_key_jwt":
        result["clientAuthenticatorType"] = "client-jwt"
        key_ref = client.get("keyRef")
        filename = SECRET_REF_FILES.get(str(key_ref))
        if filename is None:
            raise IdentityOpsError(f"{client['key']} has no supported private_key_jwt SecretRef")
        private_jwk = json.loads(private_value(secret_path(filename)))
        jwks = private_jwk if isinstance(private_jwk, dict) and "keys" in private_jwk else {"keys": [private_jwk]}
        public_keys = []
        for key in jwks.get("keys", []):
            if not isinstance(key, dict):
                raise IdentityOpsError(f"{client['key']} JWK set is malformed")
            public_keys.append(
                {
                    name: value
                    for name, value in key.items()
                    if name not in {"d", "p", "q", "dp", "dq", "qi", "oth", "k"}
                }
            )
        attributes = {
            "token.endpoint.auth.method": "private_key_jwt",
            "use.jwks.url": "false",
            "jwks.string": json.dumps({"keys": public_keys}, separators=(",", ":"), sort_keys=True),
        }
    else:
        attributes = {}
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


def secret_path(filename: str) -> Path:
    staged = Path("/evidence/secret-updates") / filename
    return staged if staged.is_file() else Path("/run/secrets") / filename


def requires_rotation(key: str, observed: object, expected: str | None, rotation_epoch: str | None) -> bool:
    if isinstance(observed, str) and expected is not None and observed == expected:
        return False
    if not rotation_epoch:
        raise IdentityOpsError(f"SecretRef mismatch for {key}; routine apply refuses implicit rotation")
    return True


def identity_admin_role_delta(observed: set[str], expected: set[str]) -> tuple[set[str], set[str]]:
    unexpected = observed - expected
    retired = {
        "manage-realm",
        "manage-organizations",
        "view-organizations",
        "query-groups",
        "query-users",
    }
    if unexpected - retired:
        raise IdentityOpsError("identity admin has unmanaged realm-management roles")
    return expected - observed, unexpected & retired


def flatten_groups(groups: list[dict[str, Any]], parent: str = "") -> list[dict[str, Any]]:
    flattened: list[dict[str, Any]] = []
    for group in groups:
        path = str(group.get("path") or f"{parent}/{group['name']}")
        flattened.append({**group, "_path": path})
        flattened.extend(flatten_groups(group.get("subGroups") or [], path))
    return flattened


def role_mapping(
    role_ref: str,
    roles_by_key: dict[str, dict[str, Any]],
    clients_by_key: dict[str, dict[str, Any]],
) -> tuple[str, dict[str, Any]] | None:
    role = roles_by_key.get(role_ref)
    if role is None:
        return None
    if role.get("_scope") == "client":
        client = clients_by_key.get(str(role["_clientKey"]))
        if client is None:
            return None
        return f"clients/{client['id']}", {"id": role["id"], "name": role["name"]}
    return "realm", {"id": role["id"], "name": role["name"]}


def mapper_payload(mapper: dict[str, Any]) -> dict[str, Any]:
    common = {
        "name": mapper["name"],
        "protocol": "openid-connect",
        "protocolMapper": (
            "oidc-group-membership-mapper"
            if mapper["mapperType"] == "group-membership"
            else "oidc-audience-mapper"
        ),
    }
    if mapper["mapperType"] == "group-membership":
        config = {
            "claim.name": mapper["claimName"],
            "full.path": str(mapper.get("fullPath", True)).lower(),
        }
    elif mapper["mapperType"] == "audience":
        config = {"included.custom.audience": mapper["includedCustomAudience"]}
    else:
        raise IdentityOpsError(f"unsupported protocol mapper type: {mapper['mapperType']}")
    config.update(
        {
            "id.token.claim": str(mapper.get("addToIdToken", False)).lower(),
            "access.token.claim": str(mapper.get("addToAccessToken", False)).lower(),
            "userinfo.token.claim": str(mapper.get("addToUserInfo", False)).lower(),
        }
    )
    return {**common, "config": config}


def plan(kcadm: Kcadm, desired: dict[str, Any], rotation_epoch: str | None = None) -> list[Operation]:
    operations: list[Operation] = []
    if desired.get("apiVersion") != "weave.keycloak-desired-state/v2" or "groups" in desired:
        raise IdentityOpsError("Identity Ops accepts only canonical desired-state v2 without legacy realm groups")
    if desired.get("clientPolicies") != []:
        raise IdentityOpsError("custom clientPolicies are unmanaged; stock Standard Token Exchange V2 requires an empty list")
    if desired.get("keycloakVersion") != "26.7.0":
        raise IdentityOpsError("desired state must target the pinned official Keycloak 26.7.0 distribution")
    realm = desired["realm"]
    realm_name = realm["name"]
    observed_realms = kcadm.call("get", "realms") or []
    observed_realm = exact(
        [item for item in observed_realms if item.get("realm") == realm_name],
        realm["key"],
        "realm",
    )
    wanted_realm = realm_payload(realm)
    if observed_realm is None:
        operations.append(Operation("create", realm["key"], "realms", None, marked_payload(realm["key"], wanted_realm, list_values=False)))
        return operations
    if not is_current(realm["key"], wanted_realm, observed_realm, list_values=False):
        operations.append(Operation("update", realm["key"], f"realms/{realm_name}", realm_name, marked_payload(realm["key"], wanted_realm, list_values=False)))

    observed_actions = kcadm.call("get", "authentication/required-actions", "-r", realm_name) or []
    for action in desired.get("requiredActions", []):
        observed = exact(
            [item for item in observed_actions if item.get("alias") == action["alias"]],
            action["key"],
            "required action",
        )
        if observed is None:
            raise IdentityOpsError(f"pinned Keycloak is missing required action {action['alias']}")
        wanted = {
            "alias": action["alias"],
            "name": action["name"],
            "enabled": action.get("enabled", True),
            "defaultAction": action.get("defaultAction", False),
        }
        if any(observed.get(name) != value for name, value in wanted.items()):
            operations.append(
                Operation(
                    "update",
                    action["key"],
                    "authentication/required-actions",
                    action["alias"],
                    wanted,
                )
            )

    scope_names = {
        str(scope["key"]): str(scope["name"])
        for scope in desired.get("clientScopes", [])
    }
    clients_by_key: dict[str, dict[str, Any]] = {}
    for client in desired.get("clients", []):
        key = client["key"]
        observed = exact(
            kcadm.call("get", "clients", "-r", realm_name, "-q", f"clientId={client['clientId']}") or [],
            key,
            "client",
        )
        wanted = client_payload(client, scope_names)
        if observed is None:
            operations.append(Operation("create", key, "clients", None, marked_payload(key, wanted, list_values=False)))
        else:
            clients_by_key[key] = observed
            if not is_current(key, wanted, observed, list_values=False):
                operations.append(Operation("update", key, "clients", str(observed["id"]), marked_payload(key, wanted, list_values=False)))
            secret_ref = client.get("secretRef")
            if secret_ref:
                if client.get("authenticationMethod") != "client_secret_basic":
                    raise IdentityOpsError(f"{key} must use client_secret_basic")
                filename = SECRET_REF_FILES.get(secret_ref)
                if filename:
                    observed_secret = kcadm.call("get", f"clients/{observed['id']}/client-secret", "-r", realm_name) or {}
                    expected_path = secret_path(filename)
                    expected_secret = private_value(expected_path) if expected_path.is_file() else None
                    if requires_rotation(key, observed_secret.get("value"), expected_secret, rotation_epoch):
                        operations.append(
                            Operation(
                                "rotate-secret",
                                f"{key}:secret-generation",
                                "client-secret",
                                str(observed["id"]),
                                {
                                    "filename": filename,
                                    "clientId": client["clientId"],
                                    "rotationEpoch": rotation_epoch,
                                },
                            )
                        )

    observed_scopes = kcadm.call("get", "client-scopes", "-r", realm_name) or []
    scopes_by_key: dict[str, dict[str, Any]] = {}
    for scope in desired.get("clientScopes", []):
        key = scope["key"]
        wanted = {"name": scope["name"], "protocol": scope.get("protocol", "openid-connect")}
        observed = exact(
            [item for item in observed_scopes if item.get("name") == scope["name"]],
            key,
            "client scope",
        )
        if observed is None:
            operations.append(Operation("create", key, "client-scopes", None, marked_payload(key, wanted, list_values=False)))
            continue
        scopes_by_key[key] = observed
        if not is_current(key, wanted, observed, list_values=False):
            operations.append(Operation("update", key, "client-scopes", str(observed["id"]), marked_payload(key, wanted, list_values=False)))
        mapper_endpoint = f"client-scopes/{observed['id']}/protocol-mappers/models"
        observed_mappers = kcadm.call("get", mapper_endpoint, "-r", realm_name) or []
        for mapper in scope.get("mappers", []):
            wanted_mapper = mapper_payload(mapper)
            observed_mapper = exact(
                [item for item in observed_mappers if item.get("name") == mapper["name"]],
                mapper["key"],
                "protocol mapper",
            )
            if observed_mapper is None:
                operations.append(Operation("create", mapper["key"], mapper_endpoint, None, wanted_mapper))
            elif (
                observed_mapper.get("protocolMapper") != wanted_mapper["protocolMapper"]
                or observed_mapper.get("config") != wanted_mapper["config"]
            ):
                operations.append(
                    Operation(
                        "update",
                        mapper["key"],
                        mapper_endpoint,
                        str(observed_mapper["id"]),
                        {**wanted_mapper, "id": observed_mapper["id"]},
                    )
                )

    all_roles = kcadm.call("get", "roles", "-r", realm_name) or []
    roles_by_key: dict[str, dict[str, Any]] = {}
    for role in desired.get("roles", []):
        key = role["key"]
        wanted = {"name": role["name"]}
        endpoint = "roles"
        observed_roles = all_roles
        if role.get("scope") == "client":
            client = clients_by_key.get(role["clientKey"])
            if client is None:
                continue
            endpoint = f"clients/{client['id']}/roles"
            observed_roles = kcadm.call("get", endpoint, "-r", realm_name) or []
        observed = exact([item for item in observed_roles if item.get("name") == role["name"]], key, "role")
        if observed is None:
            operations.append(Operation("create", key, endpoint, None, wanted))
        else:
            roles_by_key[key] = {
                **observed,
                "_scope": role.get("scope", "realm"),
                "_clientKey": role.get("clientKey"),
            }

    for scope in desired.get("clientScopes", []):
        observed_scope = scopes_by_key.get(scope["key"])
        if observed_scope is None:
            continue
        mapping_endpoint = f"client-scopes/{observed_scope['id']}/scope-mappings/realm"
        observed_mappings = kcadm.call("get", mapping_endpoint, "-r", realm_name) or []
        observed_names = {item.get("name") for item in observed_mappings}
        for role_ref in scope.get("roleScopeRefs", []):
            role = roles_by_key.get(role_ref)
            if role is None or role.get("_scope") != "realm":
                continue
            if role["name"] not in observed_names:
                operations.append(
                    Operation(
                        "map-client-scope-role",
                        f"{scope['key']}:{role_ref}",
                        mapping_endpoint,
                        None,
                        [{"id": role["id"], "name": role["name"]}],
                    )
                )

    observed_organizations = kcadm.call("get", "organizations", "-r", realm_name) or []
    organizations_by_key: dict[str, dict[str, Any]] = {}
    for organization in desired.get("organizations", []):
        key = organization["key"]
        wanted = {
            name: organization[name]
            for name in ("name", "alias", "description")
            if name in organization
        }
        redirect_url = organization.get("redirectUrl") or organization.get("redirectUri")
        if redirect_url:
            wanted["redirectUrl"] = redirect_url
        observed = exact(
            [item for item in observed_organizations if item.get("alias") == organization["alias"]],
            key,
            "organization",
        )
        if observed is None:
            operations.append(Operation("create", key, "organizations", None, marked_payload(key, wanted, list_values=True)))
        else:
            organizations_by_key[key] = observed
            if not is_current(key, wanted, observed, list_values=True):
                operations.append(Operation("update", key, "organizations", str(observed["id"]), marked_payload(key, wanted, list_values=True)))

    for group in desired.get("organizationGroups", []):
        organization = organizations_by_key.get(group["organizationRef"])
        if organization is None:
            continue
        organization_id = str(organization["id"])
        group_root = f"organizations/{organization_id}/groups"
        observed_groups = kcadm.call(
            "get", group_root, "-r", realm_name,
            "-q", "populateHierarchy=true", "-q", "briefRepresentation=false",
        ) or []
        flat_groups = flatten_groups(observed_groups)
        key = group["key"]
        wanted = {"name": group["path"].rsplit("/", 1)[-1]}
        observed = exact([item for item in flat_groups if item["_path"] == group["path"]], key, "organization group")
        if observed is None:
            parent_ref = group.get("parentGroupRef")
            if parent_ref:
                parent = next(
                    (
                        item for item in flat_groups
                        if (item.get("attributes") or {}).get("weave.semantic-key") == [parent_ref]
                    ),
                    None,
                )
                if parent is None:
                    # Parent groups are deliberately created in an earlier convergence round.
                    continue
                endpoint = f"{group_root}/{parent['id']}/children"
            else:
                endpoint = group_root
            operations.append(Operation("create", key, endpoint, None, marked_payload(key, wanted, list_values=True)))
            continue
        if not is_current(key, wanted, observed, list_values=True):
            operations.append(Operation("update", key, group_root, str(observed["id"]), marked_payload(key, wanted, list_values=True)))
        for role_ref in group.get("roleRefs", []):
            resolved = role_mapping(role_ref, roles_by_key, clients_by_key)
            if resolved is None:
                continue
            role_owner, role_value = resolved
            mapping_endpoint = f"{group_root}/{observed['id']}/role-mappings/{role_owner}"
            observed_mappings = kcadm.call("get", mapping_endpoint, "-r", realm_name) or []
            if role_value["name"] not in {item.get("name") for item in observed_mappings}:
                operations.append(
                    Operation(
                        "map-org-group-role",
                        f"{key}:{role_ref}",
                        mapping_endpoint,
                        None,
                        [role_value],
                    )
                )

    realm_management = exact(
        kcadm.call("get", "clients", "-r", realm_name, "-q", "clientId=realm-management") or [],
        "builtin-client:realm-management",
        "client",
    )
    for grant in desired.get("serviceAccountRoleGrants", []):
        client = clients_by_key.get(grant["clientKey"])
        if client is None or realm_management is None:
            continue
        account = kcadm.call("get", f"clients/{client['id']}/service-account-user", "-r", realm_name)
        if not isinstance(account, dict) or not account.get("id"):
            raise IdentityOpsError(f"service account is unavailable for {grant['clientKey']}")
        mappings = kcadm.call(
            "get",
            f"users/{account['id']}/role-mappings/clients/{realm_management['id']}",
            "-r",
            realm_name,
        ) or []
        observed_names = {item.get("name") for item in mappings}
        required_names = {role_ref.rsplit(":", 1)[-1] for role_ref in grant.get("roleRefs", [])}
        if grant["clientKey"] == "client:weave-identity-admin":
            expected = {"query-organizations"}
            missing_roles, retired_roles = identity_admin_role_delta(observed_names, expected)
            for retired_role in sorted(retired_roles):
                operations.append(
                    Operation(
                        "remove-role",
                        f"{grant['key']}:{retired_role}",
                        "remove-roles",
                        None,
                        {
                            "username": account.get("username"),
                            "clientId": "realm-management",
                            "roleName": retired_role,
                        },
                    )
                )
        roles_to_add = missing_roles if grant["clientKey"] == "client:weave-identity-admin" else required_names - observed_names
        for role_name in sorted(roles_to_add):
            operations.append(
                Operation(
                    "add-role",
                    f"{grant['key']}:{role_name}",
                    "add-roles",
                    None,
                    {
                        "username": account.get("username"),
                        "clientId": "realm-management",
                        "roleName": role_name,
                    },
                )
            )

        if grant["clientKey"] == "client:weave-identity-admin":
            fgap = desired.get("fineGrainedAdminPermissions") or {}
            subject_policy = exact(
                [
                    item for item in fgap.get("subjectPolicies", [])
                    if item.get("subjectServiceAccountClientKey") == grant["clientKey"]
                ],
                grant["clientKey"],
                "FGAP subject policy",
            )
            if (
                not fgap.get("enabled")
                or subject_policy is None
                or subject_policy.get("policyType") != "user"
                or subject_policy.get("logic") != "POSITIVE"
            ):
                raise IdentityOpsError("identity admin requires a declared user-policy FGAP subject")
            matching_permissions = [
                item for item in fgap.get("permissions", [])
                if subject_policy["key"] in item.get("policyRefs", [])
            ]
            permission_contract = exact(
                matching_permissions,
                subject_policy["key"],
                "FGAP organization permission",
            )
            if (
                permission_contract is None
                or permission_contract.get("resourceType") != "Organizations"
                or set(permission_contract.get("scopes") or []) != {"manage", "view"}
                or len(permission_contract.get("resourceRefs") or []) != 1
            ):
                raise IdentityOpsError("identity admin FGAP must declare exact organization manage/view")
            organization_ref = permission_contract["resourceRefs"][0]
            organization = organizations_by_key.get(organization_ref)
            admin_permissions = exact(
                kcadm.call("get", "clients", "-r", realm_name, "-q", "clientId=admin-permissions") or [],
                "builtin-client:admin-permissions",
                "client",
            )
            if organization is None or admin_permissions is None:
                continue
            base = f"clients/{admin_permissions['id']}/authz/resource-server"
            policy_name = str(subject_policy["name"])
            policies = kcadm.call("get", f"{base}/policy/user", "-r", realm_name) or []
            policy = exact([item for item in policies if item.get("name") == policy_name], policy_name, "FGAP policy")
            wanted_policy = {
                "name": policy_name,
                "logic": "POSITIVE",
                "users": [str(account["id"])],
            }
            if policy is None:
                operations.append(Operation("create", subject_policy["key"], f"{base}/policy/user", None, wanted_policy))
            elif (
                policy.get("logic") != "POSITIVE"
                or set(policy.get("users") or []) != {str(account["id"])}
            ):
                operations.append(Operation("update", subject_policy["key"], f"{base}/policy/user", str(policy["id"]), {**wanted_policy, "id": policy["id"]}))

            permission_name = str(permission_contract["name"])
            permissions = kcadm.call("get", f"{base}/permission/scope", "-r", realm_name) or []
            permission = exact(
                [item for item in permissions if item.get("name") == permission_name],
                permission_name,
                "FGAP permission",
            )
            wanted_permission = {
                "name": permission_name,
                "resourceType": "Organizations",
                "scopes": ["manage", "view"],
                "resources": [str(organization["id"])],
                "policies": [policy_name],
            }
            if policy is None:
                continue
            if permission is None:
                operations.append(Operation("create", permission_contract["key"], f"{base}/permission/scope", None, wanted_permission))
            elif (
                permission.get("resourceType") != "Organizations"
                or set(permission.get("scopes") or []) != {"manage", "view"}
                or set(permission.get("resources") or []) != {str(organization["id"])}
                or set(permission.get("policies") or []) != {policy_name}
            ):
                operations.append(Operation("update", permission_contract["key"], f"{base}/permission/scope", str(permission["id"]), {**wanted_permission, "id": permission["id"]}))
    return operations


def apply_operations(kcadm: Kcadm, realm: str, operations: list[Operation]) -> None:
    for operation in operations:
        if operation.action == "remove-role":
            kcadm.call(
                "remove-roles",
                "-r", realm,
                "--uusername", str(operation.payload["username"]),
                "--cclientid", str(operation.payload["clientId"]),
                "--rolename", str(operation.payload["roleName"]),
            )
            continue
        if operation.action == "rotate-secret":
            generated = kcadm.call(
                "create",
                f"clients/{operation.resource_id}/client-secret",
                "-r",
                realm,
                "-o",
            )
            value = generated.get("value") if isinstance(generated, dict) else None
            capture_generated_secret(str(operation.payload["filename"]), value)
            continue
        if operation.action == "add-role":
            kcadm.call(
                "add-roles",
                "-r", realm,
                "--uusername", str(operation.payload["username"]),
                "--cclientid", str(operation.payload["clientId"]),
                "--rolename", str(operation.payload["roleName"]),
            )
            continue
        if operation.action in {"map-org-group-role", "map-client-scope-role"}:
            kcadm.call("create", operation.endpoint, "-r", realm, payload=operation.payload)
            continue
        realm_arguments = () if operation.endpoint == "realms" or operation.endpoint.startswith("realms/") else ("-r", realm)
        if operation.action == "create":
            output_arguments = ("-o",) if operation.endpoint == "clients" else ()
            created = kcadm.call(
                "create",
                operation.endpoint,
                *realm_arguments,
                *output_arguments,
                payload=operation.payload,
            )
            if operation.endpoint == "clients":
                client_id = operation.payload.get("clientId")
                filename = SECRET_CLIENT_FILES.get(str(client_id))
                created_id = created.get("id") if isinstance(created, dict) else None
                if filename and created_id:
                    generated = kcadm.call("get", f"clients/{created_id}/client-secret", "-r", realm) or {}
                    capture_generated_secret(filename, generated.get("value"))
        else:
            endpoint = operation.endpoint
            if operation.resource_id and not endpoint.endswith(operation.resource_id):
                endpoint = f"{endpoint}/{operation.resource_id}"
            kcadm.call("update", endpoint, *realm_arguments, payload=operation.payload)


def capture_generated_secret(filename: str, value: object) -> None:
    if not isinstance(value, str) or not value:
        raise IdentityOpsError("Keycloak did not return the generated client SecretRef value")
    target = Path("/evidence/secret-updates") / filename
    target.parent.mkdir(parents=True, exist_ok=True)
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        stream.write(value + "\n")
    os.chmod(target, 0o600)


def provision_test_users(kcadm: Kcadm, desired: dict[str, Any], path: Path) -> int:
    if not path.exists():
        return 0
    realm = str(desired["realm"]["name"])
    metadata = path.lstat()
    if path.is_symlink() or not path.is_file() or metadata.st_mode & 0o777 != 0o600:
        raise IdentityOpsError("mounted test-user file must remain a regular mode-0600 file")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list):
        raise IdentityOpsError("test-user file must contain a JSON array")
    created = 0
    for item in value:
        if not isinstance(item, dict) or not all(
            isinstance(item.get(name), str) and item[name]
            for name in ("username", "secret")
        ):
            raise IdentityOpsError("each test user requires non-empty username and secret")
        for name in ("email", "firstName", "lastName"):
            if name in item and (not isinstance(item[name], str) or not item[name]):
                raise IdentityOpsError(f"optional test-user {name} must be a non-empty string")
        for name in ("roles", "groups"):
            if name in item and (
                not isinstance(item[name], list)
                or any(not isinstance(value, str) or not value for value in item[name])
            ):
                raise IdentityOpsError(f"optional test-user {name} must be an array of non-empty strings")
        observed_users = kcadm.call("get", "users", "-r", realm, "-q", f"username={item['username']}", "-q", "exact=true") or []
        created_user = exact(observed_users, item["username"], "test user")
        newly_created = created_user is None
        if created_user is None:
            user_payload: dict[str, Any] = {
                "username": item["username"],
                "enabled": True,
                "emailVerified": bool(item.get("email")),
            }
            if item.get("email"):
                user_payload["email"] = item["email"]
            for name in ("firstName", "lastName"):
                if item.get(name):
                    user_payload[name] = item[name]
            kcadm.call("create", "users", "-r", realm, payload=user_payload)
            kcadm.call(
                "set-password",
                "-r", realm,
                "--username", item["username"],
                "--new-password", item["secret"],
            )
            created_user = exact(
                kcadm.call("get", "users", "-r", realm, "-q", f"username={item['username']}", "-q", "exact=true") or [],
                item["username"],
                "test user",
            )
        if created_user is None:
            raise IdentityOpsError("test user readback failed")
        if newly_created:
            created += 1

        organizations = desired.get("organizations", [])
        if len(organizations) != 1:
            raise IdentityOpsError("test users require exactly one canonical organization")
        organization_candidates = kcadm.call("get", "organizations", "-r", realm) or []
        organization = exact(
            [value for value in organization_candidates if value.get("alias") == organizations[0]["alias"]],
            organizations[0]["key"],
            "canonical organization",
        )
        if organization is None:
            raise IdentityOpsError("canonical organization is unavailable for test users")
        organization_id = str(organization["id"])
        members = kcadm.call(
            "get", f"organizations/{organization_id}/members", "-r", realm,
            "-q", f"username={item['username']}", "-q", "exact=true",
        ) or []
        if not any(str(member.get("id")) == str(created_user["id"]) for member in members):
            kcadm.call(
                "create",
                f"organizations/{organization_id}/members",
                "-r", realm,
                payload=str(created_user["id"]),
            )

        configured_groups = desired.get("organizationGroups", [])
        requested_paths = set(item.get("groups", []))
        for role_name in item.get("roles", []):
            candidates = [
                group["path"]
                for group in configured_groups
                if any(
                    role_ref == role_name or role_ref.rsplit(":", 1)[-1] == role_name
                    for role_ref in group.get("roleRefs", [])
                )
            ]
            if len(candidates) != 1:
                raise IdentityOpsError(f"test-user role must resolve to one native organization group: {role_name}")
            requested_paths.add(candidates[0])
        if requested_paths:
            groups = kcadm.call(
                "get", f"organizations/{organization_id}/groups", "-r", realm,
                "-q", "populateHierarchy=true", "-q", "briefRepresentation=false",
            ) or []
            by_path = {group["_path"]: str(group["id"]) for group in flatten_groups(groups)}
            for group_path in sorted(requested_paths):
                if group_path not in by_path:
                    raise IdentityOpsError(f"test-user organization group does not exist: {group_path}")
                kcadm.call(
                    "update",
                    f"organizations/{organization_id}/groups/{by_path[group_path]}/members/{created_user['id']}",
                    "-r", realm,
                )
    return created


def probe_client_credentials(server: str, realm: str, clients: list[dict[str, Any]]) -> None:
    token_url = f"{server}/realms/{realm}/protocol/openid-connect/token"
    for client in clients:
        secret_ref = client.get("secretRef")
        if secret_ref not in SECRET_REF_FILES:
            continue
        if client.get("authenticationMethod") != "client_secret_basic":
            raise IdentityOpsError(f"{client['key']} token probe requires client_secret_basic")
        client_id = client["clientId"]
        secret = private_value(secret_path(SECRET_REF_FILES[secret_ref]))
        authorization = base64.b64encode(f"{client_id}:{secret}".encode("utf-8")).decode("ascii")
        request = urllib.request.Request(
            token_url,
            data=urllib.parse.urlencode({"grant_type": "client_credentials"}).encode("ascii"),
            headers={
                "Authorization": f"Basic {authorization}",
                "Content-Type": "application/x-www-form-urlencoded",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                if response.status != 200:
                    raise IdentityOpsError(f"HTTP Basic token probe failed for {client_id}")
                body = json.loads(response.read())
        except (urllib.error.URLError, json.JSONDecodeError) as error:
            raise IdentityOpsError(f"HTTP Basic token probe failed for {client_id}; response withheld") from error
        if not isinstance(body.get("access_token"), str):
            raise IdentityOpsError(f"HTTP Basic token probe returned no access token for {client_id}")


def remove_temporary_authority(kcadm: Kcadm, client_id: str) -> None:
    observed = exact(
        kcadm.call("get", "clients", "-r", "master", "-q", f"clientId={client_id}") or [],
        client_id,
        "temporary bootstrap client",
    )
    if observed is not None:
        kcadm.call("delete", f"clients/{observed['id']}", "-r", "master")
    readback = kcadm.call("get", "clients", "-r", "master", "-q", f"clientId={client_id}") or []
    if readback:
        raise IdentityOpsError("temporary bootstrap authority cleanup failed")


def evidence(command: str, desired: dict[str, Any], operations: list[Operation], empty_second_plan: bool | None) -> dict[str, object]:
    return {
        "schemaVersion": "weave.identity-ops-evidence.v1",
        "command": command,
        "desiredStateRevision": desired.get("revision"),
        "desiredStateDigest": digest(desired),
        "operationCount": len(operations),
        "operations": [item.support_safe() for item in operations],
        "emptySecondPlan": empty_second_plan,
        "temporaryBootstrapAuthorityRemoved": True,
        "tool": "kcadm.sh",
        "keycloakVersion": desired.get("keycloakVersion"),
        "containsSecretValues": False,
        "supportSafe": True,
    }


def write_evidence(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "apply", "verify"))
    parser.add_argument("--desired", type=Path, default=Path("/config/desired-state.json"))
    parser.add_argument("--evidence", type=Path, default=Path("/evidence/identity-ops.json"))
    parser.add_argument("--server", default="http://keycloak:8080")
    parser.add_argument("--bootstrap-client", default="weave-identity-ops-bootstrap")
    parser.add_argument("--password-file", type=Path, default=Path("/run/secrets/keycloak-bootstrap-admin-password"))
    parser.add_argument("--kcadm", default="/opt/keycloak/bin/kcadm.sh")
    parser.add_argument("--test-users", type=Path, default=Path("/run/weave/test-users.json"))
    parser.add_argument("--rotation-epoch", default=os.environ.get("WEAVE_IDENTITY_ROTATION_EPOCH") or None)
    args = parser.parse_args()
    kcadm: Kcadm | None = None
    temporary_authority_active = False
    try:
        desired = json.loads(args.desired.read_text(encoding="utf-8"))
        kcadm = Kcadm(args.kcadm, Path("/tmp/kcadm.config"))
        kcadm.authenticate(args.server, args.bootstrap_client, private_value(args.password_file))
        temporary_authority_active = True
        operations = plan(kcadm, desired, args.rotation_epoch)
        reported_operations = list(operations)
        second_empty: bool | None = None
        if args.command == "apply":
            for _ in range(8):
                if not operations:
                    break
                apply_operations(kcadm, desired["realm"]["name"], operations)
                operations = plan(kcadm, desired, args.rotation_epoch)
                reported_operations.extend(operations)
            second = operations
            second_empty = not second
            if second:
                raise IdentityOpsError("readback did not converge to an empty second plan")
            provision_test_users(kcadm, desired, args.test_users)
            probe_client_credentials(args.server, desired["realm"]["name"], desired.get("clients", []))
        elif args.command == "verify" and operations:
            raise IdentityOpsError("verification found a non-empty plan")
        remove_temporary_authority(kcadm, args.bootstrap_client)
        temporary_authority_active = False
        write_evidence(args.evidence, evidence(args.command, desired, reported_operations, second_empty))
        print(f"identity-ops: {args.command} complete; operations={len(reported_operations)}")
        return 0
    except (IdentityOpsError, KeyError, OSError, json.JSONDecodeError) as error:
        if temporary_authority_active and kcadm is not None:
            try:
                remove_temporary_authority(kcadm, args.bootstrap_client)
            except IdentityOpsError:
                print("WEAVE_IDENTITY_OPS_ERROR temporary bootstrap authority cleanup also failed", file=sys.stderr)
        print(f"WEAVE_IDENTITY_OPS_ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
