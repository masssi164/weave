#!/usr/bin/env python3
"""Run the live, support-safe Keycloak DCR policy and RAT lifecycle proof."""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import stat
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


SCRIPT_ROOT = Path(__file__).resolve().parent
KEYCLOAK_ROOT = SCRIPT_ROOT.parent / "keycloak"
sys.path.insert(0, str(SCRIPT_ROOT))
sys.path.insert(0, str(KEYCLOAK_ROOT))

import identity_ops  # noqa: E402
import init_secrets  # noqa: E402


APPROVED_SCOPES = (
    "agent-runtime.profile.read",
    "mcp.tools",
    "files.read",
)
PRIVATE_JWK_FIELDS = ("kty", "use", "alg", "kid", "n", "e")


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


def exchange(
    endpoint: str,
    method: str,
    bearer: str,
    body: dict[str, Any] | None = None,
) -> tuple[int, dict[str, Any]]:
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
            raw = response.read(64 * 1024 + 1)
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read(64 * 1024 + 1)
        status = error.code
    except urllib.error.URLError as error:
        raise ContractError("Keycloak DCR endpoint is unavailable") from error
    if len(raw) > 64 * 1024:
        raise ContractError("Keycloak DCR response exceeded the bounded size")
    if not raw:
        return status, {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ContractError("Keycloak DCR response is malformed; response withheld") from error
    if not isinstance(value, dict):
        raise ContractError("Keycloak DCR response is malformed; response withheld")
    return status, value


def rejected(
    endpoint: str,
    access_token: str,
    name: str,
    request: dict[str, Any],
) -> str:
    status, _ = exchange(endpoint, "POST", access_token, request)
    if status not in {400, 403}:
        raise ContractError(f"negative DCR case was not rejected [case={name}]")
    return name


def registration(
    endpoint: str,
    issuer: str,
    access_token: str,
    client_id: str,
    private_jwk: dict[str, Any],
) -> tuple[str, str]:
    status, response = exchange(
        endpoint,
        "POST",
        access_token,
        metadata(client_id, private_jwk),
    )
    expected_uri = (
        f"{issuer}/clients-registrations/openid-connect/{client_id}"
    )
    if (
        status not in {200, 201}
        or response.get("client_id") != client_id
        or response.get("registration_client_uri") != expected_uri
        or response.get("token_endpoint_auth_method") != "private_key_jwt"
        or set(str(response.get("scope", "")).split()) != set(APPROVED_SCOPES)
        or response.get("grant_types") != ["client_credentials"]
        or response.get("redirect_uris") != []
        or not isinstance(response.get("registration_access_token"), str)
        or not str(response["registration_access_token"]).strip()
        or not expected_uri.startswith(issuer + "/clients-registrations/openid-connect/")
    ):
        raise ContractError("valid DCR response did not preserve the exact workload contract")
    return expected_uri, str(response["registration_access_token"])


def workload_token(
    base: str,
    realm: str,
    issuer: str,
    client_id: str,
    private_jwk: dict[str, Any],
) -> None:
    status, response = identity_ops.private_key_jwt_token_response(
        base,
        realm,
        client_id,
        private_jwk,
        issuer,
    )
    access_token = response.get("access_token")
    if (
        status != 200
        or not isinstance(access_token, str)
        or identity_ops.access_token_client_roles(
            access_token, "realm-management"
        )
        or access_token_realm_roles(access_token) != {"weaver-runtime"}
    ):
        raise ContractError("workload effective-role projection is not exact")


def access_token_realm_roles(access_token: str) -> set[str]:
    try:
        segments = access_token.split(".")
        if len(segments) != 3:
            return set()
        padding = "=" * ((4 - len(segments[1]) % 4) % 4)
        claims = json.loads(
            base64.urlsafe_b64decode(segments[1] + padding).decode("utf-8")
        )
        realm_access = claims.get("realm_access")
        roles = realm_access.get("roles") if isinstance(realm_access, dict) else None
        if not isinstance(roles, list) or any(
            not isinstance(role, str) for role in roles
        ):
            return set()
        return set(roles)
    except (binascii.Error, UnicodeDecodeError, ValueError, json.JSONDecodeError):
        return set()


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
    status, token_response = identity_ops.private_key_jwt_token_response(
        base,
        args.realm,
        "weave-agent-runtime-admin",
        admin_jwk,
        issuer,
    )
    admin_token = token_response.get("access_token")
    if (
        status != 200
        or not isinstance(admin_token, str)
        or identity_ops.access_token_client_roles(
            admin_token, "realm-management"
        )
        != {"create-client"}
    ):
        raise ContractError("runtime administration authority is not exact")
    if (
        identity_ops.administration_read_probe_status(
            base, args.realm, "clients", admin_token
        )
        not in {401, 403}
    ):
        raise ContractError("runtime administration authority accepted broad Admin REST")

    probe_suffix = args.run_id.replace("-", "_")
    client_a = f"weaver-cell-dcr_{probe_suffix}_a"
    client_b = f"weaver-cell-dcr_{probe_suffix}_b"
    key_a = generated_jwk(f"{probe_suffix}-a-current")
    key_a_next = generated_jwk(f"{probe_suffix}-a-next")
    key_b = generated_jwk(f"{probe_suffix}-b-current")
    rejected_cases: list[str] = []
    authority_a: tuple[str, str] | None = None
    authority_b: tuple[str, str] | None = None
    cleanup_complete = False
    try:
        invalid_namespace = metadata("weaver-cell-invalid!", key_a)
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "invalid-namespace", invalid_namespace)
        )
        wrong_auth = metadata(f"weaver-cell-dcr_{probe_suffix}_wrong_auth", key_a)
        wrong_auth["token_endpoint_auth_method"] = "client_secret_basic"
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "wrong-auth-method", wrong_auth)
        )
        human_flow = metadata(f"weaver-cell-dcr_{probe_suffix}_human", key_a)
        human_flow["grant_types"] = ["authorization_code"]
        human_flow["response_types"] = ["code"]
        human_flow["redirect_uris"] = ["https://forbidden.invalid/callback"]
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "human-login-flow", human_flow)
        )
        wrong_scope = metadata(f"weaver-cell-dcr_{probe_suffix}_scope", key_a)
        wrong_scope["scope"] = " ".join(APPROVED_SCOPES) + " realm-management"
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "unapproved-scope", wrong_scope)
        )
        provider_url = metadata(f"weaver-cell-dcr_{probe_suffix}_url", key_a)
        provider_url["client_uri"] = "https://forbidden.invalid"
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "provider-url", provider_url)
        )
        custom_mapper = metadata(f"weaver-cell-dcr_{probe_suffix}_mapper", key_a)
        custom_mapper["protocol_mappers"] = [{"name": "forbidden"}]
        rejected_cases.append(
            rejected(direct_endpoint, admin_token, "protocol-mapper", custom_mapper)
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
            )
        )

        public_a, rat_a = registration(
            direct_endpoint, issuer, admin_token, client_a, key_a
        )
        authority_a = (public_a, rat_a)
        public_b, rat_b = registration(
            direct_endpoint, issuer, admin_token, client_b, key_b
        )
        authority_b = (public_b, rat_b)

        status, observed = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rat_a
        )
        observed_rat = observed.get("registration_access_token")
        if (
            status != 200
            or observed.get("client_id") != client_a
            or observed.get("token_endpoint_auth_method") != "private_key_jwt"
            or set(str(observed.get("scope", "")).split()) != set(APPROVED_SCOPES)
            or not isinstance(observed_rat, str)
            or not observed_rat
        ):
            raise ContractError("owning Cell RAT could not retrieve the exact client state")
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

        status, update_response = exchange(
            f"{direct_endpoint}/{client_a}",
            "PUT",
            rat_a,
            metadata(client_a, key_a_next, update=True),
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
        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "GET", rat_a
        )
        if status not in {401, 403}:
            raise ContractError("stale RAT remained valid after rotation")
        workload_token(base, args.realm, issuer, client_a, key_a_next)

        status, _ = exchange(
            f"{direct_endpoint}/{client_a}", "DELETE", rotated_rat
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
        cleanup_complete = True
    finally:
        for client_id, authority in (
            (client_a, authority_a),
            (client_b, authority_b),
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
            "validRegistration": True,
            "privateKeyJwt": True,
            "effectiveWorkloadRoles": ["weaver-runtime"],
            "registrationAccessTokenRotation": True,
            "staleRegistrationAccessTokenRejected": True,
            "crossCellRegistrationAccessTokenRejected": True,
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
