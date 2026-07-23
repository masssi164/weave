#!/usr/bin/env python3
"""Desired-state Keycloak reconciler behind the protected kcadm boundary.

This module is installed with the root-owned supervisor package.  It never
loads executable code from the deployment candidate.  The candidate supplies
only schema-validated JSON documents; all HTTP paths, request bodies,
dependency ordering, identity resolution and read-back coverage are built by
this module.

Ordinary reconciliation creates and updates managed resources and reconciles
declared associations.  It never deletes a resource.  In particular, the
fixed group tree is created as one parentless ``/weave`` root followed by five
children through ``/{parent-id}/children``; a slash-containing flat group name
is never accepted as an equivalent representation.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Protocol

import rfc8785


UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
EMPTY_DIGEST = "sha256:" + hashlib.sha256(rfc8785.dumps([])).hexdigest()


class ReconcileError(RuntimeError):
    pass


class Kcadm(Protocol):
    def execute(self, action: dict[str, object]) -> object | None: ...


def sha256_ref(value: object) -> str:
    return "sha256:" + hashlib.sha256(rfc8785.dumps(value)).hexdigest()


def _keyed(values: object, label: str) -> list[dict[str, Any]]:
    if not isinstance(values, list) or any(
        not isinstance(value, dict) or not isinstance(value.get("key"), str)
        for value in values
    ):
        raise ReconcileError(f"desired {label} are malformed")
    result: list[dict[str, Any]] = values
    keys = [value["key"] for value in result]
    if len(keys) != len(set(keys)):
        raise ReconcileError(f"desired {label} contain duplicate semantic keys")
    return result


def _index(values: list[dict[str, Any]], field: str, label: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for value in values:
        identity = value.get(field)
        if not isinstance(identity, str) or not identity:
            raise ReconcileError(f"{label} response omitted {field}")
        if identity in result:
            raise ReconcileError(f"{label} response contains duplicate {field}")
        result[identity] = value
    return result


def _bool_string(value: object) -> str:
    return "true" if value is True or str(value).lower() == "true" else "false"


def realm_representation(desired: dict[str, Any]) -> dict[str, object]:
    smtp = desired.get("smtp")
    if not isinstance(smtp, dict):
        raise ReconcileError("desired realm SMTP is malformed")
    smtp_server: dict[str, str] = {
        "host": str(smtp["host"]),
        "port": str(smtp["port"]),
        "from": str(smtp["fromAddress"]),
        "fromDisplayName": str(smtp["fromDisplayName"]),
        "ssl": _bool_string(smtp["ssl"]),
        "starttls": _bool_string(smtp["startTls"]),
    }
    if "usernameRef" in smtp:
        smtp_server["user"] = str(smtp["usernameRef"])
        smtp_server["auth"] = "true"
    if "passwordRef" in smtp:
        smtp_server["password"] = str(smtp["passwordRef"])
    return {
        "realm": desired["name"],
        "enabled": desired["enabled"],
        "frontendUrl": desired["frontendUrl"],
        "organizationsEnabled": desired["organizationsEnabled"],
        "adminPermissionsEnabled": desired["adminPermissionsEnabled"],
        "registrationAllowed": desired["registrationAllowed"],
        "directAccessGrantsEnabled": desired["directGrantAllowed"],
        "sslRequired": desired["sslRequired"],
        "loginWithEmailAllowed": desired["loginWithEmailAllowed"],
        "duplicateEmailsAllowed": desired["duplicateEmailsAllowed"],
        "accessTokenLifespan": desired["accessTokenLifespanSeconds"],
        "eventsListeners": desired["eventListeners"],
        "smtpServer": smtp_server,
    }


def required_action_representation(desired: dict[str, Any]) -> dict[str, object]:
    return {
        "alias": desired["alias"],
        "name": desired["name"],
        "enabled": desired["enabled"],
        "defaultAction": desired["defaultAction"],
    }


def organization_representation(desired: dict[str, Any]) -> dict[str, object]:
    return {
        "name": desired["name"],
        "alias": desired["alias"],
        "enabled": True,
        "description": desired["description"],
        "redirectUrl": desired["redirectUri"],
    }


def role_representation(desired: dict[str, Any]) -> dict[str, object]:
    return {
        "name": desired["name"],
        "description": f"Managed by Weave ({desired['key']})",
        "composite": False,
        "clientRole": desired["scope"] == "client",
    }


def scope_representation(desired: dict[str, Any]) -> dict[str, object]:
    return {
        "name": desired["name"],
        "description": f"Managed by Weave ({desired['key']})",
        "protocol": desired["protocol"],
    }


def mapper_representation(desired: dict[str, Any]) -> dict[str, object]:
    common = {
        "name": desired["name"],
        "protocol": "openid-connect",
    }
    if desired["mapperType"] == "group-membership":
        return {
            **common,
            "protocolMapper": "oidc-group-membership-mapper",
            "config": {
                "claim.name": desired["claimName"],
                "full.path": _bool_string(desired["fullPath"]),
                "id.token.claim": _bool_string(desired["addToIdToken"]),
                "access.token.claim": _bool_string(desired["addToAccessToken"]),
                "userinfo.token.claim": _bool_string(desired["addToUserInfo"]),
            },
        }
    if desired["mapperType"] == "audience":
        return {
            **common,
            "protocolMapper": "oidc-audience-mapper",
            "config": {
                "included.custom.audience": desired["includedCustomAudience"],
                "id.token.claim": _bool_string(desired["addToIdToken"]),
                "access.token.claim": _bool_string(desired["addToAccessToken"]),
                "userinfo.token.claim": _bool_string(desired["addToUserInfo"]),
            },
        }
    raise ReconcileError("unsupported desired protocol mapper type")


def client_representation(desired: dict[str, Any]) -> dict[str, object]:
    authentication = desired["authenticationMethod"]
    authenticator = "client-jwt" if authentication == "private_key_jwt" else "client-secret"
    attributes: dict[str, str] = {
        "client_credentials.use_refresh_token": _bool_string(
            desired["useRefreshTokensForClientCredentials"]
        ),
        "standard.token.exchange.enabled": _bool_string(
            desired["standardTokenExchangeEnabled"]
        ),
    }
    if desired.get("pkceMethod"):
        attributes["pkce.code.challenge.method"] = str(desired["pkceMethod"])
    if desired.get("postLogoutRedirectUris"):
        attributes["post.logout.redirect.uris"] = "##".join(desired["postLogoutRedirectUris"])
    if desired.get("accessTokenLifespanSeconds"):
        attributes["access.token.lifespan"] = str(desired["accessTokenLifespanSeconds"])
    if desired.get("rfc9068AccessToken") is True:
        attributes["access.token.header.type.rfc9068"] = "true"
    if authentication == "private_key_jwt":
        attributes.update(
            {
                "use.jwks.string": "true",
                "jwks.string": "public-jwks:" + str(desired["keyRef"]),
                "token.endpoint.auth.signing.alg": "PS256",
            }
        )
    representation: dict[str, object] = {
        "clientId": desired["clientId"],
        "name": f"Managed by Weave ({desired['key']})",
        "protocol": desired["protocol"],
        "enabled": desired["enabled"],
        "publicClient": desired["publicClient"],
        "standardFlowEnabled": desired["standardFlowEnabled"],
        "implicitFlowEnabled": desired["implicitFlowEnabled"],
        "serviceAccountsEnabled": desired["serviceAccountsEnabled"],
        "directAccessGrantsEnabled": desired["directAccessGrantsEnabled"],
        "clientAuthenticatorType": authenticator,
        "fullScopeAllowed": desired["fullScopeAllowed"],
        "redirectUris": desired["redirectUris"],
        "webOrigins": desired["webOrigins"],
        "attributes": attributes,
    }
    if "secretRef" in desired:
        representation["secret"] = desired["secretRef"]
    return representation


def client_policy_documents(
    desired: list[dict[str, Any]], clients: dict[str, dict[str, Any]], scopes: dict[str, dict[str, Any]]
) -> tuple[dict[str, object], dict[str, object]]:
    policies: list[dict[str, object]] = []
    profiles: list[dict[str, object]] = []
    for item in desired:
        requester = clients[item["requesterClientKey"]]["clientId"]
        allowed_scopes = [scopes[key]["name"] for key in item["allowedScopeRefs"]]
        configuration = {
            "requesterClientId": requester,
            "requestedAudience": item["requestedAudience"],
            "subjectClientIdPattern": item["subjectClientIdPattern"],
            "allowedScopes": allowed_scopes,
            "grantTypes": item["grantTypes"],
        }
        profile_name = item["name"] + "-profile"
        policies.append(
            {
                "name": item["name"],
                "description": f"Managed by Weave ({item['key']})",
                "enabled": item["enabled"],
                "conditions": [
                    {"condition": "any-client", "configuration": configuration}
                ],
                "profiles": [profile_name],
            }
        )
        profiles.append(
            {
                "name": profile_name,
                "description": f"Managed by Weave ({item['key']})",
                "executors": [
                    {"executor": executor, "configuration": configuration}
                    for executor in item["executors"]
                ],
            }
        )
    return {"policies": policies}, {"profiles": profiles}


@dataclass
class Inventory:
    realm: dict[str, Any] | None
    required_actions: dict[str, dict[str, Any]]
    organizations: dict[str, dict[str, Any]]
    top_groups: dict[str, dict[str, Any]]
    groups_by_key: dict[str, dict[str, Any]]
    realm_roles: dict[str, dict[str, Any]]
    clients: dict[str, dict[str, Any]]
    scopes: dict[str, dict[str, Any]]
    client_roles: dict[str, dict[str, dict[str, Any]]]
    mappers: dict[str, dict[str, dict[str, Any]]]
    service_accounts: dict[str, dict[str, Any]]


class KeycloakReconciler:
    def __init__(
        self,
        *,
        desired: dict[str, Any],
        sanitizer_profile: dict[str, Any],
        corpus_root: Path,
        reconciliation_id: str,
        temporary_client_id: str,
        client: Kcadm,
    ) -> None:
        self.desired = desired
        self.profile = sanitizer_profile
        self.corpus_root = corpus_root.resolve()
        self.reconciliation_id = reconciliation_id
        self.temporary_client_id = temporary_client_id
        self.client = client
        self.actions: list[dict[str, str]] = []
        self._validate_desired_tree()
        self.clients_by_key = {item["key"]: item for item in _keyed(desired["clients"], "clients")}
        self.scopes_by_key = {item["key"]: item for item in _keyed(desired["clientScopes"], "client scopes")}
        self.roles_by_key = {item["key"]: item for item in _keyed(desired["roles"], "roles")}
        self.groups_by_key = {item["key"]: item for item in _keyed(desired["groups"], "groups")}

    def _validate_desired_tree(self) -> None:
        groups = _keyed(self.desired.get("groups"), "groups")
        roots = [group for group in groups if group.get("parentGroupRef") is None]
        if len(roots) != 1 or roots[0].get("key") != "group:weave-root" or roots[0].get("path") != "/weave":
            raise ReconcileError("desired group tree must have the sole parentless group:weave-root at /weave")
        expected = {
            "group:owners": "/weave/owners",
            "group:admins": "/weave/admins",
            "group:members": "/weave/members",
            "group:guests": "/weave/guests",
            "group:weaver-runtime": "/weave/weaver-runtime",
        }
        children = {group["key"]: group for group in groups if group.get("parentGroupRef") is not None}
        if set(children) != set(expected):
            raise ReconcileError("desired group tree must contain exactly the five pinned children")
        for key, path in expected.items():
            if children[key].get("parentGroupRef") != "group:weave-root" or children[key].get("path") != path:
                raise ReconcileError("desired group child has an invalid parent or canonical path")

    def _execute(
        self,
        method: str,
        endpoint: str,
        *,
        query: dict[str, str] | None = None,
        body: object | None = None,
        binding: dict[str, str] | None = None,
        allow_not_found: bool = False,
    ) -> object | None:
        action: dict[str, object] = {
            "method": method,
            "endpoint": endpoint,
            "query": query or {},
            "binding": binding or {},
        }
        if body is not None:
            action["body"] = body
        if allow_not_found:
            action["allowNotFound"] = True
        return self.client.execute(action)

    def _list(self, value: object | None, label: str) -> list[dict[str, Any]]:
        if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
            raise ReconcileError(f"{label} response is not an object array")
        return value

    def _paged(
        self, endpoint: str, label: str, binding: dict[str, str]
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        identities: set[str] = set()
        first = 0
        while True:
            page = self._list(
                self._execute(
                    "GET", endpoint, query={"first": str(first), "max": "100"}, binding=binding
                ),
                label,
            )
            for item in page:
                identity = item.get("id") or item.get("_id")
                if not isinstance(identity, str) or identity in identities:
                    raise ReconcileError(f"{label} response contains a missing or duplicate provider identity")
                identities.add(identity)
            result.extend(page)
            if len(page) < 100:
                return result
            first += 100

    @staticmethod
    def _group_name(group: dict[str, Any]) -> str:
        path = group["path"]
        if not isinstance(path, str) or not path.startswith("/"):
            raise ReconcileError("desired group path is malformed")
        return path.rsplit("/", 1)[-1]

    def inventory(self) -> Inventory:
        realm_value = self._execute("GET", "/admin/realms/weave", allow_not_found=True)
        if realm_value is None:
            return Inventory(None, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        if not isinstance(realm_value, dict):
            raise ReconcileError("realm response is malformed")
        required = _index(
            self._list(
                self._execute(
                    "GET", "/admin/realms/weave/authentication/required-actions",
                    binding={"realmKey": "realm:weave"},
                ),
                "required actions",
            ),
            "alias",
            "required actions",
        )
        organizations = _index(
            self._paged(
                "/admin/realms/weave/organizations", "organizations", {"realmKey": "realm:weave"}
            ),
            "alias",
            "organizations",
        )
        top_groups = _index(
            self._paged("/admin/realms/weave/groups", "groups", {"realmKey": "realm:weave"}),
            "path",
            "groups",
        )
        groups_by_key: dict[str, dict[str, Any]] = {}
        root = top_groups.get("/weave")
        if root is not None:
            groups_by_key["group:weave-root"] = root
            children = self._paged(
                f"/admin/realms/weave/groups/{root['id']}/children",
                "root group children",
                {"groupKey": "group:weave-root"},
            )
            by_path = _index(children, "path", "root group children")
            for key, group in self.groups_by_key.items():
                if key == "group:weave-root":
                    continue
                child = by_path.get(group["path"])
                if child is not None:
                    groups_by_key[key] = child
        realm_roles = _index(
            self._paged("/admin/realms/weave/roles", "realm roles", {"realmKey": "realm:weave"}),
            "name",
            "realm roles",
        )
        clients = _index(
            self._paged("/admin/realms/weave/clients", "clients", {"realmKey": "realm:weave"}),
            "clientId",
            "clients",
        )
        scopes = _index(
            self._list(
                self._execute("GET", "/admin/realms/weave/client-scopes", binding={"realmKey": "realm:weave"}),
                "client scopes",
            ),
            "name",
            "client scopes",
        )
        client_roles: dict[str, dict[str, dict[str, Any]]] = {}
        service_accounts: dict[str, dict[str, Any]] = {}
        for client_key, desired_client in self.clients_by_key.items():
            observed_client = clients.get(desired_client["clientId"])
            if observed_client is None:
                continue
            client_uuid = str(observed_client["id"])
            client_roles[client_key] = _index(
                self._paged(
                    f"/admin/realms/weave/clients/{client_uuid}/roles",
                    f"roles for {client_key}",
                    {"clientKey": client_key},
                ),
                "name",
                f"roles for {client_key}",
            )
            if desired_client["serviceAccountsEnabled"]:
                service_account = self._execute(
                    "GET",
                    f"/admin/realms/weave/clients/{client_uuid}/service-account-user",
                    binding={"clientKey": client_key},
                )
                if not isinstance(service_account, dict):
                    raise ReconcileError(f"service account for {client_key} is malformed")
                service_accounts[client_key] = service_account
        mappers: dict[str, dict[str, dict[str, Any]]] = {}
        for scope_key, desired_scope in self.scopes_by_key.items():
            observed_scope = scopes.get(desired_scope["name"])
            if observed_scope is None:
                continue
            mappers[scope_key] = _index(
                self._list(
                    self._execute(
                        "GET",
                        f"/admin/realms/weave/client-scopes/{observed_scope['id']}/protocol-mappers/models",
                        binding={"scopeKey": scope_key},
                    ),
                    f"mappers for {scope_key}",
                ),
                "name",
                f"mappers for {scope_key}",
            )
        return Inventory(
            realm_value,
            required,
            organizations,
            top_groups,
            groups_by_key,
            realm_roles,
            clients,
            scopes,
            client_roles,
            mappers,
            service_accounts,
        )

    @staticmethod
    def _subset_equal(observed: dict[str, Any], desired: dict[str, object]) -> bool:
        def normalized(value: object) -> object:
            if isinstance(value, dict):
                return {key: normalized(item) for key, item in sorted(value.items())}
            if isinstance(value, list):
                return [normalized(item) for item in value]
            return value

        for key, expected in desired.items():
            if key in {"secret"}:
                continue
            actual = observed.get(key)
            if key == "smtpServer" and isinstance(actual, dict) and isinstance(expected, dict):
                visible = {name: value for name, value in expected.items() if name not in {"user", "password", "auth"}}
                if any(str(actual.get(name)).lower() != str(value).lower() for name, value in visible.items()):
                    return False
                continue
            if key == "attributes" and isinstance(actual, dict) and isinstance(expected, dict):
                # JWKS content is intentionally withheld by the sanitizer.  Its
                # functional proof is a separate protected credential probe.
                visible = {
                    name: value
                    for name, value in expected.items()
                    if name not in {"jwks.string", "use.jwks.string", "token.endpoint.auth.signing.alg"}
                }
                if any(str(actual.get(name)) != str(value) for name, value in visible.items()):
                    return False
                continue
            if normalized(actual) != normalized(expected):
                return False
        return True

    def _record(self, resource_key: str, action: str, result: str) -> None:
        if action in {"attach", "detach"}:
            action = "update"
        self.actions.append({"resourceKey": resource_key, "action": action, "resultCode": result})

    def _mutate_resource(
        self,
        *,
        mode: str,
        resource_key: str,
        observed: dict[str, Any] | None,
        desired_body: dict[str, object],
        create_endpoint: str | None,
        update_endpoint: str | None,
        create_binding: dict[str, str] | None = None,
        update_binding: dict[str, str] | None = None,
    ) -> None:
        if observed is None:
            self._record(resource_key, "create", "planned" if mode != "apply" else "created")
            if mode == "apply":
                if create_endpoint is None:
                    raise ReconcileError(f"managed resource {resource_key} is missing and has no create operation")
                self._execute(
                    "POST", create_endpoint, body=desired_body, binding=create_binding or {"resourceKey": resource_key}
                )
            return
        if self._subset_equal(observed, desired_body):
            self._record(resource_key, "noop", "already-converged")
            return
        self._record(resource_key, "update", "planned" if mode != "apply" else "updated")
        if mode == "apply":
            if update_endpoint is None:
                raise ReconcileError(f"managed resource {resource_key} drifted and has no update operation")
            self._execute(
                "PUT", update_endpoint, body=desired_body, binding=update_binding or {"resourceKey": resource_key}
            )

    def reconcile_resources(self, mode: str) -> Inventory:
        if mode not in {"plan", "apply", "verify"}:
            raise ReconcileError("unsupported ordinary reconciliation mode")
        inventory = self.inventory()
        realm = self.desired["realm"]
        self._mutate_resource(
            mode=mode,
            resource_key=realm["key"],
            observed=inventory.realm,
            desired_body=realm_representation(realm),
            create_endpoint="/admin/realms",
            update_endpoint="/admin/realms/weave",
        )
        if inventory.realm is None:
            if mode != "apply":
                return inventory
            inventory = self.inventory()
            if inventory.realm is None:
                raise ReconcileError("realm create did not become visible")

        for item in _keyed(self.desired["requiredActions"], "required actions"):
            observed = inventory.required_actions.get(item["alias"])
            self._mutate_resource(
                mode=mode,
                resource_key=item["key"],
                observed=observed,
                desired_body=required_action_representation(item),
                create_endpoint=None,
                update_endpoint=f"/admin/realms/weave/authentication/required-actions/{item['alias']}",
            )
        for item in _keyed(self.desired["organizations"], "organizations"):
            observed = inventory.organizations.get(item["alias"])
            self._mutate_resource(
                mode=mode,
                resource_key=item["key"],
                observed=observed,
                desired_body=organization_representation(item),
                create_endpoint="/admin/realms/weave/organizations",
                update_endpoint=(
                    f"/admin/realms/weave/organizations/{observed['id']}" if observed else None
                ),
            )
        for item in self.clients_by_key.values():
            observed = inventory.clients.get(item["clientId"])
            self._mutate_resource(
                mode=mode,
                resource_key=item["key"],
                observed=observed,
                desired_body=client_representation(item),
                create_endpoint="/admin/realms/weave/clients",
                update_endpoint=f"/admin/realms/weave/clients/{observed['id']}" if observed else None,
            )
        for item in self.scopes_by_key.values():
            observed = inventory.scopes.get(item["name"])
            self._mutate_resource(
                mode=mode,
                resource_key=item["key"],
                observed=observed,
                desired_body=scope_representation(item),
                create_endpoint="/admin/realms/weave/client-scopes",
                update_endpoint=f"/admin/realms/weave/client-scopes/{observed['id']}" if observed else None,
            )
        for item in self.roles_by_key.values():
            if item["scope"] == "realm":
                observed = inventory.realm_roles.get(item["name"])
                create = "/admin/realms/weave/roles"
                update = f"/admin/realms/weave/roles-by-id/{observed['id']}" if observed else None
            else:
                client_key = item["clientKey"]
                owner = inventory.clients.get(self.clients_by_key[client_key]["clientId"])
                if owner is None:
                    observed = None
                    create = None
                    update = None
                else:
                    observed = inventory.client_roles.get(client_key, {}).get(item["name"])
                    create = f"/admin/realms/weave/clients/{owner['id']}/roles"
                    update = f"{create}/{item['name']}" if observed else None
            self._mutate_resource(
                mode=mode,
                resource_key=item["key"],
                observed=observed,
                desired_body=role_representation(item),
                create_endpoint=create,
                update_endpoint=update,
            )

        root_desired = self.groups_by_key["group:weave-root"]
        root_observed = inventory.groups_by_key.get("group:weave-root")
        self._mutate_resource(
            mode=mode,
            resource_key="group:weave-root",
            observed=root_observed,
            desired_body={"name": self._group_name(root_desired)},
            create_endpoint="/admin/realms/weave/groups",
            update_endpoint=f"/admin/realms/weave/groups/{root_observed['id']}" if root_observed else None,
            create_binding={"resourceKey": "group:weave-root", "parentResourceKey": ""},
            update_binding={"resourceKey": "group:weave-root", "parentResourceKey": ""},
        )
        if mode == "apply" and root_observed is None:
            inventory = self.inventory()
            root_observed = inventory.groups_by_key.get("group:weave-root")
            if root_observed is None:
                raise ReconcileError("root group create did not become visible")
        for key, item in self.groups_by_key.items():
            if key == "group:weave-root":
                continue
            observed = inventory.groups_by_key.get(key)
            root_observed = inventory.groups_by_key.get("group:weave-root")
            self._mutate_resource(
                mode=mode,
                resource_key=key,
                observed=observed,
                desired_body={"name": self._group_name(item)},
                create_endpoint=(
                    f"/admin/realms/weave/groups/{root_observed['id']}/children"
                    if root_observed else None
                ),
                update_endpoint=f"/admin/realms/weave/groups/{observed['id']}" if observed else None,
                create_binding={"resourceKey": key, "parentResourceKey": "group:weave-root"},
                update_binding={"resourceKey": key, "parentResourceKey": "group:weave-root"},
            )

        if mode == "apply":
            inventory = self.inventory()
        for scope_key, item in self.scopes_by_key.items():
            observed_scope = inventory.scopes.get(item["name"])
            if observed_scope is None:
                continue
            current = inventory.mappers.get(scope_key, {})
            for mapper in _keyed(item["mappers"], f"mappers for {scope_key}"):
                observed_mapper = current.get(mapper["name"])
                self._mutate_resource(
                    mode=mode,
                    resource_key=mapper["key"],
                    observed=observed_mapper,
                    desired_body=mapper_representation(mapper),
                    create_endpoint=f"/admin/realms/weave/client-scopes/{observed_scope['id']}/protocol-mappers/models",
                    update_endpoint=(
                        f"/admin/realms/weave/client-scopes/{observed_scope['id']}/protocol-mappers/models/{observed_mapper['id']}"
                        if observed_mapper else None
                    ),
                )
        return self.inventory() if mode == "apply" else inventory

    def _provider_scope(
        self, reference: str, inventory: Inventory
    ) -> dict[str, Any]:
        if reference.startswith("builtin-scope:"):
            name = reference.removeprefix("builtin-scope:")
        else:
            desired = self.scopes_by_key.get(reference)
            if desired is None:
                raise ReconcileError(f"desired association references unknown scope {reference}")
            name = str(desired["name"])
        observed = inventory.scopes.get(name)
        if observed is None or not UUID.fullmatch(str(observed.get("id", ""))):
            raise ReconcileError(f"desired association cannot resolve scope {reference}")
        return observed

    def _provider_role(
        self, reference: str, inventory: Inventory
    ) -> tuple[str, dict[str, Any]]:
        if reference.startswith("builtin-role:realm-management:"):
            name = reference.removeprefix("builtin-role:realm-management:")
            container = inventory.clients.get("realm-management")
            if container is None:
                raise ReconcileError("realm-management client is unavailable")
            observed = self._execute(
                "GET",
                f"/admin/realms/weave/clients/{container['id']}/roles/{name}",
                binding={
                    "roleContainerKey": "builtin-client:realm-management",
                    "roleRef": reference,
                },
            )
            if not isinstance(observed, dict) or observed.get("name") != name:
                raise ReconcileError(f"builtin role is unavailable: {reference}")
            return "builtin-client:realm-management", observed
        desired = self.roles_by_key.get(reference)
        if desired is None:
            raise ReconcileError(f"desired association references unknown role {reference}")
        if desired["scope"] == "realm":
            observed = inventory.realm_roles.get(desired["name"])
            container_key = "realm"
        else:
            container_key = str(desired["clientKey"])
            observed = inventory.client_roles.get(container_key, {}).get(desired["name"])
        if observed is None or not UUID.fullmatch(str(observed.get("id", ""))):
            raise ReconcileError(f"desired association cannot resolve role {reference}")
        return container_key, observed

    def _sync_scope_association(
        self,
        *,
        mode: str,
        client_key: str,
        client_uuid: str,
        kind: str,
        desired_refs: list[str],
        inventory: Inventory,
    ) -> None:
        suffix = "default-client-scopes" if kind == "default" else "optional-client-scopes"
        operation = "client-default-scopes" if kind == "default" else "client-optional-scopes"
        endpoint = f"/admin/realms/weave/clients/{client_uuid}/{suffix}"
        binding = {"clientKey": client_key}
        current = self._list(self._execute("GET", endpoint, binding=binding), operation)
        current_by_id = _index(current, "id", operation)
        desired_by_id = {
            str(scope["id"]): (reference, scope)
            for reference in desired_refs
            for scope in [self._provider_scope(reference, inventory)]
        }
        for provider_id, (reference, _scope) in sorted(desired_by_id.items()):
            if provider_id in current_by_id:
                continue
            self._record(f"association:{client_key}:{kind}:{reference}", "attach", "planned" if mode != "apply" else "attached")
            if mode == "apply":
                self._execute(
                    "PUT",
                    f"{endpoint}/{provider_id}",
                    body={},
                    binding={**binding, "scopeRef": reference},
                )
        for provider_id, scope in sorted(current_by_id.items()):
            if provider_id in desired_by_id:
                continue
            opaque = "provider-id-sha256:" + hashlib.sha256(provider_id.encode()).hexdigest()
            self._record(f"association:{client_key}:{kind}:{opaque}", "detach", "planned" if mode != "apply" else "detached")
            if mode == "apply":
                self._execute(
                    "DELETE",
                    f"{endpoint}/{provider_id}",
                    binding={**binding, "scopeRef": opaque},
                )

    def _sync_role_mapping(
        self,
        *,
        mode: str,
        endpoint: str,
        operation: str,
        binding: dict[str, str],
        desired_roles: list[tuple[str, dict[str, Any]]],
    ) -> None:
        current = self._list(self._execute("GET", endpoint, binding=binding), operation)
        current_by_id = _index(current, "id", operation)
        desired_by_id = {str(role["id"]): (reference, role) for reference, role in desired_roles}
        additions = [value for key, value in desired_by_id.items() if key not in current_by_id]
        removals = [value for key, value in current_by_id.items() if key not in desired_by_id]
        for reference, _role in additions:
            self._record(f"association:{operation}:{reference}", "attach", "planned" if mode != "apply" else "attached")
        for role in removals:
            opaque = "provider-id-sha256:" + hashlib.sha256(str(role["id"]).encode()).hexdigest()
            self._record(f"association:{operation}:{opaque}", "detach", "planned" if mode != "apply" else "detached")
        if mode == "apply" and additions:
            self._execute(
                "POST",
                endpoint,
                body=[role for _reference, role in additions],
                binding=binding,
            )
        if mode == "apply" and removals:
            self._execute("DELETE", endpoint, body=removals, binding=binding)

    def reconcile_associations(self, mode: str, inventory: Inventory) -> Inventory:
        """Reconcile the complete closed association cross-product.

        DELETE here detaches an association; it never deletes a role, scope,
        group, client or other managed/unmanaged resource.
        """

        for client_key, desired_client in self.clients_by_key.items():
            client = inventory.clients.get(desired_client["clientId"])
            if client is None:
                raise ReconcileError(f"association owner is missing: {client_key}")
            self._sync_scope_association(
                mode=mode,
                client_key=client_key,
                client_uuid=str(client["id"]),
                kind="default",
                desired_refs=list(desired_client["defaultClientScopes"]),
                inventory=inventory,
            )
            self._sync_scope_association(
                mode=mode,
                client_key=client_key,
                client_uuid=str(client["id"]),
                kind="optional",
                desired_refs=list(desired_client["optionalClientScopes"]),
                inventory=inventory,
            )

        for scope_key, desired_scope in self.scopes_by_key.items():
            scope = inventory.scopes.get(desired_scope["name"])
            if scope is None:
                raise ReconcileError(f"scope mapping owner is missing: {scope_key}")
            by_container: dict[str, list[tuple[str, dict[str, Any]]]] = {
                "realm": [],
                **{key: [] for key in self.clients_by_key},
            }
            for reference in desired_scope["roleScopeRefs"]:
                container, role = self._provider_role(reference, inventory)
                if container not in by_container:
                    raise ReconcileError(f"scope mapping targets unsupported role container {container}")
                by_container[container].append((reference, role))
            self._sync_role_mapping(
                mode=mode,
                endpoint=f"/admin/realms/weave/client-scopes/{scope['id']}/scope-mappings/realm",
                operation="scope-realm-role-mappings",
                binding={"scopeKey": scope_key},
                desired_roles=by_container["realm"],
            )
            for client_key, client_desired in self.clients_by_key.items():
                client = inventory.clients[client_desired["clientId"]]
                self._sync_role_mapping(
                    mode=mode,
                    endpoint=f"/admin/realms/weave/client-scopes/{scope['id']}/scope-mappings/clients/{client['id']}",
                    operation="scope-client-role-mappings",
                    binding={"scopeKey": scope_key, "clientKey": client_key},
                    desired_roles=by_container[client_key],
                )

        for group_key, desired_group in self.groups_by_key.items():
            group = inventory.groups_by_key.get(group_key)
            if group is None:
                raise ReconcileError(f"group mapping owner is missing: {group_key}")
            by_container = {"realm": [], **{key: [] for key in self.clients_by_key}}
            for reference in desired_group["roleRefs"]:
                container, role = self._provider_role(reference, inventory)
                if container not in by_container:
                    raise ReconcileError(f"group mapping targets unsupported role container {container}")
                by_container[container].append((reference, role))
            self._sync_role_mapping(
                mode=mode,
                endpoint=f"/admin/realms/weave/groups/{group['id']}/role-mappings/realm",
                operation="group-realm-role-mappings",
                binding={"groupKey": group_key},
                desired_roles=by_container["realm"],
            )
            for client_key, client_desired in self.clients_by_key.items():
                client = inventory.clients[client_desired["clientId"]]
                self._sync_role_mapping(
                    mode=mode,
                    endpoint=f"/admin/realms/weave/groups/{group['id']}/role-mappings/clients/{client['id']}",
                    operation="group-client-role-mappings",
                    binding={"groupKey": group_key, "clientKey": client_key},
                    desired_roles=by_container[client_key],
                )

        grants_by_client = {
            key: []
            for key, client in self.clients_by_key.items()
            if client["serviceAccountsEnabled"]
        }
        for grant in _keyed(self.desired["serviceAccountRoleGrants"], "service-account grants"):
            if grant["clientKey"] not in grants_by_client:
                raise ReconcileError("service-account grant targets a disabled account")
            grants_by_client[grant["clientKey"]].extend(grant["roleRefs"])
        for service_key, references in grants_by_client.items():
            account = inventory.service_accounts.get(service_key)
            if account is None:
                raise ReconcileError(f"service account is missing: {service_key}")
            groups = self._paged(
                f"/admin/realms/weave/users/{account['id']}/groups",
                "service-account-groups",
                {"serviceAccountClientKey": service_key},
            )
            for group in groups:
                opaque = "provider-id-sha256:" + hashlib.sha256(str(group["id"]).encode()).hexdigest()
                self._record(f"association:{service_key}:group:{opaque}", "detach", "planned" if mode != "apply" else "detached")
                if mode == "apply":
                    self._execute(
                        "DELETE",
                        f"/admin/realms/weave/users/{account['id']}/groups/{group['id']}",
                        binding={"serviceAccountClientKey": service_key, "groupRef": opaque},
                    )
            by_container: dict[str, list[tuple[str, dict[str, Any]]]] = {
                "realm": [],
                "builtin-client:realm-management": [],
                **{key: [] for key in self.clients_by_key},
            }
            for reference in references:
                container, role = self._provider_role(reference, inventory)
                by_container[container].append((reference, role))
            self._sync_role_mapping(
                mode=mode,
                endpoint=f"/admin/realms/weave/users/{account['id']}/role-mappings/realm",
                operation="service-account-realm-role-mappings",
                binding={"serviceAccountClientKey": service_key},
                desired_roles=by_container["realm"],
            )
            for container_key in ["builtin-client:realm-management", *self.clients_by_key]:
                if container_key == "builtin-client:realm-management":
                    container = inventory.clients.get("realm-management")
                else:
                    container = inventory.clients.get(self.clients_by_key[container_key]["clientId"])
                if container is None:
                    raise ReconcileError(f"role container is missing: {container_key}")
                self._sync_role_mapping(
                    mode=mode,
                    endpoint=f"/admin/realms/weave/users/{account['id']}/role-mappings/clients/{container['id']}",
                    operation="service-account-client-role-mappings",
                    binding={
                        "serviceAccountClientKey": service_key,
                        "roleContainerKey": container_key,
                    },
                    desired_roles=by_container[container_key],
                )
        return self.inventory() if mode == "apply" else inventory

    def _coverage_module(self) -> Any:
        path = self.corpus_root / "tools/keycloak_query_coverage.py"
        if not path.is_file() or path.is_symlink():
            raise ReconcileError("pinned Keycloak query coverage helper is unavailable")
        specification = importlib.util.spec_from_file_location("weave_keycloak_query_coverage", path)
        if specification is None or specification.loader is None:
            raise ReconcileError("pinned Keycloak query coverage helper cannot be loaded")
        module = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(module)
        return module

    def _provider_identity(self, item: dict[str, Any]) -> str:
        identity = item.get("id") or item.get("_id") or item.get("name") or item.get("alias")
        if not isinstance(identity, str) or not identity:
            raise ReconcileError("read-back item has no stable provider identity")
        return identity

    def _coverage_target(
        self, operation: str, bindings: dict[str, str], inventory: Inventory
    ) -> tuple[str, Callable[[dict[str, Any]], str | None]]:
        clients = {
            key: inventory.clients.get(item["clientId"])
            for key, item in self.clients_by_key.items()
        }
        scopes = {
            key: inventory.scopes.get(item["name"])
            for key, item in self.scopes_by_key.items()
        }
        groups = inventory.groups_by_key
        client_id_to_key = {item["clientId"]: key for key, item in self.clients_by_key.items()}
        scope_name_to_key = {item["name"]: key for key, item in self.scopes_by_key.items()}
        role_name_to_key = {
            (item.get("clientKey"), item["name"]): key for key, item in self.roles_by_key.items()
        }
        group_path_to_key = {item["path"]: key for key, item in self.groups_by_key.items()}
        organization_alias_to_key = {
            item["alias"]: item["key"] for item in _keyed(self.desired["organizations"], "organizations")
        }
        required_alias_to_key = {
            item["alias"]: item["key"] for item in _keyed(self.desired["requiredActions"], "required actions")
        }
        mapper_names = {
            (scope["key"], mapper["name"]): mapper["key"]
            for scope in self.scopes_by_key.values()
            for mapper in _keyed(scope["mappers"], f"mappers for {scope['key']}")
        }

        def require(mapping: dict[str, dict[str, Any] | None], key: str, label: str) -> dict[str, Any]:
            value = mapping.get(key)
            if not isinstance(value, dict) or not UUID.fullmatch(str(value.get("id", ""))):
                raise ReconcileError(f"read-back cannot resolve {label} {key}")
            return value

        if operation == "required-actions":
            return "/admin/realms/weave/authentication/required-actions", lambda item: required_alias_to_key.get(str(item.get("alias")))
        if operation == "organizations":
            return "/admin/realms/weave/organizations", lambda item: organization_alias_to_key.get(str(item.get("alias")))
        if operation == "groups":
            return "/admin/realms/weave/groups", lambda item: group_path_to_key.get(str(item.get("path")))
        if operation == "group-children":
            group = require(groups, bindings["groupKey"], "group")
            return f"/admin/realms/weave/groups/{group['id']}/children", lambda item: group_path_to_key.get(str(item.get("path")))
        if operation == "realm-roles":
            return "/admin/realms/weave/roles", lambda item: role_name_to_key.get((None, str(item.get("name"))))
        if operation == "clients":
            return "/admin/realms/weave/clients", lambda item: client_id_to_key.get(str(item.get("clientId")))
        if operation == "client-scopes":
            return "/admin/realms/weave/client-scopes", lambda item: scope_name_to_key.get(str(item.get("name"))) or (f"builtin-scope:{item['name']}" if isinstance(item.get("name"), str) else None)
        if operation == "client-roles":
            client_key = bindings["clientKey"]
            owner = require(clients, client_key, "client")
            return f"/admin/realms/weave/clients/{owner['id']}/roles", lambda item: role_name_to_key.get((client_key, str(item.get("name"))))
        if operation in {"client-default-scopes", "client-optional-scopes"}:
            client_key = bindings["clientKey"]
            owner = require(clients, client_key, "client")
            suffix = "default-client-scopes" if operation == "client-default-scopes" else "optional-client-scopes"
            return f"/admin/realms/weave/clients/{owner['id']}/{suffix}", lambda item: scope_name_to_key.get(str(item.get("name"))) or (f"builtin-scope:{item['name']}" if isinstance(item.get("name"), str) else None)
        if operation == "scope-mappers":
            scope_key = bindings["scopeKey"]
            scope = require(scopes, scope_key, "client scope")
            return f"/admin/realms/weave/client-scopes/{scope['id']}/protocol-mappers/models", lambda item: mapper_names.get((scope_key, str(item.get("name"))))
        if operation in {"scope-realm-role-mappings", "scope-client-role-mappings"}:
            scope_key = bindings["scopeKey"]
            scope = require(scopes, scope_key, "client scope")
            if operation == "scope-realm-role-mappings":
                return f"/admin/realms/weave/client-scopes/{scope['id']}/scope-mappings/realm", lambda item: role_name_to_key.get((None, str(item.get("name"))))
            client_key = bindings["clientKey"]
            owner = require(clients, client_key, "client")
            return f"/admin/realms/weave/client-scopes/{scope['id']}/scope-mappings/clients/{owner['id']}", lambda item: role_name_to_key.get((client_key, str(item.get("name"))))
        if operation in {"group-realm-role-mappings", "group-client-role-mappings"}:
            group = require(groups, bindings["groupKey"], "group")
            if operation == "group-realm-role-mappings":
                return f"/admin/realms/weave/groups/{group['id']}/role-mappings/realm", lambda item: role_name_to_key.get((None, str(item.get("name"))))
            client_key = bindings["clientKey"]
            owner = require(clients, client_key, "client")
            return f"/admin/realms/weave/groups/{group['id']}/role-mappings/clients/{owner['id']}", lambda item: role_name_to_key.get((client_key, str(item.get("name"))))
        if operation == "client-service-account":
            client_key = bindings["clientKey"]
            owner = require(clients, client_key, "client")
            expected = "service-account:" + self.clients_by_key[client_key]["clientId"]
            return f"/admin/realms/weave/clients/{owner['id']}/service-account-user", lambda _item: expected
        if operation.startswith("service-account-"):
            service_key = bindings["serviceAccountClientKey"]
            account = inventory.service_accounts.get(service_key)
            if not isinstance(account, dict) or not UUID.fullmatch(str(account.get("id", ""))):
                raise ReconcileError(f"read-back cannot resolve service account {service_key}")
            if operation == "service-account-groups":
                return f"/admin/realms/weave/users/{account['id']}/groups", lambda item: group_path_to_key.get(str(item.get("path")))
            if operation == "service-account-realm-role-mappings":
                return f"/admin/realms/weave/users/{account['id']}/role-mappings/realm", lambda item: role_name_to_key.get((None, str(item.get("name"))))
            container_key = bindings["roleContainerKey"]
            if container_key == "builtin-client:realm-management":
                container = inventory.clients.get("realm-management")
                if not isinstance(container, dict):
                    raise ReconcileError("realm-management role container is unavailable")
                mapper = lambda item: f"builtin-role:realm-management:{item['name']}" if isinstance(item.get("name"), str) else None
            else:
                container = require(clients, container_key, "role container")
                mapper = lambda item: role_name_to_key.get((container_key, str(item.get("name"))))
            return f"/admin/realms/weave/users/{account['id']}/role-mappings/clients/{container['id']}", mapper
        if operation in {"client-policies", "client-policy-profiles"}:
            suffix = "policies" if operation == "client-policies" else "profiles"
            policy_names = {item["name"]: item["key"] for item in _keyed(self.desired["clientPolicies"], "client policies")}
            if operation == "client-policy-profiles":
                policy_names = {item["name"] + "-profile": item["key"] for item in _keyed(self.desired["clientPolicies"], "client policies")}
            return f"/admin/realms/weave/client-policies/{suffix}", lambda item: policy_names.get(str(item.get("name")))
        if operation == "master-temporary-client-discovery":
            return "/admin/realms/master/clients", lambda item: str(item.get("id")) if item.get("clientId") == self.temporary_client_id else None
        if operation.startswith("fgap-"):
            admin = inventory.clients.get("admin-permissions")
            if not isinstance(admin, dict) or not UUID.fullmatch(str(admin.get("id", ""))):
                raise ReconcileError("admin-permissions client is unavailable")
            base = f"/admin/realms/weave/clients/{admin['id']}/authz/resource-server"
            if operation == "fgap-resources":
                provider_refs = {
                    str(value["id"]): key for key, value in inventory.groups_by_key.items()
                }
                provider_refs.update(
                    {
                        str(value["id"]): item["key"]
                        for item in _keyed(self.desired["organizations"], "organizations")
                        if (value := inventory.organizations.get(item["alias"])) is not None
                    }
                )
                def resource(item: dict[str, Any]) -> str | None:
                    resource_type = str(item.get("type", "")).lower()
                    if not resource_type:
                        return None
                    name = str(item.get("name", ""))
                    if name.lower() == resource_type:
                        return f"admin-resource:{resource_type}:*"
                    reference = provider_refs.get(name)
                    return f"admin-resource:{resource_type}:{reference}" if reference else None
                return base + "/resource", resource
            if operation == "fgap-scopes":
                return base + "/scope", lambda item: f"admin-scope:{item['name']}" if isinstance(item.get("name"), str) else None
            if operation == "fgap-user-policies":
                names = {item["name"]: item["key"] for item in _keyed(self.desired["fineGrainedAdminPermissions"]["subjectPolicies"], "FGAP subject policies")}
                return base + "/policy/user", lambda item: names.get(str(item.get("name")))
            names = {item["name"]: item["key"] for item in _keyed(self.desired["fineGrainedAdminPermissions"]["permissions"], "FGAP permissions")}
            return base + "/permission/scope", lambda item: names.get(str(item.get("name")))
        raise ReconcileError(f"no exact read-back target for operation {operation}")

    def query_coverage(
        self, inventory: Inventory, temporary_client_uuid: str
    ) -> list[dict[str, object]]:
        helper = self._coverage_module()
        expectations = helper.expected_query_binding_expectations(
            self.desired,
            self.reconciliation_id,
            self.temporary_client_id,
            temporary_client_uuid,
        )
        query_bindings = self.profile["bindingSemantics"]["operationQueryBindings"]
        coverage: list[dict[str, object]] = []
        for expectation in expectations:
            document = expectation["document"]
            operation = str(document["operationId"])
            bindings = document["semanticBindings"]
            endpoint, mapper = self._coverage_target(operation, bindings, inventory)
            contract = query_bindings[operation]
            cardinality = contract["responseCardinality"]
            if cardinality == "complete-offset-pages":
                items: list[dict[str, Any]] = []
                counts: list[int] = []
                first = 0
                seen: set[str] = set()
                while True:
                    page = self._list(
                        self._execute(
                            "GET", endpoint,
                            query={"first": str(first), "max": "100"},
                            binding=bindings,
                        ),
                        operation,
                    )
                    for item in page:
                        identity = self._provider_identity(item)
                        if identity in seen:
                            raise ReconcileError(f"{operation} paged response contains a duplicate identity")
                        seen.add(identity)
                    items.extend(page)
                    counts.append(len(page))
                    if len(page) < 100:
                        break
                    first += 100
                offsets = [index * 100 for index in range(len(counts))]
                terminal = "short-page"
            else:
                value = self._execute(
                    "GET",
                    endpoint,
                    query=(
                        {"clientId": self.temporary_client_id, "first": "0", "max": "2"}
                        if operation == "master-temporary-client-discovery" else {}
                    ),
                    binding=bindings,
                )
                if cardinality == "exactly-one":
                    items = value if isinstance(value, list) else [value]
                    if len(items) != 1 or not isinstance(items[0], dict):
                        raise ReconcileError(f"{operation} did not return exactly one object")
                    terminal = "exact-cardinality"
                else:
                    if operation in {"client-policies", "client-policy-profiles"} and isinstance(value, dict):
                        items = value.get("policies" if operation == "client-policies" else "profiles", [])
                    else:
                        items = value
                    if not isinstance(items, list) or any(not isinstance(item, dict) for item in items):
                        raise ReconcileError(f"{operation} did not return a complete object array")
                    terminal = "single-complete-response"
                counts = [len(items)]
                offsets = []
            expected = list(expectation["expectedIdentities"])
            matched: list[str] = []
            unexpected: list[str] = []
            mapped_seen: set[str] = set()
            for item in items:
                semantic = mapper(item)
                if semantic is None:
                    semantic = "provider-id-sha256:" + hashlib.sha256(
                        self._provider_identity(item).encode("utf-8")
                    ).hexdigest()
                if semantic in mapped_seen:
                    raise ReconcileError(f"{operation} maps two provider objects to one identity")
                mapped_seen.add(semantic)
                (matched if semantic in expected else unexpected).append(semantic)
            matched.sort()
            unexpected.sort()
            policy = expectation["comparisonPolicy"]
            complete = matched == expected and (policy != "exact-desired-set" or not unexpected)
            row = {
                "operationId": operation,
                "bindingDigest": sha256_ref(document),
                "comparisonPolicy": policy,
                "expectedIdentityCount": len(expected),
                "expectedIdentitySetDigest": sha256_ref(expected),
                "responseCardinality": cardinality,
                "requestCount": len(counts),
                "responseItemCounts": counts,
                "firstOffsets": offsets,
                "matchedExpectedIdentityCount": len(matched),
                "matchedExpectedIdentitySetDigest": sha256_ref(matched),
                "unexpectedIdentityCount": len(unexpected),
                "unexpectedIdentitySetDigest": sha256_ref(unexpected),
                "terminalReason": terminal,
                "complete": complete,
            }
            if not complete:
                raise ReconcileError(f"complete read-back mismatch for {operation}:{row['bindingDigest']}")
            coverage.append(row)
        if len(coverage) != len(expectations):
            raise ReconcileError("read-back coverage is partial")
        return sorted(coverage, key=lambda row: (str(row["operationId"]), str(row["bindingDigest"])))


def query_coverage_digest(rows: list[dict[str, object]]) -> str:
    return sha256_ref(rows)
