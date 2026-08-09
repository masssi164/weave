#!/usr/bin/env python3
"""Render deterministic, support-safe Compose configuration from the pinned corpus."""

from __future__ import annotations

import argparse
import copy
import json
import os
import re
import stat
import sys
from pathlib import Path
from urllib.parse import urlsplit

KEYCLOAK_MODULE_ROOT = Path(__file__).resolve().parents[1] / "keycloak"
if str(KEYCLOAK_MODULE_ROOT) not in sys.path:
    sys.path.insert(0, str(KEYCLOAK_MODULE_ROOT))

from realm_renderer import (  # noqa: E402 - module path is repository-local
    MACHINE_KEY_PROJECTIONS,
    RealmProjectionError,
    fresh_start_migration_bundle,
    pretty_json,
    project_realm,
    sha256_digest,
    validate_public_jwks,
)

from compose_env import (
    ComposeContext,
    ContractError,
    assert_revision,
    canonical_json,
    load_context,
    revision,
    specification_context,
)

SECRET_REF_PATHS = {
    "secretref:keycloak/weave-backend-jwk": "keycloak-weave-backend-jwk.json",
    "secretref:keycloak/weave-mcp-server-jwk": "keycloak-weave-mcp-server-jwk.json",
    "secretref:keycloak/weave-identity-admin-jwk": "keycloak-weave-identity-admin-jwk.json",
    "secretref:keycloak/weave-agent-runtime-admin-jwk": "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin",
    "secretref:smtp/password": "smtp-password",
}
REQUIRED_PRIVATE_FILES = (
    "backend-db-password",
    "identity-reference-hmac-key",
    "keycloak-db-password",
    "control-db-password",
)
MATRIX_PRIVATE_FILES = (
    "mas-db-password",
    "synapse-db-password",
    "mas-encryption-secret",
    "mas-matrix-secret",
    "mas-signing-key.pem",
    "synapse-registration-shared-secret",
    "synapse-macaroon-secret-key",
    "synapse-form-secret",
    "matrix-appservice-as-token",
    "matrix-appservice-hs-token",
)
NEXTCLOUD_PRIVATE_FILES = (
    "nextcloud-db-password",
    "nextcloud-admin-password",
    "nextcloud-actor-token",
)
S3_PRIVATE_FILES = (
    "runtime-state-s3-access-key",
    "runtime-state-s3-secret-key",
)
PROVIDER_CONFIGTREE_FILES = frozenset(
    {
        "matrix-as-token",
        "matrix-hs-token",
        "weave.calendar.caldav.backend-token",
        "weave.nextcloud.files.actor-token",
    }
)


def _private_mode(path: Path) -> bool:
    return stat.S_IMODE(path.stat().st_mode) == 0o600


def _read_secret(context: ComposeContext, name: str) -> str:
    path = context.secret_root / name
    if path.is_symlink() or not path.is_file() or not _private_mode(path):
        raise ContractError(f"secret must be a regular mode-0600 file: {path}")
    value = path.read_text(encoding="utf-8").strip()
    if not value:
        raise ContractError(f"secret is empty: {path}")
    return value


def _write(path: Path, payload: str | bytes, *, private: bool, runtime_owner: tuple[int, int] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700 if private else 0o755)
    if path.is_symlink():
        raise ContractError(f"refusing generated symlink target: {path}")
    data = payload.encode("utf-8") if isinstance(payload, str) else payload
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600 if private else 0o644)
    try:
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600 if private else 0o644)
        if private and runtime_owner is not None:
            uid, gid = runtime_owner
            if path.stat().st_uid != uid or path.stat().st_gid != gid:
                try:
                    os.chown(path, uid, gid)
                except PermissionError as error:
                    raise ContractError(
                        f"cannot bind private config {path} to runtime uid/gid {uid}:{gid}"
                    ) from error
    finally:
        if temporary.exists():
            temporary.unlink()


def _runtime_directory(path: Path, runtime_owner: tuple[int, int]) -> None:
    if path.is_symlink():
        raise ContractError(f"refusing generated symlink directory: {path}")
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path, 0o700)
    uid, gid = runtime_owner
    if path.stat().st_uid != uid or path.stat().st_gid != gid:
        try:
            os.chown(path, uid, gid)
        except PermissionError as error:
            raise ContractError(
                f"cannot bind generated directory {path} to runtime uid/gid {uid}:{gid}"
            ) from error


def _reset_provider_configtree(path: Path) -> None:
    if path.is_symlink() or not path.is_dir():
        raise ContractError(f"provider configtree is not a regular directory: {path}")
    entries = tuple(path.iterdir())
    unknown = sorted(entry.name for entry in entries if entry.name not in PROVIDER_CONFIGTREE_FILES)
    if unknown:
        raise ContractError(
            "provider configtree contains unmanaged entries: " + ", ".join(unknown)
        )
    for entry in entries:
        if entry.is_symlink() or not entry.is_file():
            raise ContractError(f"provider configtree entry is not a regular file: {entry}")
        entry.unlink()


def _json(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ContractError(f"expected JSON object: {path}")
    return value


def _public_jwks(context: ComposeContext, secret_ref: str, filename: str) -> dict[str, object]:
    path = context.generated_root / "keycloak/public-jwks" / filename
    if path.is_symlink() or not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o644:
        raise ContractError(f"public JWKS projection is unavailable: {path}")
    return validate_public_jwks(_json(path), owner=secret_ref)


def _origin(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ContractError(f"expected HTTPS public URL: {value}")
    return f"{parsed.scheme}://{parsed.netloc}"


def _gateway_site(value: str) -> str:
    parsed = urlsplit(_origin(value))
    if parsed.hostname is None or ":" in parsed.hostname:
        raise ContractError(f"expected DNS-hosted HTTPS gateway URL: {value}")
    return f"https://{parsed.hostname}"


def _image_digest(context: ComposeContext) -> str:
    image = context.env["WEAVE_KEYCLOAK_IMAGE"]
    digest_match = re.fullmatch(
        r"[A-Za-z0-9._-]+(?::[0-9]+)?/[A-Za-z0-9._/-]+@(sha256:[0-9a-f]{64})",
        image,
    )
    if digest_match:
        return digest_match.group(1)
    if (
        context.environment == "e2e"
        and context.isolated_namespace is not None
        and re.fullmatch(r"sha256:[0-9a-f]{64}", image)
    ):
        return image
    raise ContractError(
        f"{context.profile} render requires one immutable downstream Keycloak OCI digest; "
        "the preparation task builds the version-pinned runtime before render"
    )


def _overlay(context: ComposeContext, baseline_revision: str) -> dict[str, object]:
    profile = context.environment
    smtp: dict[str, object]
    if profile == "dev":
        smtp = {
            "host": "mailpit",
            "port": 1025,
            "fromAddress": f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}",
            "fromDisplayName": "Weave",
            "ssl": False,
            "startTls": False,
        }
    elif profile in {"dogfood", "e2e"}:
        if context.env.get("WEAVE_MAILPIT_REQUIRE_TLS", "").lower() != "true":
            raise ContractError(f"{profile} SMTP requires Mailpit implicit TLS via WEAVE_MAILPIT_REQUIRE_TLS=true")
        smtp = {
            "host": "mailpit",
            "port": 1025,
            "fromAddress": f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}",
            "fromDisplayName": "Weave",
            "ssl": True,
            "startTls": False,
        }
    elif profile == "prod":
        host = context.env.get("WEAVE_SMTP_HOST", "")
        if not host or host == "mailpit":
            raise ContractError("prod requires an external implicit-TLS WEAVE_SMTP_HOST")
        username = context.env.get("WEAVE_SMTP_USERNAME", "")
        if not username:
            raise ContractError("prod requires a non-secret WEAVE_SMTP_USERNAME")
        smtp = {
            "host": host,
            "port": int(context.env.get("WEAVE_SMTP_PORT", "465")),
            "fromAddress": context.env.get("WEAVE_SMTP_FROM_ADDRESS", f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}"),
            "fromDisplayName": context.env.get("WEAVE_SMTP_FROM_DISPLAY_NAME", "Weave"),
            "ssl": True,
            "startTls": False,
            "username": username,
            "passwordVaultRef": "${vault.smtp-password}",
        }
    else:
        raise ContractError(f"unsupported render environment: {profile}")
    value: dict[str, object] = {
        "apiVersion": "weave.keycloak-environment-overlay/v3",
        "revision": "",
        "baselineRevision": baseline_revision,
        "environment": profile,
        "publicUrls": {
            "weave": _origin(context.env["WEAVE_PUBLIC_URL"]),
            "api": context.env["WEAVE_API_URL"],
            "auth": _origin(context.env["WEAVE_AUTH_URL"]),
        },
        "smtpEndpoints": smtp,
        "organizationMetadata": {
            "name": context.env["WEAVE_ORGANIZATION_NAME"],
            "alias": context.env["WEAVE_ORGANIZATION_ALIAS"],
            "description": context.env["WEAVE_ORGANIZATION_DESCRIPTION"],
            "redirectUri": _origin(context.env["WEAVE_PUBLIC_URL"]),
        },
        "secretRefs": {
            "weaveBackendJwk": "secretref:keycloak/weave-backend-jwk",
            "weaveMcpServerJwk": "secretref:keycloak/weave-mcp-server-jwk",
            "identityAdmin": "secretref:keycloak/weave-identity-admin-jwk",
            "agentRuntimeAdmin": "secretref:keycloak/weave-agent-runtime-admin-jwk",
        },
        "imageDigest": _image_digest(context),
    }
    value["revision"] = revision(value)
    return value


def _replace_strings(value: object, replacements: tuple[tuple[str, str], ...]) -> object:
    if isinstance(value, str):
        result = value
        for source, target in replacements:
            result = result.replace(source, target)
        return result
    if isinstance(value, list):
        return [_replace_strings(item, replacements) for item in value]
    if isinstance(value, dict):
        return {key: _replace_strings(item, replacements) for key, item in value.items()}
    return value


def _render_desired(baseline: dict[str, object], overlay: dict[str, object]) -> dict[str, object]:
    desired = copy.deepcopy(baseline)
    public = overlay["publicUrls"]
    assert isinstance(public, dict)
    replacements = (
        ("https://api.weave.test/mcp", f"{_origin(str(public['api']))}/mcp"),
        ("https://api.weave.test/api", str(public["api"])),
        ("https://auth.weave.test", str(public["auth"])),
        ("https://weave.test", str(public["weave"])),
    )
    desired = _replace_strings(desired, replacements)
    assert isinstance(desired, dict)
    if desired.get("apiVersion") != "weave.keycloak-desired-state/v3":
        raise ContractError("realm projection requires the canonical Keycloak desired-state v3 corpus")
    if desired.get("keycloakVersion") != "26.7.1":
        raise ContractError("canonical desired state must pin Keycloak 26.7.1")
    if "groups" in desired:
        raise ContractError("desired-state v3 must not contain legacy human realm groups")
    client_policies = desired.get("clientPolicies")
    if not isinstance(client_policies, list) or len(client_policies) != 1:
        raise ContractError("canonical desired state must declare exactly one workload registration policy")
    organization_groups = desired.get("organizationGroups")
    if not isinstance(organization_groups, list):
        raise ContractError("desired-state v3 must declare native organizationGroups")
    observed_group_paths = {group.get("path") for group in organization_groups if isinstance(group, dict)}
    if observed_group_paths != {"/owners", "/admins", "/members", "/guests", "/capabilities", "/capabilities/weaver"}:
        raise ContractError("canonical organizationGroups must contain the four role groups and Weaver capability namespace")
    if any(group.get("organizationRef") != "organization:weave-primary" for group in organization_groups if isinstance(group, dict)):
        raise ContractError("all canonical groups must belong to the primary organization")
    by_path = {group.get("path"): group for group in organization_groups if isinstance(group, dict)}
    if any(by_path[path].get("parentGroupRef") is not None for path in {"/owners", "/admins", "/members", "/guests", "/capabilities"}):
        raise ContractError("role and capability namespace groups must be top-level")
    if (
        by_path["/capabilities/weaver"].get("parentGroupRef") != "organization-group:weave-primary:capabilities"
        or by_path["/capabilities"].get("roleRefs") != []
        or by_path["/capabilities/weaver"].get("roleRefs") != []
    ):
        raise ContractError("Weaver capability must be a role-free leaf below the canonical capability namespace")
    fgap = desired.get("fineGrainedAdminPermissions")
    if not isinstance(fgap, dict) or fgap.get("enabled") is not True:
        raise ContractError("desired-state v3 must enable declared Organizations FGAP")
    grants = desired.get("serviceAccountRoleGrants")
    if not isinstance(grants, list):
        raise ContractError("desired-state v3 service-account grants are missing")
    identity_grants = [grant for grant in grants if isinstance(grant, dict) and grant.get("clientKey") == "client:weave-identity-admin"]
    if len(identity_grants) != 1 or identity_grants[0].get("roleRefs") != [
        "builtin-role:realm-management:query-organizations",
        "builtin-role:realm-management:query-users",
    ]:
        raise ContractError("identity admin must have only the query-organizations and query-users collection gates plus declared FGAP")
    realm_contract = desired.get("realm")
    if not isinstance(realm_contract, dict) or realm_contract.get("adminPermissionsEnabled") is not True:
        raise ContractError("desired-state v3 must enable Keycloak admin permissions")
    desired["environment"] = overlay["environment"]
    provenance = desired["provenance"]
    assert isinstance(provenance, dict)
    provenance["overlayRevision"] = overlay["revision"]
    realm = desired["realm"]
    assert isinstance(realm, dict)
    realm["frontendUrl"] = public["auth"]
    smtp = overlay["smtpEndpoints"]
    assert isinstance(smtp, dict)
    realm["smtpServer"] = {key: value for key, value in smtp.items() if key != "passwordVaultRef"}
    if smtp.get("passwordVaultRef"):
        realm["smtpServer"]["password"] = smtp["passwordVaultRef"]
    organizations = desired.get("organizations")
    if not isinstance(organizations, list) or len(organizations) != 1:
        raise ContractError("desired-state v3 must declare exactly one bootstrap organization")
    organization = organizations[0]
    if not isinstance(organization, dict) or organization.get("key") != "organization:weave-primary":
        raise ContractError("desired-state v3 bootstrap organization is malformed")
    metadata = overlay["organizationMetadata"]
    assert isinstance(metadata, dict)
    organization["name"] = metadata["name"]
    organization["alias"] = metadata["alias"]
    organization["description"] = metadata["description"]
    organization["redirectUrl"] = metadata["redirectUri"]
    desired["revision"] = revision(desired)
    return desired


def _caddy(context: ComposeContext) -> str:
    env = context.env
    public_site = _gateway_site(env["WEAVE_PUBLIC_URL"])
    api_site = _gateway_site(env["WEAVE_API_URL"])
    auth_site = _gateway_site(env["WEAVE_AUTH_URL"])
    matrix_site = _gateway_site(env["WEAVE_MATRIX_URL"])
    files_site = _gateway_site(env["WEAVE_FILES_URL"])
    backend = "host.docker.internal:8080" if context.profile == "dev" else "backend:8080"
    mcp = "host.docker.internal:8091" if context.profile == "dev" else "mcp:8091"
    mcp_handler = "reverse_proxy " + mcp
    matrix_handler = "reverse_proxy synapse:8008"
    mas_handler = "reverse_proxy mas:8080"
    files_handler = """reverse_proxy nextcloud:80 {
    header_up X-Forwarded-For {http.request.remote.host}
  }"""
    if env["WEAVE_CHAT_PROVIDER"] != "matrix-synapse":
        matrix_handler = 'respond `{"error":"matrix provider disabled"}` 404'
        mas_handler = matrix_handler
    if env["WEAVE_FILES_PROVIDER"] != "nextcloud-webdav" and env["WEAVE_CALENDAR_PROVIDER"] != "nextcloud-caldav":
        files_handler = 'respond `{"error":"nextcloud provider disabled"}` 404'
    mailpit_block = ""
    if "dev-tools" in context.active_profiles or context.profile in {"dogfood", "e2e"}:
        mailpit_url = env.get("WEAVE_MAILPIT_URL")
        if not mailpit_url:
            raise ContractError(f"{context.profile} Mailpit gateway requires WEAVE_MAILPIT_URL")
        mailpit_site = _gateway_site(mailpit_url)
        mailpit_block = f"""{mailpit_site} {{
  tls /certs/mailpit-cert.pem /certs/mailpit-key.pem
  @private_network remote_ip private_ranges
  handle @private_network {{
    reverse_proxy mailpit:8025
  }}
  respond "Forbidden" 403
}}
"""
    return f"""{{
  admin off
  auto_https off
}}

{public_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  handle_path /api/* {{
    reverse_proxy {backend}
  }}
  handle_path /mcp* {{
    {mcp_handler}
  }}
  reverse_proxy {backend}
}}

{api_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  handle /.well-known/oauth-protected-resource* {{
    {mcp_handler}
  }}
  handle /mcp* {{
    {mcp_handler}
  }}
  reverse_proxy {backend}
}}

{auth_site} {{
  tls /certs/cert.pem /certs/key.pem
  reverse_proxy keycloak:8080
}}
{mailpit_block}

{matrix_site} {{
  tls /certs/cert.pem /certs/key.pem
  @internal path /api/internal/* /actuator/*
  handle @internal {{
    respond 404
  }}
  @client_well_known path /.well-known/matrix/client
  handle @client_well_known {{
    header Content-Type application/json
    respond `{{"m.homeserver":{{"base_url":"{env['WEAVE_MATRIX_URL']}"}}}}` 200
  }}
  @synapse path /_matrix/* /_synapse/client/* /_synapse/mas/*
  handle @synapse {{
    {matrix_handler}
  }}
  handle {{
    {mas_handler}
  }}
}}

{files_site} {{
  tls /certs/cert.pem /certs/key.pem
  {files_handler}
}}
"""


def _mas(context: ComposeContext) -> str:
    env = context.env
    insecure = "true" if context.profile == "dev" else "false"
    return f"""http:
  public_base: {env['WEAVE_MATRIX_URL']}/
  listeners:
    - name: web
      resources:
        - name: discovery
        - name: human
        - name: oauth
        - name: compat
        - name: graphql
        - name: assets
        - name: health
      binds:
        - address: \"[::]:8080\"
database:
  host: postgres
  port: 5432
  username: {env['WEAVE_MAS_DB_USERNAME']}
  password: {_read_secret(context, 'mas-db-password')}
  database: {env['WEAVE_MAS_DB_NAME']}
  ssl_mode: disable
matrix:
  kind: synapse_modern
  homeserver: {env['WEAVE_MATRIX_HOST']}
  endpoint: http://synapse:8008
  secret: {_read_secret(context, 'mas-matrix-secret')}
secrets:
  encryption: {_read_secret(context, 'mas-encryption-secret')}
  keys:
    - kid: weave-mas-current
      key_file: /config/signing.key
passwords:
  enabled: false
account:
  password_registration_enabled: false
  login_with_email_allowed: true
policy:
  data:
    client_registration:
      allow_insecure_uris: {insecure}
upstream_oauth2:
  providers:
    - id: 01J0000000WEAVEKEYC10AKMAS
      issuer: {env['WEAVE_AUTH_URL']}/realms/weave
      human_name: Weave Identity
      client_id: matrix-mas
      client_secret: {_read_secret(context, 'keycloak-matrix-mas')}
      token_endpoint_auth_method: client_secret_post
      scope: \"openid email profile\"
      discovery_mode: oidc
      pkce_method: auto
      fetch_userinfo: true
"""


def _synapse(context: ComposeContext) -> str:
    env = context.env
    return f"""server_name: \"{env['WEAVE_MATRIX_HOST']}\"
pid_file: /data/homeserver.pid
public_baseurl: \"{env['WEAVE_MATRIX_URL']}/\"