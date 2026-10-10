"""Provision a real foreign Keycloak organization in one disposable E2E realm.

The one-shot migration administrator already exists at this point. Its secret
is never copied into the fixture, and the migration removes that administrator
before the application tier starts. No production render or import is changed.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from compose_env import ContractError


ALIAS = "weave-foreign-e2e"
IDENTIFIER = re.compile(r"[0-9a-fA-F-]{36}")


def _request(base: str, method: str, path: str, *, token: str | None = None,
             payload: object | None = None, form: dict[str, str] | None = None,
             expected: int = 200) -> tuple[object | None, str]:
    if not path.startswith("/") or path.startswith("//"):
        raise ContractError("foreign organization fixture path is invalid")
    body = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload, separators=(",", ":")).encode()
        headers["Content-Type"] = "application/json"
    if form is not None:
        body = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base + path, data=body, headers=headers,
                                     method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            if response.status != expected:
                raise ContractError(
                    f"foreign organization fixture {method} failed with HTTP {response.status}"
                )
            data = response.read(65537)
            if len(data) > 65536:
                raise ContractError("foreign organization fixture response exceeded its bound")
            return (json.loads(data) if data else None, response.headers.get("Location", ""))
    except urllib.error.HTTPError as error:
        # Keycloak responses may include account or credential details. Report
        # only the status, never the body or request headers.
        raise ContractError(
            f"foreign organization fixture {method} failed with HTTP {error.code}"
        ) from None
    except urllib.error.URLError as error:
        raise ContractError("foreign organization fixture transport unavailable") from None


def _location_id(location: str) -> str:
    value = urllib.parse.urlparse(location).path.rstrip("/").rsplit("/", 1)[-1]
    if not IDENTIFIER.fullmatch(value):
        raise ContractError("foreign organization fixture omitted its identifier")
    return value


def provision(context, migration_secret: Path) -> Path:
    if context.environment != "e2e" or context.isolated_namespace is None:
        raise ContractError("foreign organization fixture requires isolated E2E")
    if migration_secret.is_symlink() or not migration_secret.is_file():
        raise ContractError("foreign organization fixture has no one-shot administrator")
    secret = migration_secret.read_text(encoding="ascii").strip()
    if len(secret) < 32:
        raise ContractError("foreign organization fixture administrator is invalid")
    base = "http://127.0.0.1:" + context.env["WEAVE_KEYCLOAK_HOST_PORT"]
    token_response, _ = _request(
        base, "POST", "/realms/master/protocol/openid-connect/token",
        form={"grant_type": "client_credentials",
              "client_id": "weave-realm-migration-bootstrap",
              "client_secret": secret},
    )
    if not isinstance(token_response, dict):
        raise ContractError("foreign organization fixture administrator token missing")
    token = token_response.get("access_token")
    if not isinstance(token, str) or not token:
        raise ContractError("foreign organization fixture administrator token missing")

    admin = "/admin/realms/weave"
    _, organization_location = _request(
        base, "POST", admin + "/organizations", token=token,
        payload={"name": "Weave Foreign E2E", "alias": ALIAS, "enabled": True},
        expected=201,
    )
    organization_id = _location_id(organization_location)
    suffix = hashlib.sha256(context.env["WEAVE_E2E_RUN_ID"].encode()).hexdigest()[:20]
    email = f"weave-foreign-{suffix}@example.invalid"
    password = secrets.token_urlsafe(36)
    _, user_location = _request(
        base, "POST", admin + "/users", token=token,
        payload={"username": email.split("@", 1)[0], "email": email,
                 "enabled": True, "emailVerified": True,
                 "credentials": [{"type": "password", "value": password,
                                  "temporary": False}]},
        expected=201,
    )
    user_id = _location_id(user_location)
    _request(base, "POST", admin + f"/organizations/{organization_id}/members",
             token=token, payload=user_id, expected=201)
    _, group_location = _request(
        base, "POST", admin + f"/organizations/{organization_id}/groups",
        token=token, payload={"name": "owners"}, expected=201,
    )
    group_id = _location_id(group_location)
    clients, _ = _request(base, "GET", admin + "/clients?clientId=weave-app",
                          token=token)
    if not isinstance(clients, list) or len(clients) != 1 or not isinstance(clients[0], dict):
        raise ContractError("foreign organization fixture Weave client is ambiguous")
    client_id = clients[0].get("id")
    if not isinstance(client_id, str) or not IDENTIFIER.fullmatch(client_id):
        raise ContractError("foreign organization fixture Weave client is invalid")
    role, _ = _request(base, "GET", admin + f"/clients/{client_id}/roles/owner",
                       token=token)
    if not isinstance(role, dict) or role.get("name") != "owner":
        raise ContractError("foreign organization fixture owner role missing")
    _request(
        base, "POST",
        admin + f"/organizations/{organization_id}/groups/{group_id}"
        + f"/role-mappings/clients/{client_id}",
        token=token, payload=[role], expected=204,
    )
    _request(
        base, "PUT",
        admin + f"/organizations/{organization_id}/groups/{group_id}/members/{user_id}",
        token=token, expected=204,
    )

    target = context.secret_root / "foreign-organization-e2e-identity.json"
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump({"schemaVersion": "weave.e2e-foreign-organization-fixture/v1",
                   "organizationAlias": ALIAS, "organizationId": organization_id,
                   "email": email, "password": password,
                   "candidateCommit": context.env["WEAVE_CANDIDATE_COMMIT"],
                   "composeProject": context.env["WEAVE_COMPOSE_PROJECT"]}, output)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    print("WEAVE_FOREIGN_ORGANIZATION_FIXTURE_RESULT status=passed supportSafe=true")
    return target
