#!/usr/bin/env python3
"""Rootless, one-shot Keycloak desired-state reconciliation through kcadm.sh."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class IdentityOpsError(RuntimeError):
    pass


def classify_kcadm_failure(diagnostic: str) -> str:
    normalized = diagnostic.casefold()
    if "conflicting policy" in normalized and "already exists" in normalized:
        return "authorization-name-conflict"
    if "reset-password" in normalized and (
        "scope" in normalized or "not found" in normalized
    ):
        return "reset-password-scope-rejected"
    if "negative" in normalized and "polic" in normalized:
        return "negative-policy-rejected"
    if "positive" in normalized and "polic" in normalized:
        return "positive-policy-required"
    if "decision" in normalized and "unanimous" in normalized:
        return "decision-strategy-rejected"
    if "resource type" in normalized and "users" in normalized:
        return "users-resource-type-rejected"
    return "unclassified"


SECRET_REF_FILES = {
    "secretref:keycloak/weave-backend-jwk": "keycloak-weave-backend-jwk.json",
    "secretref:keycloak/weave-mcp-server-jwk": "keycloak-weave-mcp-server-jwk.json",
    "secretref:keycloak/weave-identity-admin": "keycloak-weave-identity-admin",
    "secretref:keycloak/weave-agent-runtime-admin-jwk": (
        "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin"
    ),
    "secretref:keycloak/nextcloud": "keycloak-nextcloud",
    "secretref:keycloak/matrix-mas": "keycloak-matrix-mas",
}
SECRET_CLIENT_FILES = {
    "weave-identity-admin": "keycloak-weave-identity-admin",
    "nextcloud": "keycloak-nextcloud",
    "matrix-mas": "keycloak-matrix-mas",
}
IDENTITY_ADMIN_REALM_MANAGEMENT_ROLES = frozenset(
    {
        "query-organizations",
        "query-users",
    }
)
RUNTIME_ADMIN_REALM_MANAGEMENT_ROLES = frozenset({"create-client"})
WORKLOAD_POLICY_EXECUTOR = "weave-workload-client-registration-enforcer"
IDENTITY_ADMIN_FGAP_CONTRACT = {
    "enabled": True,
    "subjectPolicies": [
        {
            "key": "admin-policy:identity-admin",
            "name": "weave-identity-admin user policy",
            "policyType": "user",
            "logic": "POSITIVE",
            "subjectServiceAccountClientKey": "client:weave-identity-admin",
        },
    ],
    "permissions": [
        {
            "key": "admin-permission:identity-primary-organization",
            "name": "weave-identity-admin primary organization",
            "resourceType": "Organizations",
            "resourceRefs": ["organization:weave-primary"],
            "allResources": False,
            "scopes": ["view", "manage"],
            "policyRefs": ["admin-policy:identity-admin"],
        },
        {
            "key": "admin-permission:identity-users",
            "name": "weave-identity-admin users",
            "resourceType": "Users",
            "resourceRefs": [],
            "allResources": True,
            "scopes": ["view", "manage", "manage-group-membership"],
            "policyRefs": ["admin-policy:identity-admin"],
        },
    ],
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
    REAUTHENTICATION_INTERVAL_SECONDS = 30

    def __init__(self, executable: str, config: Path) -> None:
        self.executable = executable
        self.config = config
        self._authentication: tuple[str, str, str] | None = None
        self._authenticated_at: float | None = None

    def call(self, *arguments: str, payload: Any = None) -> Any:
        if len(arguments) < 2:
            raise IdentityOpsError("kcadm operation requires a command and endpoint")
        if arguments[:2] != ("config", "credentials"):
            self._reauthenticate_if_due()
        return self._execute(*arguments, payload=payload)

    def _execute(self, *arguments: str, payload: Any = None) -> Any:
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
            diagnostic = result.stderr + "\n" + result.stdout
            status = re.search(r"(?i)HTTP(?: error)?[^0-9]{0,12}([45][0-9]{2})", diagnostic)
            status_label = status.group(1) if status else "unknown"
            failure_code = classify_kcadm_failure(diagnostic)
            raise IdentityOpsError(
                f"kcadm operation failed ({arguments[0]} {arguments[1]}), "
                f"httpStatus={status_label}, failureCode={failure_code}, "
                f"exitCode={result.returncode}, output withheld"
            )
        output = result.stdout.strip()
        return json.loads(output) if output else None

    def authenticate(self, server: str, client: str, secret: str) -> None:
        self._execute(
            "config", "credentials", "--server", server, "--realm", "master",
            "--client", client, "--secret", secret,
        )
        self._authentication = (server, client, secret)
        self._authenticated_at = time.monotonic()

    def _reauthenticate_if_due(self) -> None:
        if self._authentication is None or self._authenticated_at is None:
            return
        if time.monotonic() - self._authenticated_at < self.REAUTHENTICATION_INTERVAL_SECONDS:
            return
        server, client, secret = self._authentication
        self._execute(
            "config", "credentials", "--server", server, "--realm", "master",
            "--client", client, "--secret", secret,
        )
        self._authenticated_at = time.monotonic()


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
        "verifyEmail": realm["verifyEmail"],
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


def client_payload(client: dict[str, Any]) -> dict[str, Any]:
    allowed = (
        "clientId", "name", "description", "protocol", "enabled", "publicClient",
        "serviceAccountsEnabled", "standardFlowEnabled", "implicitFlowEnabled",
        "directAccessGrantsEnabled", "fullScopeAllowed",
        "redirectUris", "webOrigins",
    )
    result = {name: client[name] for name in allowed if name in client}
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
            public_key = {
                name: value
                for name, value in key.items()
                if name not in {"d", "p", "q", "dp", "dq", "qi", "oth", "k", "key_ops"}
            }
            public_key["key_ops"] = ["verify"]
            public_keys.append(public_key)
        attributes = {
            "token.endpoint.auth.method": "private_key_jwt",
            "token.endpoint.auth.signing.alg": "PS256",
            "use.jwks.url": "false",
            "use.jwks.string": "true",
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


def client_scope_payload(scope: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": scope["name"],
        "protocol": scope.get("protocol", "openid-connect"),
        "attributes": {
            "include.in.token.scope": str(scope["includeInTokenScope"]).lower(),
        },
    }


def client_scope_attachment_operations(
    kcadm: Kcadm,
    realm: str,
    desired_clients: list[dict[str, Any]],
    clients_by_key: dict[str, dict[str, Any]],
    observed_scopes: list[dict[str, Any]],
    scope_names: dict[str, str],
) -> list[Operation]:
    scopes_by_name: dict[str, list[dict[str, Any]]] = {}
    for scope in observed_scopes:
        name = str(scope.get("name", "")).strip()
        if name:
            scopes_by_name.setdefault(name, []).append(scope)

    operations: list[Operation] = []
    associations = (
        ("defaultClientScopes", "default-client-scopes"),
        ("optionalClientScopes", "optional-client-scopes"),
    )
    for client in desired_clients:
        client_key = str(client["key"])
        observed_client = clients_by_key.get(client_key)
        if observed_client is None:
            continue
        client_id = str(observed_client["id"])
        for desired_field, endpoint_segment in associations:
            desired_refs = [str(reference) for reference in client.get(desired_field, [])]
            desired_names = {
                scope_names.get(reference, reference.removeprefix("builtin-scope:"))
                for reference in desired_refs
            }
            endpoint = f"clients/{client_id}/{endpoint_segment}"
            observed_attachments = kcadm.call("get", endpoint, "-r", realm) or []
            if not isinstance(observed_attachments, list):
                raise IdentityOpsError(
                    f"Keycloak returned an invalid {endpoint_segment} projection"
                )
            observed_names = {
                str(scope.get("name", "")).strip()
                for scope in observed_attachments
                if str(scope.get("name", "")).strip()
            }

            for reference in desired_refs:
                name = scope_names.get(
                    reference,
                    reference.removeprefix("builtin-scope:"),
                )
                target = exact(
                    scopes_by_name.get(name, []),
                    reference,
                    "client scope",
                )
                if target is None:
                    if reference.startswith("builtin-scope:"):
                        raise IdentityOpsError(
                            f"pinned Keycloak is missing built-in client scope {name}"
                        )
                    # A managed custom scope is created in an earlier convergence round.
                    continue
                scope_id = str(target.get("id", "")).strip()
                if not scope_id:
                    raise IdentityOpsError(
                        f"Keycloak client scope {name} has no stable identifier"
                    )
                if name not in observed_names:
                    operations.append(
                        Operation(
                            "attach-client-scope",
                            f"{client_key}:{endpoint_segment}:{reference}",
                            f"{endpoint}/{scope_id}",
                            None,
                            {"clientKey": client_key, "scopeRef": reference},
                        )
                    )

            for current in observed_attachments:
                name = str(current.get("name", "")).strip()
                scope_id = str(current.get("id", "")).strip()
                if not name or not scope_id:
                    raise IdentityOpsError(
                        f"Keycloak returned an invalid {endpoint_segment} association"
                    )
                if name not in desired_names:
                    operations.append(
                        Operation(
                            "detach-client-scope",
                            f"{client_key}:{endpoint_segment}:observed:{name}",
                            f"{endpoint}/{scope_id}",
                            None,
                            {"clientKey": client_key, "scopeName": name},
                        )
                    )
    return operations


def client_scope_role_mapping_operations(
    kcadm: Kcadm,
    realm: str,
    desired_scopes: list[dict[str, Any]],
    scopes_by_key: dict[str, dict[str, Any]],
    desired_roles: list[dict[str, Any]],
    roles_by_key: dict[str, dict[str, Any]],
    clients_by_key: dict[str, dict[str, Any]],
) -> list[Operation]:
    managed_roles_by_container: dict[tuple[str, str | None], set[str]] = {}
    desired_role_by_key: dict[str, dict[str, Any]] = {}
    for role in desired_roles:
        role_key = str(role["key"])
        role_scope = str(role.get("scope", "realm"))
        client_key = str(role["clientKey"]) if role_scope == "client" else None
        container = (role_scope, client_key)
        managed_roles_by_container.setdefault(container, set()).add(str(role["name"]))
        desired_role_by_key[role_key] = role

    operations: list[Operation] = []
    for scope in desired_scopes:
        scope_key = str(scope["key"])
        observed_scope = scopes_by_key.get(scope_key)
        if observed_scope is None:
            continue
        scope_id = str(observed_scope["id"])
        expected_refs = {str(reference) for reference in scope.get("roleScopeRefs", [])}

        expected_refs_by_container: dict[tuple[str, str | None], set[str]] = {}
        for role_ref in expected_refs:
            desired_role = desired_role_by_key.get(role_ref)
            if desired_role is None:
                raise IdentityOpsError(f"{scope_key} references unmanaged role {role_ref}")
            role_scope = str(desired_role.get("scope", "realm"))
            client_key = (
                str(desired_role["clientKey"]) if role_scope == "client" else None
            )
            expected_refs_by_container.setdefault((role_scope, client_key), set()).add(
                role_ref
            )

        for container, managed_names in sorted(
            managed_roles_by_container.items(),
            key=lambda item: (item[0][0], item[0][1] or ""),
        ):
            role_scope, client_key = container
            if role_scope == "realm":
                endpoint = f"client-scopes/{scope_id}/scope-mappings/realm"
            elif role_scope == "client" and client_key is not None:
                observed_client = clients_by_key.get(client_key)
                if observed_client is None:
                    continue
                endpoint = (
                    f"client-scopes/{scope_id}/scope-mappings/clients/"
                    f"{observed_client['id']}"
                )
            else:
                raise IdentityOpsError(
                    f"unsupported role scope {role_scope} in {scope_key}"
                )

            observed_mappings = kcadm.call("get", endpoint, "-r", realm) or []
            if not isinstance(observed_mappings, list):
                raise IdentityOpsError(
                    f"Keycloak returned an invalid role mapping projection for {scope_key}"
                )
            observed_by_name = {
                str(mapping.get("name", "")): mapping
                for mapping in observed_mappings
                if str(mapping.get("name", ""))
            }
            container_refs = expected_refs_by_container.get(container, set())
            expected_names = {
                str(desired_role_by_key[role_ref]["name"])
                for role_ref in container_refs
            }

            for role_name in sorted(
                (observed_by_name.keys() & managed_names) - expected_names
            ):
                operations.append(
                    Operation(
                        "remove-client-scope-role",
                        f"{scope_key}:managed-role:{role_name}",
                        endpoint,
                        None,
                        [observed_by_name[role_name]],
                    )
                )

            for role_ref in sorted(container_refs):
                role = roles_by_key.get(role_ref)
                if role is None:
                    # A managed role is created in an earlier convergence round.
                    continue
                role_name = str(role["name"])
                if role_name not in observed_by_name:
                    operations.append(
                        Operation(
                            "map-client-scope-role",
                            f"{scope_key}:{role_ref}",
                            endpoint,
                            None,
                            [{"id": role["id"], "name": role_name}],
                        )
                    )
    return operations


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
    }
    if unexpected - retired:
        raise IdentityOpsError("identity admin has unmanaged realm-management roles")
    return expected - observed, unexpected & retired


def runtime_admin_role_delta(
    observed: set[str], expected: set[str]
) -> tuple[set[str], set[str]]:
    if expected != set(RUNTIME_ADMIN_REALM_MANAGEMENT_ROLES):
        raise IdentityOpsError(
            "runtime administration must resolve to exactly create-client"
        )
    return expected - observed, observed - expected


def flatten_groups(groups: list[dict[str, Any]], parent: str = "") -> list[dict[str, Any]]:
    flattened: list[dict[str, Any]] = []
    for group in groups:
        path = str(group.get("path") or f"{parent}/{group['name']}")
        flattened.append({**group, "_path": path})
        flattened.extend(flatten_groups(group.get("subGroups") or [], path))
    return flattened


def paged_get(
    kcadm: Kcadm,
    endpoint: str,
    realm: str,
    *query_arguments: str,
) -> list[dict[str, Any]]:
    page_size = 100
    first = 0
    collected: list[dict[str, Any]] = []
    observed_ids: set[str] = set()
    while True:
        page = kcadm.call(
            "get",
            endpoint,
            "-r",
            realm,
            *query_arguments,
            "-q",
            f"first={first}",
            "-q",
            f"max={page_size}",
        ) or []
        if not isinstance(page, list) or any(not isinstance(item, dict) for item in page):
            raise IdentityOpsError(f"paged kcadm response is invalid for {endpoint}")
        for item in page:
            resource_id = item.get("id")
            if not isinstance(resource_id, str) or not resource_id:
                raise IdentityOpsError(f"paged kcadm response has no stable id for {endpoint}")
            if resource_id in observed_ids:
                raise IdentityOpsError(f"paged kcadm response repeats an id for {endpoint}")
            observed_ids.add(resource_id)
            collected.append(item)
        if len(page) < page_size:
            return collected
        first += page_size


def organization_group_inventory(
    kcadm: Kcadm,
    group_root: str,
    realm: str,
) -> list[dict[str, Any]]:
    top_level = paged_get(
        kcadm,
        group_root,
        realm,
        "-q",
        "briefRepresentation=false",
    )
    pending = [(item, "") for item in top_level]
    flattened: list[dict[str, Any]] = []
    observed_ids: set[str] = set()
    while pending:
        group, parent_path = pending.pop(0)
        resource_id = str(group["id"])
        if resource_id in observed_ids:
            raise IdentityOpsError("organization group hierarchy repeats a provider id")
        observed_ids.add(resource_id)
        path = str(group.get("path") or f"{parent_path}/{group['name']}")
        flattened.append({**group, "_path": path})
        children = paged_get(
            kcadm,
            f"{group_root}/{resource_id}/children",
            realm,
        )
        pending.extend((child, path) for child in children)
    return flattened


def organization_group_create_operation(
    group: dict[str, Any],
    group_root: str,
    flat_groups: list[dict[str, Any]],
) -> Operation | None:
    key = str(group["key"])
    wanted = {"name": str(group["path"]).rsplit("/", 1)[-1]}
    parent_ref = group.get("parentGroupRef")
    if parent_ref is None:
        return Operation(
            "create",
            key,
            group_root,
            None,
            marked_payload(key, wanted, list_values=True),
        )
    parent = next(
        (
            item for item in flat_groups
            if (item.get("attributes") or {}).get("weave.semantic-key") == [parent_ref]
        ),
        None,
    )
    if parent is None:
        # The declared parent is created in an earlier convergence round.
        return None
    # Keycloak owns the generated child identifier. The canonical child-create
    # operation binds the parent through the endpoint and sends only the desired
    # child name; it is never a staging or ID-based move operation.
    return Operation(
        "create",
        key,
        f"{group_root}/{parent['id']}/children",
        None,
        wanted,
    )


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
    mapper_type = mapper["mapperType"]
    protocol_mapper = {
        "group-membership": "oidc-group-membership-mapper",
        "organization-group-membership": "oidc-organization-group-membership-mapper",
        "audience": "oidc-audience-mapper",
        "user-session-note": "oidc-usersessionmodel-note-mapper",
    }.get(mapper_type)
    if protocol_mapper is None:
        raise IdentityOpsError(f"unsupported protocol mapper type: {mapper_type}")
    common = {
        "name": mapper["name"],
        "protocol": "openid-connect",
        "protocolMapper": protocol_mapper,
    }
    if mapper_type == "group-membership":
        config = {
            "claim.name": mapper["claimName"],
            "full.path": str(mapper.get("fullPath", True)).lower(),
        }
    elif mapper_type == "organization-group-membership":
        config = {
            "addGroupRoleMappings": str(
                mapper.get("addGroupRoleMappings", False)
            ).lower(),
        }
    elif mapper_type == "audience":
        config = {"included.custom.audience": mapper["includedCustomAudience"]}
    elif mapper_type == "user-session-note":
        config = {
            "user.session.note": mapper["sessionNote"],
            "claim.name": mapper["claimName"],
            "jsonType.label": mapper["claimValueType"],
            "lightweight.claim": "false",
            "access.tokenResponse.claim": "false",
            "introspection.token.claim": "false",
        }
    config.update(
        {
            "id.token.claim": str(mapper.get("addToIdToken", False)).lower(),
            "access.token.claim": str(mapper.get("addToAccessToken", False)).lower(),
            "userinfo.token.claim": str(mapper.get("addToUserInfo", False)).lower(),
        }
    )
    return {**common, "config": config}


def mapper_is_current(wanted: dict[str, Any], observed: dict[str, Any]) -> bool:
    if any(
        observed.get(field) != wanted.get(field)
        for field in ("name", "protocol", "protocolMapper")
    ):
        return False
    wanted_config = wanted.get("config")
    observed_config = observed.get("config")
    if not isinstance(wanted_config, dict) or not isinstance(observed_config, dict):
        return False
    return all(observed_config.get(key) == value for key, value in wanted_config.items())


def projected_fields_are_current(
    wanted: dict[str, Any],
    observed: dict[str, Any],
) -> bool:
    return all(observed.get(key) == value for key, value in wanted.items())


def complete_relationship_names(
    kcadm: Kcadm,
    endpoint: str,
    realm: str,
) -> set[str]:
    response = kcadm.call("get", endpoint, "-r", realm) or []
    if not isinstance(response, list) or any(not isinstance(item, dict) for item in response):
        raise IdentityOpsError(f"kcadm relationship response is invalid for {endpoint}")
    names: list[str] = []
    for item in response:
        name = item.get("name")
        if not isinstance(name, str) or not name:
            raise IdentityOpsError(f"kcadm relationship response has no semantic name for {endpoint}")
        names.append(name)
    if len(names) != len(set(names)):
        raise IdentityOpsError(f"kcadm relationship response repeats a semantic name for {endpoint}")
    return set(names)


def permission_relationships(
    kcadm: Kcadm,
    permission_endpoint: str,
    realm: str,
) -> tuple[set[str], set[str], set[str]]:
    return (
        complete_relationship_names(kcadm, f"{permission_endpoint}/resources", realm),
        complete_relationship_names(kcadm, f"{permission_endpoint}/scopes", realm),
        complete_relationship_names(
            kcadm,
            f"{permission_endpoint}/associatedPolicies",
            realm,
        ),
    )


def plan_identity_admin_fgap(
    kcadm: Kcadm,
    realm: str,
    fgap: dict[str, Any],
    account: dict[str, Any],
    organizations_by_key: dict[str, dict[str, Any]],
) -> list[Operation]:
    if fgap != IDENTITY_ADMIN_FGAP_CONTRACT:
        raise IdentityOpsError(
            "identity admin FGAP differs from the exact guarded organization and user "
            "lifecycle contract"
        )

    admin_permissions = exact(
        kcadm.call(
            "get",
            "clients",
            "-r",
            realm,
            "-q",
            "clientId=admin-permissions",
        )
        or [],
        "builtin-client:admin-permissions",
        "client",
    )
    if admin_permissions is None:
        return []

    operations: list[Operation] = []
    base = f"clients/{admin_permissions['id']}/authz/resource-server"
    observed_policies = paged_get(kcadm, f"{base}/policy/user", realm)
    policies_by_key: dict[str, dict[str, Any]] = {}
    policy_names_by_key: dict[str, str] = {}

    for contract in fgap["subjectPolicies"]:
        policy_name = str(contract["name"])
        policy_names_by_key[str(contract["key"])] = policy_name
        policy = exact(
            [item for item in observed_policies if item.get("name") == policy_name],
            str(contract["key"]),
            "FGAP user policy",
        )
        wanted = {
            "name": policy_name,
            "logic": str(contract["logic"]),
            "users": [str(account["id"])],
        }
        if policy is None:
            operations.append(
                Operation(
                    "create",
                    str(contract["key"]),
                    f"{base}/policy/user",
                    None,
                    wanted,
                )
            )
            continue
        policies_by_key[str(contract["key"])] = policy
        if (
            policy.get("logic") != wanted["logic"]
            or set(policy.get("users") or []) != {str(account["id"])}
        ):
            operations.append(
                Operation(
                    "update",
                    str(contract["key"]),
                    f"{base}/policy/user",
                    str(policy["id"]),
                    {**wanted, "id": policy["id"]},
                )
            )

    observed_permissions = paged_get(kcadm, f"{base}/permission/scope", realm)
    for contract in fgap["permissions"]:
        policy_refs = [str(item) for item in contract["policyRefs"]]
        if any(policy_ref not in policies_by_key for policy_ref in policy_refs):
            # Referenced policies are materialized in this convergence round.
            continue
        policy_names = {policy_names_by_key[policy_ref] for policy_ref in policy_refs}
        resource_type = str(contract["resourceType"])
        resource_refs = [str(item) for item in contract["resourceRefs"]]
        all_resources = bool(contract["allResources"])
        if resource_type == "Organizations":
            if all_resources or resource_refs != ["organization:weave-primary"]:
                raise IdentityOpsError(
                    "identity admin Organizations FGAP must target only the primary organization"
                )
            organization = organizations_by_key.get(resource_refs[0])
            if organization is None:
                continue
            requested_resource_ids = {str(organization["id"])}
            expected_resource_names = requested_resource_ids
        elif resource_type == "Users":
            if not all_resources or resource_refs:
                raise IdentityOpsError(
                    "identity admin Users FGAP must use the declared all-Users lifecycle boundary"
                )
            requested_resource_ids = set()
            # Keycloak materializes an all-resource permission against the
            # built-in resource-type sentinel. Its relationship name is the
            # resource type even though the create/update representation must
            # omit explicit resource IDs.
            expected_resource_names = {resource_type}
        else:
            raise IdentityOpsError(
                f"unsupported identity admin FGAP resource type: {resource_type}"
            )

        permission_name = str(contract["name"])
        permission = exact(
            [item for item in observed_permissions if item.get("name") == permission_name],
            str(contract["key"]),
            "FGAP scope permission",
        )
        wanted: dict[str, Any] = {
            "name": permission_name,
            "resourceType": resource_type,
            "scopes": list(contract["scopes"]),
            "policies": sorted(policy_names),
        }
        if requested_resource_ids:
            wanted["resources"] = sorted(requested_resource_ids)
        if permission is None:
            operations.append(
                Operation(
                    "create",
                    str(contract["key"]),
                    f"{base}/permission/scope",
                    None,
                    wanted,
                )
            )
            continue

        permission_endpoint = f"{base}/permission/scope/{permission['id']}"
        observed_resources, observed_scopes, observed_policies_for_permission = (
            permission_relationships(kcadm, permission_endpoint, realm)
        )
        permission_config = permission.get("config")
        observed_resource_type = permission.get("resourceType") or (
            permission_config.get("defaultResourceType")
            if isinstance(permission_config, dict)
            else None
        )
        if (
            observed_resource_type != resource_type
            or observed_resources != expected_resource_names
            or observed_scopes != set(contract["scopes"])
            or observed_policies_for_permission != policy_names
        ):
            operations.append(
                Operation(
                    "update",
                    str(contract["key"]),
                    f"{base}/permission/scope",
                    str(permission["id"]),
                    {**wanted, "id": permission["id"]},
                )
            )
    return operations


def workload_client_policy_payloads(
    policies: list[dict[str, Any]],
) -> tuple[dict[str, Any], dict[str, Any]]:
    if len(policies) != 1:
        raise IdentityOpsError(
            "Identity Ops requires exactly one workload registration policy"
        )
    contract = policies[0]
    required = {
        "conditionProvider": "any-client",
        "executorProvider": WORKLOAD_POLICY_EXECUTOR,
        "executorVersion": "1",
        "keycloakVersion": "26.7.0",
        "runtimeAdminClientKey": "client:weave-agent-runtime-admin",
        "registrationProvider": "openid-connect",
        "identifierMetadata": "client_name",
        "workloadRoleRef": "role:weaver-runtime",
    }
    if any(contract.get(name) != value for name, value in required.items()):
        raise IdentityOpsError(
            "workload registration policy does not match the pinned Keycloak contract"
        )
    name = str(contract.get("name", "")).strip()
    if not name or contract.get("enabled") is not True:
        raise IdentityOpsError(
            "workload registration policy must be named and enabled"
        )
    profile_name = f"{name}-profile"
    profiles = {
        "profiles": [
            {
                "name": profile_name,
                "description": (
                    "Weave-owned, version-pinned per-Cell workload registration policy."
                ),
                "executors": [
                    {
                        "executor": WORKLOAD_POLICY_EXECUTOR,
                        "configuration": {},
                    }
                ],
            }
        ]
    }
    policy_payload = {
        "policies": [
            {
                "name": name,
                "description": (
                    "Restrict authenticated OIDC Dynamic Client Registration "
                    "to Weave per-Cell workloads."
                ),
                "enabled": True,
                "conditions": [
                    {
                        "condition": "any-client",
                        "configuration": {},
                    }
                ],
                "profiles": [profile_name],
            }
        ]
    }
    return profiles, policy_payload


def _normalized_client_profiles(value: object) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise IdentityOpsError("Keycloak returned malformed client policy profiles")
    profiles = value.get("profiles") or []
    if not isinstance(profiles, list):
        raise IdentityOpsError("Keycloak returned malformed client policy profiles")
    return {
        "profiles": [
            {
                "name": profile.get("name"),
                "description": profile.get("description"),
                "executors": [
                    {
                        "executor": executor.get("executor"),
                        "configuration": executor.get("configuration") or {},
                    }
                    for executor in (profile.get("executors") or [])
                    if isinstance(executor, dict)
                ],
            }
            for profile in profiles
            if isinstance(profile, dict)
        ]
    }


def _normalized_client_policies(value: object) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise IdentityOpsError("Keycloak returned malformed client policies")
    policies = value.get("policies") or []
    if not isinstance(policies, list):
        raise IdentityOpsError("Keycloak returned malformed client policies")
    return {
        "policies": [
            {
                "name": policy.get("name"),
                "description": policy.get("description"),
                "enabled": policy.get("enabled"),
                "conditions": [
                    {
                        "condition": condition.get("condition"),
                        "configuration": condition.get("configuration") or {},
                    }
                    for condition in (policy.get("conditions") or [])
                    if isinstance(condition, dict)
                ],
                "profiles": list(policy.get("profiles") or []),
            }
            for policy in policies
            if isinstance(policy, dict)
        ]
    }


def plan_workload_client_policy(
    kcadm: Kcadm,
    realm: str,
    policies: list[dict[str, Any]],
) -> list[Operation]:
    wanted_profiles, wanted_policies = workload_client_policy_payloads(policies)
    observed_profiles = kcadm.call(
        "get", "client-policies/profiles", "-r", realm
    ) or {}
    observed_policies = kcadm.call(
        "get", "client-policies/policies", "-r", realm
    ) or {}
    operations: list[Operation] = []
    if _normalized_client_profiles(observed_profiles) != wanted_profiles:
        operations.append(
            Operation(
                "update",
                "client-policy-profile:weaver-cell-registration",
                "client-policies/profiles",
                None,
                wanted_profiles,
            )
        )
    if _normalized_client_policies(observed_policies) != wanted_policies:
        operations.append(
            Operation(
                "update",
                "client-policy:weaver-cell-registration",
                "client-policies/policies",
                None,
                wanted_policies,
            )
        )
    return operations


def plan(kcadm: Kcadm, desired: dict[str, Any], rotation_epoch: str | None = None) -> list[Operation]:
    operations: list[Operation] = []
    if desired.get("apiVersion") != "weave.keycloak-desired-state/v2" or "groups" in desired:
        raise IdentityOpsError("Identity Ops accepts only canonical desired-state v2 without legacy realm groups")
    client_policies = desired.get("clientPolicies")
    if not isinstance(client_policies, list):
        raise IdentityOpsError("desired state must declare clientPolicies")
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

    operations.extend(
        plan_workload_client_policy(kcadm, realm_name, client_policies)
    )

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
        wanted = client_payload(client)
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
        wanted = client_scope_payload(scope)
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
            elif not mapper_is_current(wanted_mapper, observed_mapper):
                operations.append(
                    Operation(
                        "update",
                        mapper["key"],
                        mapper_endpoint,
                        str(observed_mapper["id"]),
                        {**wanted_mapper, "id": observed_mapper["id"]},
                    )
                )

    operations.extend(
        client_scope_attachment_operations(
            kcadm,
            realm_name,
            desired.get("clients", []),
            clients_by_key,
            observed_scopes,
            scope_names,
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

    operations.extend(
        client_scope_role_mapping_operations(
            kcadm,
            realm_name,
            desired.get("clientScopes", []),
            scopes_by_key,
            desired.get("roles", []),
            roles_by_key,
            clients_by_key,
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
            operations.append(Operation("create", key, "organizations", None, wanted))
        else:
            organizations_by_key[key] = observed
            if not projected_fields_are_current(wanted, observed):
                operations.append(
                    Operation(
                        "update",
                        key,
                        "organizations",
                        str(observed["id"]),
                        {**wanted, "id": observed["id"]},
                    )
                )

    group_inventories: dict[str, list[dict[str, Any]]] = {}
    for group in desired.get("organizationGroups", []):
        organization = organizations_by_key.get(group["organizationRef"])
        if organization is None:
            continue
        organization_id = str(organization["id"])
        group_root = f"organizations/{organization_id}/groups"
        if organization_id not in group_inventories:
            group_inventories[organization_id] = organization_group_inventory(
                kcadm,
                group_root,
                realm_name,
            )
        flat_groups = group_inventories[organization_id]
        key = group["key"]
        wanted = {"name": group["path"].rsplit("/", 1)[-1]}
        observed = exact([item for item in flat_groups if item["_path"] == group["path"]], key, "organization group")
        if observed is None:
            create_operation = organization_group_create_operation(group, group_root, flat_groups)
            if create_operation is not None:
                operations.append(create_operation)
            continue
        if group.get("parentGroupRef") is None and not is_current(
            key,
            wanted,
            observed,
            list_values=True,
        ):
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
            expected = set(IDENTITY_ADMIN_REALM_MANAGEMENT_ROLES)
            missing_roles, retired_roles = identity_admin_role_delta(observed_names, expected)
        elif grant["clientKey"] == "client:weave-agent-runtime-admin":
            expected = set(RUNTIME_ADMIN_REALM_MANAGEMENT_ROLES)
            if required_names != expected:
                raise IdentityOpsError(
                    "runtime administration role grant must contain only create-client"
                )
            missing_roles, retired_roles = runtime_admin_role_delta(
                observed_names, expected
            )
        else:
            expected = required_names
            missing_roles = required_names - observed_names
            retired_roles = set()
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
        roles_to_add = missing_roles
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

        if grant["clientKey"] in {
            "client:weave-identity-admin",
            "client:weave-agent-runtime-admin",
        }:
            scope_mapping_endpoint = (
                f"clients/{client['id']}/scope-mappings/clients/{realm_management['id']}"
            )
            scope_mappings = kcadm.call(
                "get",
                scope_mapping_endpoint,
                "-r",
                realm_name,
            ) or []
            observed_scope_names = {item.get("name") for item in scope_mappings}
            if grant["clientKey"] == "client:weave-identity-admin":
                missing_scope_roles, retired_scope_roles = identity_admin_role_delta(
                    observed_scope_names,
                    expected,
                )
            else:
                missing_scope_roles, retired_scope_roles = runtime_admin_role_delta(
                    observed_scope_names,
                    expected,
                )
            for retired_role in sorted(retired_scope_roles):
                mapped_role = exact(
                    [item for item in scope_mappings if item.get("name") == retired_role],
                    f"{grant['key']}:scope:{retired_role}",
                    "client role scope mapping",
                )
                if mapped_role is not None:
                    operations.append(
                        Operation(
                            "remove-client-scope-role",
                            f"{grant['key']}:scope:{retired_role}",
                            scope_mapping_endpoint,
                            None,
                            [mapped_role],
                        )
                    )
            if missing_scope_roles:
                realm_management_roles = kcadm.call(
                    "get",
                    f"clients/{realm_management['id']}/roles",
                    "-r",
                    realm_name,
                ) or []
                for role_name in sorted(missing_scope_roles):
                    role = exact(
                        [item for item in realm_management_roles if item.get("name") == role_name],
                        f"builtin-role:realm-management:{role_name}",
                        "realm-management role",
                    )
                    if role is None:
                        raise IdentityOpsError(
                            f"required realm-management role is unavailable: {role_name}"
                        )
                    operations.append(
                        Operation(
                            "map-client-scope-role",
                            f"{grant['key']}:scope:{role_name}",
                            scope_mapping_endpoint,
                            None,
                            [role],
                        )
                    )

            if grant["clientKey"] == "client:weave-identity-admin":
                fgap = desired.get("fineGrainedAdminPermissions")
                if not isinstance(fgap, dict):
                    raise IdentityOpsError(
                        "identity admin requires a declared FGAP contract"
                    )
                operations.extend(
                    plan_identity_admin_fgap(
                        kcadm,
                        realm_name,
                        fgap,
                        account,
                        organizations_by_key,
                    )
                )
    return operations


def apply_operations(kcadm: Kcadm, realm: str, operations: list[Operation]) -> None:
    for operation in operations:
        try:
            apply_operation(kcadm, realm, operation)
        except IdentityOpsError as error:
            raise IdentityOpsError(
                f"identity operation failed action={operation.action}, key={operation.key}; {error}"
            ) from error


def apply_operation(kcadm: Kcadm, realm: str, operation: Operation) -> None:
    if operation.action == "remove-role":
        kcadm.call(
            "remove-roles",
            "-r", realm,
            "--uusername", str(operation.payload["username"]),
            "--cclientid", str(operation.payload["clientId"]),
            "--rolename", str(operation.payload["roleName"]),
        )
        return
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
        return
    if operation.action == "add-role":
        kcadm.call(
            "add-roles",
            "-r", realm,
            "--uusername", str(operation.payload["username"]),
            "--cclientid", str(operation.payload["clientId"]),
            "--rolename", str(operation.payload["roleName"]),
        )
        return
    if operation.action in {"map-org-group-role", "map-client-scope-role"}:
        kcadm.call("create", operation.endpoint, "-r", realm, payload=operation.payload)
        return
    if operation.action == "remove-client-scope-role":
        kcadm.call("delete", operation.endpoint, "-r", realm, payload=operation.payload)
        return
    if operation.action == "attach-client-scope":
        kcadm.call("update", operation.endpoint, "-r", realm)
        return
    if operation.action == "detach-client-scope":
        kcadm.call("delete", operation.endpoint, "-r", realm)
        return
    realm_arguments = (
        ()
        if operation.endpoint == "realms" or operation.endpoint.startswith("realms/")
        else ("-r", realm)
    )
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
                generated = (
                    kcadm.call(
                        "get",
                        f"clients/{created_id}/client-secret",
                        "-r",
                        realm,
                    )
                    or {}
                )
                capture_generated_secret(filename, generated.get("value"))
        return
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


def client_credentials_token_response(
    server: str,
    realm: str,
    client_id: str,
    secret: str,
) -> tuple[int, dict[str, Any]]:
    token_url = f"{server}/realms/{realm}/protocol/openid-connect/token"
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
            body = json.loads(response.read())
            if not isinstance(body, dict):
                raise IdentityOpsError(
                    f"client credentials response is malformed for {client_id}"
                )
            return response.status, body
    except urllib.error.HTTPError as error:
        try:
            body = json.loads(error.read(4096))
        except json.JSONDecodeError as parse_error:
            raise IdentityOpsError(
                f"client credentials error response is malformed for {client_id}; response withheld"
            ) from parse_error
        if not isinstance(body, dict):
            raise IdentityOpsError(
                f"client credentials error response is malformed for {client_id}; response withheld"
            )
        return error.code, body
    except (urllib.error.URLError, json.JSONDecodeError) as error:
        raise IdentityOpsError(
            f"client credentials request failed for {client_id}; response withheld"
        ) from error


def base64url_bytes(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def base64url_integer(value: str) -> int:
    padding = "=" * (-len(value) % 4)
    return int.from_bytes(base64.urlsafe_b64decode(value + padding), "big")


def mask_generation_function(seed: bytes, length: int) -> bytes:
    result = bytearray()
    counter = 0
    while len(result) < length:
        result.extend(hashlib.sha256(seed + counter.to_bytes(4, "big")).digest())
        counter += 1
    return bytes(result[:length])


def rsa_ps256_signature(message: bytes, private_jwk: dict[str, Any]) -> bytes:
    try:
        modulus = base64url_integer(str(private_jwk["n"]))
        private_exponent = base64url_integer(str(private_jwk["d"]))
    except (KeyError, ValueError) as error:
        raise IdentityOpsError("runtime administration private JWK is malformed") from error
    modulus_bits = modulus.bit_length()
    encoded_bits = modulus_bits - 1
    encoded_length = (encoded_bits + 7) // 8
    salt = os.urandom(32)
    digest = hashlib.sha256(message).digest()
    encoded_digest = hashlib.sha256(b"\x00" * 8 + digest + salt).digest()
    padding_length = encoded_length - len(encoded_digest) - len(salt) - 2
    if modulus_bits < 2048 or padding_length < 0:
        raise IdentityOpsError("runtime administration private JWK is malformed")
    data_block = b"\x00" * padding_length + b"\x01" + salt
    data_mask = mask_generation_function(encoded_digest, encoded_length - len(encoded_digest) - 1)
    masked_data = bytearray(left ^ right for left, right in zip(data_block, data_mask))
    masked_data[0] &= 0xFF >> (8 * encoded_length - encoded_bits)
    encoded_message = bytes(masked_data) + encoded_digest + b"\xbc"
    signature = pow(
        int.from_bytes(encoded_message, "big"),
        private_exponent,
        modulus,
    )
    return signature.to_bytes((modulus_bits + 7) // 8, "big")


def private_key_jwt_token_response(
    server: str,
    realm: str,
    client_id: str,
    private_jwk: dict[str, Any],
    assertion_audience: str | None = None,
) -> tuple[int, dict[str, Any]]:
    if (
        private_jwk.get("kty") != "RSA"
        or private_jwk.get("alg") != "PS256"
        or private_jwk.get("key_ops") != ["sign"]
        or not isinstance(private_jwk.get("kid"), str)
    ):
        raise IdentityOpsError("runtime administration private JWK is malformed")
    token_url = f"{server}/realms/{realm}/protocol/openid-connect/token"
    audience = assertion_audience or token_url
    now = int(time.time())
    protected = base64url_bytes(
        json.dumps(
            {"alg": "PS256", "kid": private_jwk["kid"], "typ": "JWT"},
            separators=(",", ":"),
            sort_keys=True,
        ).encode("ascii")
    )
    claims = base64url_bytes(
        json.dumps(
            {
                "aud": audience,
                "exp": now + 60,
                "iat": now,
                "iss": client_id,
                "jti": str(uuid.uuid4()),
                "sub": client_id,
            },
            separators=(",", ":"),
            sort_keys=True,
        ).encode("ascii")
    )
    signing_input = f"{protected}.{claims}".encode("ascii")
    assertion = f"{protected}.{claims}.{base64url_bytes(rsa_ps256_signature(signing_input, private_jwk))}"
    request = urllib.request.Request(
        token_url,
        data=urllib.parse.urlencode(
            {
                "grant_type": "client_credentials",
                "client_id": client_id,
                "client_assertion_type": (
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                ),
                "client_assertion": assertion,
            }
        ).encode("ascii"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            body = json.loads(response.read())
            if not isinstance(body, dict):
                raise IdentityOpsError(
                    f"private_key_jwt response is malformed for {client_id}"
                )
            return response.status, body
    except urllib.error.HTTPError as error:
        try:
            body = json.loads(error.read(4096))
        except json.JSONDecodeError as parse_error:
            raise IdentityOpsError(
                f"private_key_jwt error response is malformed for {client_id}; response withheld"
            ) from parse_error
        if not isinstance(body, dict):
            raise IdentityOpsError(
                f"private_key_jwt error response is malformed for {client_id}; response withheld"
            )
        return error.code, body
    except (urllib.error.URLError, json.JSONDecodeError) as error:
        raise IdentityOpsError(
            f"private_key_jwt request failed for {client_id}; response withheld"
        ) from error


def oauth_probe_failure_category(body: dict[str, Any]) -> str:
    error = str(body.get("error", "")).casefold()
    description = str(body.get("error_description", "")).casefold()
    if error == "invalid_client":
        if "audience" in description:
            return "invalid-client-audience"
        if "public key" in description or "load key" in description:
            return "invalid-client-public-key"
        if "signature" in description:
            return "invalid-client-signature"
        if "algorithm" in description:
            return "invalid-client-algorithm"
        return "invalid-client"
    if error == "unauthorized_client":
        return "unauthorized-client"
    if error == "invalid_grant":
        return "invalid-grant"
    return "unclassified-oauth-error"


def access_token_client_roles(access_token: str, client_id: str) -> set[str]:
    try:
        segments = access_token.split(".")
        if len(segments) != 3:
            raise ValueError("JWT segment count")
        padding = "=" * (-len(segments[1]) % 4)
        claims = json.loads(
            base64.urlsafe_b64decode(segments[1] + padding).decode("utf-8")
        )
        if not isinstance(claims, dict):
            return set()
        resource_access = claims.get("resource_access")
        access = (
            resource_access.get(client_id)
            if isinstance(resource_access, dict)
            else None
        )
        roles = access.get("roles") if isinstance(access, dict) else None
        if not isinstance(roles, list) or any(not isinstance(role, str) for role in roles):
            return set()
        return set(roles)
    except (binascii.Error, UnicodeDecodeError, ValueError, json.JSONDecodeError):
        return set()


def probe_client_credentials(
    server: str,
    realm: str,
    clients: list[dict[str, Any]],
    assertion_audience: str,
) -> None:
    for client in clients:
        secret_ref = client.get("secretRef")
        key_ref = client.get("keyRef")
        if secret_ref not in SECRET_REF_FILES and key_ref not in SECRET_REF_FILES:
            continue
        client_id = client["clientId"]
        if client.get("authenticationMethod") == "client_secret_basic":
            secret = private_value(secret_path(SECRET_REF_FILES[secret_ref]))
            status, body = client_credentials_token_response(
                server,
                realm,
                str(client_id),
                secret,
            )
        elif client.get("authenticationMethod") == "private_key_jwt":
            private_jwk = json.loads(
                private_value(secret_path(SECRET_REF_FILES[key_ref]))
            )
            status, body = private_key_jwt_token_response(
                server,
                realm,
                str(client_id),
                private_jwk,
                assertion_audience,
            )
        else:
            raise IdentityOpsError(
                f"{client['key']} token probe requires a supported authentication method"
            )
        if client.get("serviceAccountsEnabled") is True:
            if status != 200 or not isinstance(body.get("access_token"), str):
                raise IdentityOpsError(
                    "service-account client credentials probe failed for "
                    f"{client_id}; category={oauth_probe_failure_category(body)}; "
                    "response withheld"
                )
            if client.get("key") == "client:weave-agent-runtime-admin":
                projected_roles = access_token_client_roles(
                    str(body["access_token"]),
                    "realm-management",
                )
                if projected_roles != set(RUNTIME_ADMIN_REALM_MANAGEMENT_ROLES):
                    raise IdentityOpsError(
                        "runtime administration token role projection differs from "
                        "exact create-client authority; response withheld"
                    )
        elif status not in {400, 401} or body.get("error") != "unauthorized_client":
            raise IdentityOpsError(
                f"non-service client unexpectedly accepted client credentials for {client_id}"
            )


def administration_read_probe_status(
    server: str,
    realm: str,
    resource_path: str,
    access_token: str,
) -> int:
    endpoint = (
        f"{server}/admin/realms/{urllib.parse.quote(realm, safe='')}"
        f"/{resource_path.lstrip('/')}"
    )
    request = urllib.request.Request(
        endpoint,
        headers={"Authorization": f"Bearer {access_token}"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            response.read(4096)
            return response.status
    except urllib.error.HTTPError as error:
        error.read(4096)
        return error.code
    except urllib.error.URLError as error:
        raise IdentityOpsError(
            "identity-admin positive authorization probe failed; response withheld"
        ) from error


def probe_identity_admin_authorization(
    kcadm: Kcadm,
    server: str,
    realm: str,
    clients: list[dict[str, Any]],
    organizations: list[dict[str, Any]],
) -> None:
    desired_client = exact(
        [
            client
            for client in clients
            if client.get("key") == "client:weave-identity-admin"
        ],
        "client:weave-identity-admin",
        "desired identity administration client",
    )
    if desired_client is None:
        raise IdentityOpsError("identity administration client is missing from desired state")
    secret_ref = desired_client.get("secretRef")
    filename = SECRET_REF_FILES.get(str(secret_ref))
    if (
        filename is None
        or desired_client.get("authenticationMethod") != "client_secret_basic"
        or desired_client.get("serviceAccountsEnabled") is not True
    ):
        raise IdentityOpsError(
            "identity administration client cannot perform the positive authorization probes"
        )
    observed_client = exact(
        kcadm.call(
            "get",
            "clients",
            "-r",
            realm,
            "-q",
            f"clientId={desired_client['clientId']}",
        )
        or [],
        "client:weave-identity-admin",
        "identity administration client",
    )
    if observed_client is None:
        raise IdentityOpsError("identity administration client was not materialized")
    account = kcadm.call(
        "get",
        f"clients/{observed_client['id']}/service-account-user",
        "-r",
        realm,
    ) or {}
    account_id = account.get("id")
    if not isinstance(account_id, str) or not account_id:
        raise IdentityOpsError(
            "identity administration service account has no stable identifier"
        )
    status, token_response = client_credentials_token_response(
        server,
        realm,
        str(desired_client["clientId"]),
        private_value(secret_path(filename)),
    )
    access_token = token_response.get("access_token")
    if status != 200 or not isinstance(access_token, str) or not access_token:
        raise IdentityOpsError(
            "identity administration token is unavailable for the positive authorization probes"
        )
    desired_organization = exact(
        [
            organization
            for organization in organizations
            if organization.get("key") == "organization:weave-primary"
        ],
        "organization:weave-primary",
        "desired primary organization",
    )
    if desired_organization is None:
        raise IdentityOpsError("primary organization is missing from desired state")
    observed_organization = exact(
        [
            organization
            for organization in (kcadm.call("get", "organizations", "-r", realm) or [])
            if organization.get("alias") == desired_organization.get("alias")
        ],
        "organization:weave-primary",
        "primary organization",
    )
    organization_id = (
        observed_organization.get("id")
        if isinstance(observed_organization, dict)
        else None
    )
    if not isinstance(organization_id, str) or not organization_id:
        raise IdentityOpsError("primary organization has no stable identifier")
    read_probes = {
        "primary-organization": (
            "organizations/" + urllib.parse.quote(organization_id, safe="")
        ),
        "service-account-user": (
            "users/" + urllib.parse.quote(account_id, safe="")
        ),
    }
    for probe_name, resource_path in read_probes.items():
        read_status = administration_read_probe_status(
            server,
            realm,
            resource_path,
            access_token,
        )
        if read_status != 200:
            raise IdentityOpsError(
                "identity administration positive authorization probe was denied; "
                f"probe={probe_name}, httpStatus={read_status}, response withheld"
            )


def client_credentials_are_rejected(server: str, client_id: str, secret: str) -> bool:
    try:
        status, body = client_credentials_token_response(
            server,
            "master",
            client_id,
            secret,
        )
        return (
            status in {400, 401}
            and body.get("error") == "invalid_client"
        )
    except IdentityOpsError:
        return False


def remove_temporary_authority(
    kcadm: Kcadm,
    client_id: str,
    server: str,
    secret: str,
) -> None:
    observed = exact(
        kcadm.call("get", "clients", "-r", "master", "-q", f"clientId={client_id}") or [],
        client_id,
        "temporary bootstrap client",
    )
    if observed is not None:
        kcadm.call("delete", f"clients/{observed['id']}", "-r", "master")
    if not client_credentials_are_rejected(server, client_id, secret):
        raise IdentityOpsError("temporary bootstrap authority still grants new tokens")


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
    parser.add_argument("--rotation-epoch", default=os.environ.get("WEAVE_IDENTITY_ROTATION_EPOCH") or None)
    args = parser.parse_args()
    kcadm: Kcadm | None = None
    temporary_authority_active = False
    try:
        desired = json.loads(args.desired.read_text(encoding="utf-8"))
        bootstrap_secret = private_value(args.password_file)
        kcadm = Kcadm(args.kcadm, Path("/tmp/kcadm.config"))
        kcadm.authenticate(args.server, args.bootstrap_client, bootstrap_secret)
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
                remaining = ",".join(
                    f"{operation.action}:{operation.key}"
                    for operation in second
                )
                raise IdentityOpsError(
                    "readback did not converge to an empty second plan; "
                    f"remaining={remaining}"
                )
            public_issuer = (
                f"{str(desired['realm']['frontendUrl']).rstrip('/')}"
                f"/realms/{desired['realm']['name']}"
            )
            probe_client_credentials(
                args.server,
                desired["realm"]["name"],
                desired.get("clients", []),
                public_issuer,
            )
            probe_identity_admin_authorization(
                kcadm,
                args.server,
                desired["realm"]["name"],
                desired.get("clients", []),
                desired.get("organizations", []),
            )
        elif args.command == "verify" and operations:
            raise IdentityOpsError("verification found a non-empty plan")
        remove_temporary_authority(
            kcadm,
            args.bootstrap_client,
            args.server,
            bootstrap_secret,
        )
        temporary_authority_active = False
        write_evidence(args.evidence, evidence(args.command, desired, reported_operations, second_empty))
        print(f"identity-ops: {args.command} complete; operations={len(reported_operations)}")
        return 0
    except (IdentityOpsError, KeyError, OSError, json.JSONDecodeError) as error:
        if temporary_authority_active and kcadm is not None:
            try:
                remove_temporary_authority(
                    kcadm,
                    args.bootstrap_client,
                    args.server,
                    bootstrap_secret,
                )
            except IdentityOpsError:
                print("WEAVE_IDENTITY_OPS_ERROR temporary bootstrap authority cleanup also failed", file=sys.stderr)
        print(f"WEAVE_IDENTITY_OPS_ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
