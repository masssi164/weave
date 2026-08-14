#!/usr/bin/env python3
"""Run the live, support-safe Keycloak DCR policy and RAT lifecycle proof."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


SCRIPT_ROOT = Path(__file__).resolve().parent
KEYCLOAK_ROOT = SCRIPT_ROOT.parent / "keycloak"
sys.path.insert(0, str(SCRIPT_ROOT))
sys.path.insert(0, str(KEYCLOAK_ROOT))

import oauth_probe  # noqa: E402
import init_secrets  # noqa: E402


APPROVED_SCOPES = (
    "agent-runtime.profile.read",
    "mcp.tools",
    "files.read",
)
PRIVATE_JWK_FIELDS = ("kty", "use", "alg", "kid", "n", "e")
FORBIDDEN_METADATA_FIELDS = (
    "client_secret",
    "client_secret_expires_at",
    "jwks_uri",
    "sector_identifier_uri",
    "software_id",
    "software_version",
    "software_statement",
    "client_uri",
    "logo_uri",
    "policy_uri",
    "tos_uri",
    "initiate_login_uri",
    "root_url",
    "base_url",
    "admin_url",
    "provider_url",
    "web_origins",
    "request_uris",
    "protocol_mappers",
    "protocolMappers",
    "attributes",
)


class ContractError(RuntimeError):
    pass


def private_json(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise ContractError("runtime administration SecretRef is not a regular file")
    if stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise ContractError("runtime administration SecretRef mode is not 0600")
    try:
        value = json.loads(path.read_bytes())
    except (OSError, json.JSONDecodeError) as error:
        raise ContractError("runtime administration SecretRef is malformed") from error
    if not isinstance(value, dict):
        raise ContractError("runtime administration SecretRef is malformed")
    return value


def generated_jwk(key_id: str) -> dict[str, Any]:
    try:
        value = json.loads(init_secrets.generate_rsa_jwk(key_id))
    except (ValueError, init_secrets.ContractError) as error:
        raise ContractError("ephemeral workload key generation failed") from error
    if not isinstance(value, dict):
        raise ContractError("ephemeral workload key generation failed")
    return value


def public_jwks(private_jwk: dict[str, Any]) -> dict[str, Any]:
    key = {field: private_jwk.get(field) for field in PRIVATE_JWK_FIELDS}
    if (
        key.get("kty") != "RSA"
        or key.get("use") != "sig"
        or key.get("alg") != "PS256"
        or any(not isinstance(key.get(field), str) for field in PRIVATE_JWK_FIELDS)
    ):
        raise ContractError("ephemeral workload public key is malformed")
    return {"keys": [key]}


def metadata(
    client_id: str,
    private_jwk: dict[str, Any],
    *,
    update: bool = False,
) -> dict[str, Any]:
    value: dict[str, Any] = {
        "client_name": client_id,
        "token_endpoint_auth_method": "private_key_jwt",
        "token_endpoint_auth_signing_alg": "PS256",
        "subject_type": "public",
        "backchannel_logout_session_required": False,
        "backchannel_logout_revoke_offline_tokens": False,
        "frontchannel_logout_session_required": False,
        "scope": " ".join(APPROVED_SCOPES),
        "redirect_uris": [],
        "grant_types": ["client_credentials"],
        "response_types": [],
        "jwks": public_jwks(private_jwk),
    }
    if update:
        value["client_id"] = client_id
    return value


def registration_state_digest(
    client_id: str,
    realm: str,
    private_jwk: dict[str, Any],
    operation: str,
) -> str:
    state = {
        "clientId": client_id,
        "defaultClientScopes": ["weaver-runtime-workload"],
        "effectiveRoles": [
            {
                "containerId": realm,
                "kind": "realm",
                "name": "weaver-runtime",
            }
        ],
        "fixedAttributes": {
            "access.token.header.type.rfc9068": "true",
            "access.token.lifespan": "59",
            "backchannel.logout.revoke.offline.tokens": "false",
            "backchannel.logout.session.required": "false",
            "frontchannel.logout.session.required": "false",
            "token.endpoint.auth.signing.alg": "PS256",
            "use.jwks.string": "true",
            "use.jwks.url": "false",
            "use.refresh.tokens": "false",
        },
        "flows": {
            "authorizationCode": False,
            "ciba": False,
            "device": False,
            "directAccessGrant": False,
            "implicit": False,
            "jwtAuthorizationGrant": False,
            "serviceAccounts": True,
            "standardTokenExchange": False,
            "uma": False,
        },
        "operation": operation,
        "optionalClientScopes": sorted(APPROVED_SCOPES),
        "protocolMappers": [],
        "publicJwks": public_jwks(private_jwk),
        "tokenEndpointAuthentication": {
            "algorithm": "PS256",
            "method": "client-jwt",
        },
        "uris": [],
        "webOrigins": [],
    }
    encoded = json.dumps(
        state, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(encoded).hexdigest()


def registration_handoff_headers(
    client_id: str,
    realm: str,
    private_jwk: dict[str, Any],
    operation: str,
) -> dict[str, str]:
    capability = base64.urlsafe_b64encode(os.urandom(32)).rstrip(b"=").decode(
        "ascii"
    )
    return {
        "Weave-Registration-Handoff": capability,
        "Weave-Registration-Handoff-State": registration_state_digest(
            client_id, realm, private_jwk, operation
        ),
        "Weave-Registration-Handoff-Operation": operation,
    }


def exchange_details(
    endpoint: str,
    method: str,
    bearer: str,
    body: dict[str, Any] | None = None,
    request_headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any], dict[str, str]]:
    payload = None
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {bearer}",
    }
    if body is not None:
        payload = json.dumps(body, separators=(",", ":"), sort_keys=True).encode(
            "utf-8"
        )
        headers["Content-Type"] = "application/json"
    if request_headers is not None:
        if any(
            not isinstance(name, str)
            or not isinstance(value, str)
            or name in headers
            for name, value in request_headers.items()
        ):
            raise ContractError("additional DCR request headers are invalid")
        headers.update(request_headers)
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read(64 * 1024 + 1)
            status = response.status
            response_headers = {
                name.casefold(): value for name, value in response.headers.items()
            }
    except urllib.error.HTTPError as error:
        raw = error.read(64 * 1024 + 1)
        status = error.code
        response_headers = {
            name.casefold(): value
            for name, value in (error.headers or {}).items()
        }
    except urllib.error.URLError as error:
        raise ContractError("Keycloak DCR endpoint is unavailable") from error
    if len(raw) > 64 * 1024:
        raise ContractError("Keycloak DCR response exceeded the bounded size")
    if not raw:
        return status, {}, response_headers
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ContractError("Keycloak DCR response is malformed; response withheld") from error
    if not isinstance(value, dict):
        raise ContractError("Keycloak DCR response is malformed; response withheld")
    return status, value, response_headers


def exchange(
    endpoint: str,
    method: str,
    bearer: str,
    body: dict[str, Any] | None = None,
    request_headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any]]:
    status, response, _ = exchange_details(
        endpoint, method, bearer, body, request_headers
    )
    return status, response


def handoff_exchange(
    endpoint: str,
    bearer: str,
    headers: dict[str, str],
) -> tuple[int, dict[str, Any]]:
    status, response, response_headers = exchange_details(
        endpoint,
        "POST",
        bearer,
        request_headers=headers,
    )
    cache_control = response_headers.get("cache-control", "")
    if (
        "no-store" not in {
            directive.strip().casefold()
            for directive in cache_control.split(",")
        }
        or response_headers.get("pragma", "").casefold() != "no-cache"
    ):
        raise ContractError(
            "registration handoff response is not explicitly non-cacheable"
        )
    return status, response


def recovered_handoff_authority(
    status: int,
    response: dict[str, Any],
    client_id: str,
    expected_uri: str,
    expected_state_digest: str,
    previous_authority: str,
    operation: str,
) -> str:
    violations: list[str] = []
    if status != 200:
        violations.append(f"status-{status}")
    protocol_error = response.get("error")
    if isinstance(protocol_error, str) and re.fullmatch(
        r"[a-z][a-z0-9_]{0,63}", protocol_error
    ):
        violations.append("protocol-" + protocol_error.replace("_", "-"))
    if response.get("client_id") != client_id:
        violations.append("client")
    if response.get("registration_client_uri") != expected_uri:
        violations.append("uri")
    if response.get("state_digest") != expected_state_digest:
        violations.append("state")
    subject_digest = response.get("subject_digest")
    if not isinstance(subject_digest, str) or not re.fullmatch(
        r"sha256:[0-9a-f]{64}", subject_digest
    ):
        violations.append("subject")
    authority = response.get("registration_access_token")
    if not isinstance(authority, str) or not authority:
        violations.append("authority-absent")
    elif authority == previous_authority:
        violations.append("authority-not-rotated")
    if violations:
        raise ContractError(
            "registration handoff recovery violated the exact contract "
            f"[operation={operation},constraints={','.join(violations)}]"
        )
    return authority


def exchange_status(
    endpoint: str,
    method: str,
    bearer: str,
    body: dict[str, Any] | None = None,
) -> int:
    payload = None
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {bearer}",
    }
    if body is not None:
        payload = json.dumps(body, separators=(",", ":"), sort_keys=True).encode(
            "utf-8"
        )
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            response.read(64 * 1024 + 1)
            return response.status
    except urllib.error.HTTPError as error:
        error.read(64 * 1024 + 1)
        return error.code
    except urllib.error.URLError as error:
        raise ContractError("Keycloak Admin REST endpoint is unavailable") from error


def rejected(
    endpoint: str,
    access_token: str,
    name: str,
    request: dict[str, Any],
    request_headers: dict[str, str],
) -> str:
    status, _ = exchange(
        endpoint, "POST", access_token, request, request_headers
    )
    if status not in {400, 403}:
        raise ContractError(f"negative DCR case was not rejected [case={name}]")
    return name


def rejected_status(status: int, name: str) -> str:
    if status not in {400, 401, 403}:
        raise ContractError(f"negative DCR case was not rejected [case={name}]")
    return name


def require_internal_spi_warning_absent(container_id: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{12,64}", container_id):
        raise ContractError("Keycloak runtime container identity is invalid")
    process = subprocess.Popen(
        ["docker", "logs", container_id],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    assert process.stdout is not None
    warning_seen = False
    for line in process.stdout:
        if "KC-SERVICES0047" in line:
            warning_seen = True
    return_code = process.wait(timeout=20)
    if return_code != 0:
        raise ContractError("Keycloak runtime warning scan failed")
    if warning_seen:
        raise ContractError("Keycloak runtime loaded a forbidden internal-SPI provider")


def registration(
    endpoint: str,
    issuer: str,
    realm: str,
    access_token: str,
    client_id: str,
    private_jwk: dict[str, Any],
) -> tuple[str, str]:
    handoff_headers = registration_handoff_headers(
        client_id, realm, private_jwk, "create"
    )
    status, response = exchange(
        endpoint,
        "POST",
        access_token,
        metadata(client_id, private_jwk),
        handoff_headers,
    )
    expected_uri = (
        f"{issuer}/clients-registrations/openid-connect/{client_id}"
    )
    violations: list[str] = []
    if status not in {200, 201}:
        violations.append(f"status-{status}")
    observed_uri = response.get("registration_client_uri")
    observed_client_id = response.get("client_id")
    if observed_client_id != client_id:
        violations.append("client-id")
    if observed_uri != expected_uri:
        violations.append("registration-uri")
        violations.extend(
            registration_uri_mismatch_constraints(
                expected_uri, observed_uri, client_id
            )
        )
        if (
            isinstance(observed_client_id, str)
            and isinstance(observed_uri, str)
            and urllib.parse.unquote(
                urllib.parse.urlsplit(observed_uri).path.rpartition("/")[2]
            )
            == observed_client_id
        ):
            violations.append("registration-uri-matches-response-client")
    if not expected_uri.startswith(
        issuer + "/clients-registrations/openid-connect/"
    ):
        violations.append("expected-uri-coordinate")
    if violations:
        raise ContractError(
            "valid DCR response did not preserve the exact workload contract "
            f"[constraints={','.join(violations)}]"
        )
    initial_rat = exact_client_state(response, client_id, private_jwk)
    recover_endpoint = (
        f"{endpoint}/{client_id}/weave-registration-handoff/recover"
    )
    status, recovered = handoff_exchange(
        recover_endpoint, access_token, handoff_headers
    )
    recovered_rat = recovered_handoff_authority(
        status,
        recovered,
        client_id,
        expected_uri,
        handoff_headers["Weave-Registration-Handoff-State"],
        initial_rat,
        "create",
    )
    status, _ = exchange(
        f"{endpoint}/{client_id}", "GET", initial_rat
    )
    if status not in {401, 403}:
        raise ContractError(
            "pre-recovery Registration Access Token remained valid"
        )
    finalize_endpoint = (
        f"{endpoint}/{client_id}/weave-registration-handoff/finalize"
    )
    status, finalized = handoff_exchange(
        finalize_endpoint, recovered_rat, handoff_headers
    )
    if status != 204 or finalized:
        raise ContractError("registration handoff finalize did not complete")
    status, _ = handoff_exchange(
        recover_endpoint, access_token, handoff_headers
    )
    if status not in {403, 404}:
        raise ContractError("finalized registration handoff remained recoverable")
    status, observed = exchange(
        f"{endpoint}/{client_id}", "GET", recovered_rat
    )
    if status != 200:
        raise ContractError(
            "recovered Registration Access Token cannot read finalized state"
        )
    current_rat = exact_client_state(observed, client_id, private_jwk)
    return expected_uri, current_rat


def registration_uri_mismatch_constraints(
    expected: str, observed: Any, client_id: str
) -> list[str]:
    if not isinstance(observed, str):
        return ["registration-uri-type"]
    try:
        expected_uri = urllib.parse.urlsplit(expected)
        observed_uri = urllib.parse.urlsplit(observed)
        expected_port = expected_uri.port
        observed_port = observed_uri.port
    except ValueError:
        return ["registration-uri-syntax"]

    constraints: list[str] = []
    if observed_uri.scheme != expected_uri.scheme:
        constraints.append("registration-uri-scheme")
    if observed_uri.hostname != expected_uri.hostname:
        constraints.append("registration-uri-host")
    if observed_port != expected_port:
        constraints.append("registration-uri-port")
    expected_prefix, _, expected_tail = expected_uri.path.rpartition("/")
    observed_prefix, _, observed_tail = observed_uri.path.rpartition("/")
    if observed_prefix != expected_prefix:
        constraints.append("registration-uri-path")
    if urllib.parse.unquote(observed_tail) != client_id or expected_tail != client_id:
        constraints.append("registration-uri-client")
    if observed_uri.query or observed_uri.fragment:
        constraints.append("registration-uri-suffix")
    return constraints or ["registration-uri-bytes"]


def exact_client_state(
    response: dict[str, Any],
    client_id: str,
    private_jwk: dict[str, Any],
) -> str:
    rat = response.get("registration_access_token")
    violations: list[str] = []
    expected_values = (
        ("client-id", response.get("client_id"), client_id),
        ("client-name", response.get("client_name"), client_id),
        (
            "authentication-method",
            response.get("token_endpoint_auth_method"),
            "private_key_jwt",
        ),
        (
            "authentication-algorithm",
            response.get("token_endpoint_auth_signing_alg"),
            "PS256",
        ),
        ("subject-type", response.get("subject_type"), "public"),
        ("grant-types", response.get("grant_types"), ["client_credentials"]),
        ("redirect-uris", response.get("redirect_uris"), []),
        ("response-types", response.get("response_types"), []),
        ("public-jwks", response.get("jwks"), public_jwks(private_jwk)),
    )
    violations.extend(
        name for name, observed, expected in expected_values if observed != expected
    )
    observed_scope = response.get("scope")
    if not isinstance(observed_scope, str):
        violations.append("scopes-type")
    else:
        observed_scopes = set(observed_scope.split())
        approved_scopes = set(APPROVED_SCOPES)
        if approved_scopes - observed_scopes:
            violations.append("scopes-missing")
        if observed_scopes - approved_scopes:
            violations.append("scopes-unapproved")
    violations.extend(
        "forbidden-" + field.replace("_", "-")
        for field in FORBIDDEN_METADATA_FIELDS
        if response.get(field) not in (None, "", [], {})
    )
    if not isinstance(rat, str) or not rat:
        violations.append("registration-authority")
    if violations:
        raise ContractError(
            "Keycloak client state did not preserve the exact workload contract "
            f"[constraints={','.join(violations)}]"
        )
    return rat


def workload_token(
    base: str,
    realm: str,
    issuer: str,
    client_id: str,
    private_jwk: dict[str, Any],
) -> None:
    status, response = oauth_probe.private_key_jwt_token_response(
        base,
        realm,
        client_id,
        private_jwk,
        issuer,
    )
    access_token = response.get("access_token")
    if status != 200 or not isinstance(access_token, str):
        raise ContractError("workload effective-role projection is not exact")
    try:
        realm_roles, client_roles = oauth_probe.access_token_role_projection(
            access_token
        )
    except oauth_probe.OAuthProbeError as error:
        raise ContractError(
            "workload effective-role projection is malformed"
        ) from error
    if realm_roles != {"weaver-runtime"} or client_roles:
        raise ContractError("workload effective-role projection is not exact")


def atomic_evidence(path: Path, value: dict[str, Any]) -> None:
    if path.parent.is_symlink() or not path.parent.is_dir():
        raise ContractError("DCR evidence parent is unavailable")
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def run(args: argparse.Namespace) -> None:
    base = args.keycloak_base.rstrip("/")
    issuer = args.issuer.rstrip("/")
    expected_issuer = f"{issuer.rsplit('/realms/', 1)[0]}/realms/{args.realm}"
    if issuer != expected_issuer or not issuer.startswith("https://"):
        raise ContractError("DCR issuer is not the exact public realm issuer")
    direct_endpoint = (
        f"{base}/realms/{args.realm}/clients-registrations/openid-connect"
    )
    admin_jwk = private_json(args.runtime_admin_jwk)
    status, token_response = oauth_probe.private_key_jwt_token_response(
        base,
        args.realm,
        "weave-agent-runtime-admin",
        admin_jwk,
        issuer,
    )
    admin_token = token_response.get("access_token")
    if status != 200:
        raise ContractError(
            f"runtime administration private-key authentication was rejected with HTTP {status}"
        )
    if not isinstance(admin_token, str):
        raise ContractError("runtime administration token response is malformed")
    try:
        admin_realm_roles, admin_client_roles = (
            oauth_probe.access_token_role_projection(admin_token)
        )
    except oauth_probe.OAuthProbeError as error:
        raise ContractError(
            "runtime administration token role projection is malformed"
        ) from error
    if (
        admin_realm_roles
        or admin_client_roles
        != {"realm-management": {"create-client"}}
    ):
        realm_projection = ",".join(sorted(admin_realm_roles)) or "none"
        client_projection = ",".join(
            f"{client_id}:[{','.join(sorted(roles))}]"
            for client_id, roles in sorted(admin_client_roles.items())
        ) or "none"
        raise ContractError(
            "runtime administration authority is not exact "
            f"(realm={realm_projection}; clients={client_projection})"
        )
    if (
        oauth_probe.administration_read_probe_status(
            base, args.realm, "clients", admin_token
        )
        not in {401, 403}
    ):
        raise ContractError("runtime administration authority accepted broad Admin REST")

    probe_suffix = args.run_id.replace("-", "_")
    client_a = f"weaver-cell-dcr_{probe_suffix}_a"
    client_b = f"weaver-cell-dcr_{probe_suffix}_b"
    rollback_client = f"weaver-cell-dcr_{probe_suffix}_rollback"
    key_a = generated_jwk(f"{probe_suffix}-a-current")
    key_a_next = generated_jwk(f"{probe_suffix}-a-next")
    key_b = generated_jwk(f"{probe_suffix}-b-current")
    rollback_key = generated_jwk(f"{probe_suffix}-rollback")
    rejected_cases: list[str] = []
    authority_a: tuple[str, str] | None = None
    authority_b: tuple[str, str] | None = None
    rollback_authority: tuple[str, str] | None = None
    failed_create_rollback_verified = False
    failed_update_rollback_verified = False
    cross_cell_update_rejected = False
    cross_cell_handoff_rejected = False
    direct_admin_rest_creation_rejected = False
    cleanup_complete = False
    try:
        invalid_namespace = metadata("weaver-cell-invalid!", key_a)
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "invalid-namespace",
                invalid_namespace,
                registration_handoff_headers(
                    str(invalid_namespace["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        outside_namespace = metadata(f"external-workload-{probe_suffix}", key_a)
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "out-of-namespace",
                outside_namespace,
                registration_handoff_headers(
                    str(outside_namespace["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        wrong_auth = metadata(f"weaver-cell-dcr_{probe_suffix}_wrong_auth", key_a)
        wrong_auth["token_endpoint_auth_method"] = "client_secret_basic"
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "wrong-auth-method",
                wrong_auth,
                registration_handoff_headers(
                    str(wrong_auth["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        human_flow = metadata(f"weaver-cell-dcr_{probe_suffix}_human", key_a)
        human_flow["grant_types"] = ["authorization_code"]
        human_flow["response_types"] = ["code"]
        human_flow["redirect_uris"] = ["https://forbidden.invalid/callback"]
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "human-login-flow",
                human_flow,
                registration_handoff_headers(
                    str(human_flow["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        web_origin = metadata(rollback_client, rollback_key)
        web_origin["web_origins"] = ["https://forbidden.invalid"]
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "web-origin",
                web_origin,
                registration_handoff_headers(
                    str(web_origin["client_name"]),
                    args.realm,
                    rollback_key,
                    "create",
                ),
            )
        )
        wrong_scope = metadata(f"weaver-cell-dcr_{probe_suffix}_scope", key_a)
        wrong_scope["scope"] = " ".join(APPROVED_SCOPES) + " realm-management"
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "unapproved-scope",
                wrong_scope,
                registration_handoff_headers(
                    str(wrong_scope["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        provider_url = metadata(f"weaver-cell-dcr_{probe_suffix}_url", key_a)
        provider_url["client_uri"] = "https://forbidden.invalid"
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "provider-url",
                provider_url,
                registration_handoff_headers(
                    str(provider_url["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        custom_mapper = metadata(f"weaver-cell-dcr_{probe_suffix}_mapper", key_a)
        custom_mapper["protocol_mappers"] = [{"name": "forbidden"}]
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "protocol-mapper",
                custom_mapper,
                registration_handoff_headers(
                    str(custom_mapper["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )
        custom_attribute = metadata(
            f"weaver-cell-dcr_{probe_suffix}_attribute", key_a
        )
        custom_attribute["attributes"] = {"forbidden": "true"}
        rejected_cases.append(
            rejected(
                direct_endpoint,
                admin_token,
                "custom-attribute",
                custom_attribute,
                registration_handoff_headers(
                    str(custom_attribute["client_name"]),
                    args.realm,
                    key_a,
                    "create",
                ),
            )
        )

        admin_rest_client = {
            "clientId": f"weaver-cell-dcr_{probe_suffix}_admin_bypass",
            "name": f"weaver-cell-dcr_{probe_suffix}_admin_bypass",
            "protocol": "openid-connect",
            "publicClient": False,
            "serviceAccountsEnabled": True,
            "standardFlowEnabled": False,
            "implicitFlowEnabled": False,
            "directAccessGrantsEnabled": False,
        }
        status = exchange_status(
            f"{base}/admin/realms/{args.realm}/clients",
            "POST",
            admin_token,
            admin_rest_client,
        )
        rejected_cases.append(rejected_status(status, "direct-admin-rest-bypass"))
        direct_admin_rest_creation_rejected = True

        rollback_public, rollback_rat = registration(
            direct_endpoint,
            issuer,
            args.realm,
            admin_token,
            rollback_client,
            rollback_key,
        )
        rollback_authority = (rollback_public, rollback_rat)
        failed_create_rollback_verified = True
        status, _ = exchange(
            f"{direct_endpoint}/{rollback_client}",
            "DELETE",
            rollback_rat,
        )
        if status not in {200, 204}:
            raise ContractError("failed-create rollback probe cleanup failed")
        rollback_authority = None

        public_a, rat_a = registration(
            direct_endpoint, issuer, args.realm, admin_token, client_a, key_a
        )
        authority_a = (public_a, rat_a)
        public_b, rat_b = registration(
            direct_endpoint, issuer, args.realm, admin_token, client_b, key_b
        )
        authority_b = (public_b, rat_b)

        status, observed = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rat_a
        )
        if status != 200:
            raise ContractError("owning Cell RAT could not retrieve the exact client state")
        observed_rat = exact_client_state(observed, client_a, key_a)
        rat_a = observed_rat
        authority_a = (public_a, rat_a)
        workload_token(base, args.realm, issuer, client_a, key_a)
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rat_b
        )
        if status not in {401, 403}:
            raise ContractError("cross-Cell RAT read was not rejected")
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "DELETE", rat_b
        )
        if status not in {401, 403}:
            raise ContractError("cross-Cell RAT delete was not rejected")
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}",
            "PUT",
            rat_b,
            metadata(client_a, key_a_next, update=True),
            registration_handoff_headers(
                client_a, args.realm, key_a_next, "rotate"
            ),
        )
        rejected_cases.append(rejected_status(status, "cross-cell-update"))
        cross_cell_update_rejected = True

        rejected_update = metadata(client_b, key_b, update=True)
        rejected_update["web_origins"] = ["https://forbidden.invalid"]
        status, _ = exchange(
            f"{direct_endpoint}/{client_b}",
            "PUT",
            rat_b,
            rejected_update,
            registration_handoff_headers(
                client_b, args.realm, key_b, "rotate"
            ),
        )
        rejected_cases.append(rejected_status(status, "failed-update-rollback"))
        status, observed = exchange(
            f"{direct_endpoint}/{client_b}",
            "GET",
            rat_b,
        )
        if status != 200:
            raise ContractError("failed update invalidated the owning Cell RAT")
        rat_b = exact_client_state(observed, client_b, key_b)
        authority_b = (public_b, rat_b)
        failed_update_rollback_verified = True

        update_handoff = registration_handoff_headers(
            client_a, args.realm, key_a_next, "rotate"
        )
        status, update_response = exchange(
            f"{direct_endpoint}/{client_a}",
            "PUT",
            rat_a,
            metadata(client_a, key_a_next, update=True),
            update_handoff,
        )
        rotated_rat = update_response.get("registration_access_token")
        if (
            status != 200
            or not isinstance(rotated_rat, str)
            or not rotated_rat
            or rotated_rat == rat_a
        ):
            raise ContractError("mutating DCR update did not rotate the RAT")
        authority_a = (public_a, rotated_rat)
        recover_endpoint = (
            f"{direct_endpoint}/{client_a}/weave-registration-handoff/recover"
        )
        status, _ = handoff_exchange(
            recover_endpoint,
            admin_token,
            registration_handoff_headers(
                client_b, args.realm, key_b, "rotate"
            ),
        )
        if status not in {403, 404}:
            raise ContractError(
                "another Cell registration handoff proof was accepted"
            )
        cross_cell_handoff_rejected = True
        status, recovered_update = handoff_exchange(
            recover_endpoint, admin_token, update_handoff
        )
        recovered_update_rat = recovered_handoff_authority(
            status,
            recovered_update,
            client_a,
            public_a,
            update_handoff["Weave-Registration-Handoff-State"],
            rotated_rat,
            "rotate",
        )
        authority_a = (public_a, recovered_update_rat)
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rat_a
        )
        if status not in {401, 403}:
            raise ContractError("stale RAT remained valid after rotation")
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rotated_rat
        )
        if status not in {401, 403}:
            raise ContractError("pre-recovery RAT remained valid after recovery")
        finalize_endpoint = (
            f"{direct_endpoint}/{client_a}/weave-registration-handoff/finalize"
        )
        status, finalized = handoff_exchange(
            finalize_endpoint, recovered_update_rat, update_handoff
        )
        if status != 204 or finalized:
            raise ContractError("updated registration handoff did not finalize")
        status, _ = handoff_exchange(
            recover_endpoint, admin_token, update_handoff
        )
        if status not in {403, 404}:
            raise ContractError(
                "finalized update registration handoff remained recoverable"
            )
        status, observed = exchange(
            f"{direct_endpoint}/{client_a}", "GET", recovered_update_rat
        )
        if status != 200:
            raise ContractError("rotated RAT could not retrieve the final client state")
        current_rat = exact_client_state(observed, client_a, key_a_next)
        authority_a = (public_a, current_rat)
        workload_token(base, args.realm, issuer, client_a, key_a_next)

        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "DELETE", current_rat
        )
        if status not in {200, 204}:
            raise ContractError("owning Cell RAT could not delete its client")
        authority_a = None
        status, _ = exchange(
            f"{direct_endpoint}/{client_b}", "DELETE", rat_b
        )
        if status not in {200, 204}:
            raise ContractError("second owning Cell RAT could not delete its client")
        authority_b = None
        require_internal_spi_warning_absent(args.keycloak_container_id)
        cleanup_complete = True
    finally:
        for client_id, authority in (
            (client_a, authority_a),
            (client_b, authority_b),
            (rollback_client, rollback_authority),
        ):
            if authority is not None:
                exchange(f"{direct_endpoint}/{client_id}", "DELETE", authority[1])

    atomic_evidence(
        args.output,
        {
            "schemaVersion": "weave.keycloak-dcr-live-proof/v1",
            "candidateCommit": args.candidate_commit,
            "specificationCommit": args.specification_commit,
            "composeProject": args.compose_project,
            "runtimeAdminRoles": ["create-client"],
            "broadAdminRestRejected": True,
            "directAdminRestCreationRejected": direct_admin_rest_creation_rejected,
            "validRegistration": True,
            "privateKeyJwt": True,
            "effectiveWorkloadRoles": ["weaver-runtime"],
            "registrationAccessTokenRotation": True,
            "postUpdateFinalStateVerified": True,
            "staleRegistrationAccessTokenRejected": True,
            "crossCellRegistrationAccessTokenRejected": True,
            "crossCellUpdateRejected": cross_cell_update_rejected,
            "crossCellHandoffRejected": cross_cell_handoff_rejected,
            "handoffRecoveryAndFinalize": True,
            "handoffResponsesNonCacheable": True,
            "failedCreateRollbackVerified": failed_create_rollback_verified,
            "failedUpdateRollbackVerified": failed_update_rollback_verified,
            "internalSpiWarningAbsent": True,
            "negativeCases": rejected_cases,
            "cleanupComplete": cleanup_complete,
            "credentialsIncluded": False,
            "supportSafe": True,
        },
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keycloak-base", required=True)
    parser.add_argument("--issuer", required=True)
    parser.add_argument("--realm", required=True)
    parser.add_argument("--runtime-admin-jwk", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--candidate-commit", required=True)
    parser.add_argument("--specification-commit", required=True)
    parser.add_argument("--compose-project", required=True)
    parser.add_argument("--keycloak-container-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    try:
        run(parse_args())
    except Exception as error:
        print(
            "WEAVE_DCR_CONTRACT_ERROR "
            + str(error).replace("\n", " ")[:512],
            file=sys.stderr,
        )
        return 2
    print("WEAVE_DCR_CONTRACT_RESULT status=passed supportSafe=true")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
