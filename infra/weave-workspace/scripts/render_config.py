#!/usr/bin/env python3
"""Render deterministic, support-safe Compose configuration from the pinned corpus."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import stat
from pathlib import Path
from urllib.parse import urlsplit

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
    "secretref:keycloak/weave-identity-admin-jwk": (
        "keycloak-weave-identity-admin-jwk.json"
    ),
    "secretref:keycloak/weave-agent-runtime-admin-jwk": (
        "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin"
    ),
    "secretref:smtp/password": "smtp-password",
}
REQUIRED_PRIVATE_FILES = (
    "backend-db-password",
    "identity-reference-hmac-key",
    "keycloak-db-password",
    "control-db-password",
    "keycloak-weave-identity-admin-jwk.json",
    "keycloak-weave-backend-jwk.json",
    "keycloak-weave-mcp-server-jwk.json",
    "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin",
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
    """Remove credentials from a prior provider selection before rendering."""
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
        # compose_env already limits a local Keycloak image ID to the isolated
        # E2E boundary. Preserve that exact content digest in desired-state
        # provenance instead of reintroducing a conflicting registry-only rule.
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
    elif profile == "e2e":
        if context.env.get("WEAVE_MAILPIT_REQUIRE_TLS", "").lower() != "true":
            raise ContractError(
                "e2e SMTP requires Mailpit implicit TLS via WEAVE_MAILPIT_REQUIRE_TLS=true"
            )
        smtp = {
            "host": "mailpit",
            "port": 1025,
            "fromAddress": f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}",
            "fromDisplayName": "Weave",
            "ssl": True,
            "startTls": False,
        }
    elif profile in {"dogfood", "prod"}:
        host = context.env.get("WEAVE_SMTP_HOST", "")
        if not host or host == "mailpit":
            raise ContractError(
                "dogfood/prod requires an external implicit-TLS WEAVE_SMTP_HOST"
            )
        username = context.env.get("WEAVE_SMTP_USERNAME", "")
        if not username:
            raise ContractError("dogfood/prod requires a non-secret WEAVE_SMTP_USERNAME")
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
        raise ContractError(
            "dogfood/prod Keycloak SMTP is blocked until the canonical File Vault "
            "mount and realm import path are implemented and qualified"
        )
    else:
        raise ContractError(f"unsupported render environment: {profile}")
    value: dict[str, object] = {
        "apiVersion": "weave.keycloak-environment-overlay/v2",
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
        raise ContractError("Identity Ops requires the canonical Keycloak desired-state v3 corpus")
    if desired.get("keycloakVersion") != "26.7.0":
        raise ContractError("canonical desired state must pin Keycloak 26.7.0")
    if "groups" in desired:
        raise ContractError("desired-state v3 must not contain legacy human realm groups")
    client_policies = desired.get("clientPolicies")
    if not isinstance(client_policies, list) or len(client_policies) != 1:
        raise ContractError(
            "canonical desired state must declare exactly one workload registration policy"
        )
    organization_groups = desired.get("organizationGroups")
    if not isinstance(organization_groups, list):
        raise ContractError("desired-state v3 must declare native organizationGroups")
    observed_group_paths = {group.get("path") for group in organization_groups if isinstance(group, dict)}
    if observed_group_paths != {
        "/owners",
        "/admins",
        "/members",
        "/guests",
        "/capabilities",
        "/capabilities/weaver",
    }:
        raise ContractError(
            "canonical organizationGroups must contain the four role groups and Weaver capability namespace"
        )
    if any(
        group.get("organizationRef") != "organization:weave-primary"
        for group in organization_groups
        if isinstance(group, dict)
    ):
        raise ContractError("all canonical groups must belong to the primary organization")
    by_path = {
        group.get("path"): group
        for group in organization_groups
        if isinstance(group, dict)
    }
    if any(
        by_path[path].get("parentGroupRef") is not None
        for path in {"/owners", "/admins", "/members", "/guests", "/capabilities"}
    ):
        raise ContractError("role and capability namespace groups must be top-level")
    if (
        by_path["/capabilities/weaver"].get("parentGroupRef")
        != "organization-group:weave-primary:capabilities"
        or by_path["/capabilities"].get("roleRefs") != []
        or by_path["/capabilities/weaver"].get("roleRefs") != []
    ):
        raise ContractError(
            "Weaver capability must be a role-free leaf below the canonical capability namespace"
        )
    fgap = desired.get("fineGrainedAdminPermissions")
    if not isinstance(fgap, dict) or fgap.get("enabled") is not True:
        raise ContractError("desired-state v3 must enable declared Organizations FGAP")
    grants = desired.get("serviceAccountRoleGrants")
    if not isinstance(grants, list):
        raise ContractError("desired-state v3 service-account grants are missing")
    identity_grants = [
        grant for grant in grants
        if isinstance(grant, dict) and grant.get("clientKey") == "client:weave-identity-admin"
    ]
    if len(identity_grants) != 1 or identity_grants[0].get("roleRefs") != [
        "builtin-role:realm-management:query-organizations",
        "builtin-role:realm-management:query-users",
    ]:
        raise ContractError(
            "identity admin must have only the query-organizations and query-users collection "
            "gates plus declared FGAP"
        )
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
    realm["smtp"] = copy.deepcopy(overlay["smtpEndpoints"])
    organizations = desired["organizations"]
    if not isinstance(organizations, list) or len(organizations) != 1:
        raise ContractError("canonical baseline must contain exactly one managed organization")
    organization = organizations[0]
    assert isinstance(organization, dict)
    organization.update(copy.deepcopy(overlay["organizationMetadata"]))
    desired["revision"] = revision(desired)
    return desired


def _caddy(context: ComposeContext) -> str:
    env = context.env
    backend = f"host.docker.internal:{env['WEAVE_HOST_DEV_BACKEND_PORT']}" if context.profile == "dev" else "backend:8080"
    mcp_handler = "respond \"MCP workload edge is not part of the host-dev dependency profile\" 503" if context.profile == "dev" else "reverse_proxy mcp:8091"
    public_site = _gateway_site(env["WEAVE_PUBLIC_URL"])
    api_site = _gateway_site(env["WEAVE_API_ORIGIN"])
    auth_site = _gateway_site(env["WEAVE_AUTH_URL"])
    matrix_site = _gateway_site(env["WEAVE_MATRIX_URL"])
    files_site = _gateway_site(env["WEAVE_FILES_URL"])
    matrix_handler = (
        "reverse_proxy synapse:8008"
        if env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse"
        else 'respond "Optional Matrix provider is not selected" 404'
    )
    mas_handler = (
        "reverse_proxy mas:8080"
        if env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse"
        else 'respond "Optional Matrix provider is not selected" 404'
    )
    files_handler = (
        """reverse_proxy nextcloud:80 {
    header_up X-Forwarded-For {http.request.remote.host}
    header_up X-Forwarded-Host {host}
    header_up X-Forwarded-Proto {scheme}
  }"""
        if (
            env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
            or env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav"
        )
        else 'respond "Optional Nextcloud provider is not selected" 404'
    )
    return f"""{{
  admin off
}}

http:// {{
  @ca path /weave-local-ca.pem
  handle @ca {{
    root * /certs
    rewrite * /ca.pem
    file_server
  }}
  respond \"HTTPS required\" 308
}}

{public_site} {{
  tls /certs/cert.pem /certs/key.pem
  encode zstd gzip
  @internal path /api/internal/* /actuator/*
  respond @internal \"Not Found\" 404
  @product_api path /api/*
  reverse_proxy @product_api {backend}
  @admin_console path /admin-console /admin-console/*
  reverse_proxy @admin_console {backend}
  @files path /files /files/*
  respond @files \"Weave Files is exposed through the backend-owned WebDAV facade.\" 200
  @calendar path /calendar /calendar/*
  respond @calendar \"Weave Calendar is exposed through the backend-owned CalDAV facade.\" 200
  respond \"Weave product gateway\" 200
}}

{api_site} {{
  tls /certs/cert.pem /certs/key.pem
  encode zstd gzip
  @internal path /api/internal/* /actuator/*
  respond @internal \"Not Found\" 404
  @mcp path /mcp /mcp/* /.well-known/oauth-protected-resource/mcp
  handle @mcp {{
    {mcp_handler}
  }}
  reverse_proxy {backend}
}}

{auth_site} {{
  tls /certs/cert.pem /certs/key.pem
  reverse_proxy keycloak:8080
}}

{matrix_site} {{
  tls /certs/cert.pem /certs/key.pem
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
listeners:
  - port: 8008
    tls: false
    type: http
    x_forwarded: true
    resources:
      - names: [client]
        compress: false
database:
  name: psycopg2
  args:
    user: {env['WEAVE_SYNAPSE_DB_USERNAME']}
    password: \"{_read_secret(context, 'synapse-db-password')}\"
    database: {env['WEAVE_SYNAPSE_DB_NAME']}
    host: postgres
    port: 5432
    cp_min: 5
    cp_max: 10
media_store_path: /data/media_store
report_stats: false
enable_registration: false
registration_shared_secret: \"{_read_secret(context, 'synapse-registration-shared-secret')}\"
macaroon_secret_key: \"{_read_secret(context, 'synapse-macaroon-secret-key')}\"
form_secret: \"{_read_secret(context, 'synapse-form-secret')}\"
signing_key_path: \"/data/{env['WEAVE_MATRIX_HOST']}.signing.key\"
app_service_config_files:
  - /run/weave-chat-appservice/registration.yaml
trusted_key_servers: []
suppress_key_server_warning: true
matrix_authentication_service:
  enabled: true
  endpoint: http://mas:8080
  secret: \"{_read_secret(context, 'mas-matrix-secret')}\"
"""


def _appservice(context: ComposeContext) -> str:
    env = context.env
    host_regex = re.escape(env["WEAVE_MATRIX_HOST"])
    callback = (
        f"http://host.docker.internal:{env['WEAVE_HOST_DEV_BACKEND_PORT']}/api/internal/chat/matrix/appservice"
        if context.profile == "dev"
        else "http://backend:8080/api/internal/chat/matrix/appservice"
    )
    return f"""id: weave-chat-synapse
url: {callback}
as_token: \"{_read_secret(context, 'matrix-appservice-as-token')}\"
hs_token: \"{_read_secret(context, 'matrix-appservice-hs-token')}\"
sender_localpart: _weave_appservice
rate_limited: true
receive_ephemeral: false
namespaces:
  users:
    - exclusive: true
      regex: '^@_weave_[a-z0-9]{{26,64}}:{host_regex}$'
  aliases:
    - exclusive: true
      regex: '^#_weave_[a-z0-9]{{26,64}}:{host_regex}$'
  rooms: []
"""


def _backend_env(context: ComposeContext) -> str:
    env = context.env
    host_dev = context.profile == "dev"
    keycloak_base = (
        f"http://127.0.0.1:{env['WEAVE_KEYCLOAK_HOST_PORT']}" if host_dev else "http://keycloak:8080"
    )
    matrix_base = (
        f"http://127.0.0.1:{env['WEAVE_SYNAPSE_HOST_PORT']}" if host_dev else "http://synapse:8008"
    )
    nextcloud_base = (
        f"http://127.0.0.1:{env['WEAVE_NEXTCLOUD_HOST_PORT']}" if host_dev else "http://nextcloud"
    )
    appservice_root = (
        context.generated_root / "backend/configtree"
        if host_dev
        else Path("/run/secrets/providers")
    )
    calendar_id = "weave-workspace"
    if context.isolated_namespace is not None:
        calendar_id = f"{calendar_id}-{context.isolated_namespace}"
    calendar_path = f"/remote.php/dav/calendars/{env['WEAVE_NEXTCLOUD_ACTOR_USERNAME']}/{calendar_id}/"
    values = {
        "SPRING_PROFILES_ACTIVE": context.profile,
        "WEAVE_OIDC_ISSUER_URI": f"{env['WEAVE_AUTH_URL']}/realms/weave",
        "WEAVE_OIDC_JWK_SET_URI": f"{keycloak_base}/realms/weave/protocol/openid-connect/certs",
        "WEAVE_OIDC_REQUIRED_AUDIENCE": env["WEAVE_API_URL"],
        "WEAVE_API_BASE_URL": env["WEAVE_API_URL"],
        "WEAVE_CHAT_PROVIDER": env["WEAVE_CHAT_PROVIDER"],
        "WEAVE_FILES_PROVIDER": env["WEAVE_FILES_PROVIDER"],
        "WEAVE_FILES_NATIVE_BLOB_STORE": env["WEAVE_FILES_NATIVE_BLOB_STORE"],
        "WEAVE_FILES_NATIVE_FILESYSTEM_ROOT": (
            str(context.generated_root / "native-files/blobs")
            if host_dev
            else "/var/lib/weave/files/blobs"
        ),
        "WEAVE_CALENDAR_PROVIDER": env["WEAVE_CALENDAR_PROVIDER"],
        "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ENABLED": "true",
        "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ORGANIZATION_REF": "tenant-default",
        "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_ADAPTER_KEY": env["WEAVE_FILES_PROVIDER"],
        "WEAVE_PROVIDER_BINDINGS_BOOTSTRAP_FILES_CONFIGURATION_REF": (
            "secretref:files:nextcloud"
            if env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
            else "native:filesystem"
        ),
        "WEAVE_IDENTITY_KEYCLOAK_BASE_URL": keycloak_base,
        "WEAVE_IDENTITY_KEYCLOAK_CREDENTIAL_REF": (
            "credentialref://weave/keycloak/weave-identity-admin"
        ),
        "WEAVE_IDENTITY_KEYCLOAK_PRIVATE_JWK_FILE": str(
            context.secret_root / "keycloak-weave-identity-admin-jwk.json"
            if host_dev
            else Path(
                "/run/secrets/identity-admin/"
                "weave-identity-admin-private-jwk.json"
            )
        ),
        "WEAVE_IDENTITY_KEYCLOAK_PRIVATE_KEY_JWT_AUDIENCE": (
            f"{env['WEAVE_AUTH_URL']}/realms/weave"
        ),
        "WEAVE_IDENTITY_KEYCLOAK_ORGANIZATION_ALIAS": env["WEAVE_ORGANIZATION_ALIAS"],
        "WEAVE_IDENTITY_REFERENCE_HMAC_SECRET_FILE": str(
            context.secret_root / "identity-reference-hmac-key"
            if host_dev
            else Path("/run/secrets/identity-reference-hmac-key")
        ),
        "WEAVE_MATRIX_BASE_URL": env["WEAVE_API_ORIGIN"],
        "WEAVE_WORKSPACE_CHAT_ENABLED": "true",
        "WEAVE_WORKSPACE_CHAT_READINESS": "ready",
        "WEAVE_WORKSPACE_FILES_ENABLED": "true",
        "WEAVE_WORKSPACE_FILES_READINESS": "ready",
        "WEAVE_WORKSPACE_CALENDAR_ENABLED": "true",
        "WEAVE_WORKSPACE_CALENDAR_READINESS": "ready",
        "WEAVE_MATRIX_FEDERATION_ENABLED": "false",
        "WEAVE_IDENTITY_BOOTSTRAP_OWNER_ENABLED": (
            "true" if context.isolated_namespace is not None else "false"
        ),
    }
    if env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        values.update(
            {
                "WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL": matrix_base,
                "WEAVE_CHAT_MATRIX_SERVER_NAME": env["WEAVE_MATRIX_HOST"],
                "WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE": str(appservice_root / "matrix-as-token"),
                "WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE": str(appservice_root / "matrix-hs-token"),
                "WEAVE_MATRIX_BASE_URL": matrix_base,
            }
        )
    if (
        env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
        or env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav"
    ):
        values.update(
            {
                "WEAVE_NEXTCLOUD_BASE_URL": nextcloud_base,
                "WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME": env["WEAVE_NEXTCLOUD_ACTOR_USERNAME"],
                "WEAVE_CALDAV_BASE_URL": nextcloud_base,
                "WEAVE_CALDAV_BACKEND_USERNAME": env["WEAVE_NEXTCLOUD_ACTOR_USERNAME"],
                "WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE": calendar_path,
            }
        )
    if context.environment == "e2e":
        values["WEAVE_IDENTITY_BOOTSTRAP_OWNER_TOKEN_FILE"] = (
            "/run/secrets/weave/bootstrap-owner-token"
        )
    if host_dev:
        values["SPRING_CONFIG_IMPORT"] = f"configtree:{context.generated_root / 'backend/configtree'}/"
    else:
        values.update(
            {
                "SPRING_DATASOURCE_URL": f"jdbc:postgresql://postgres:5432/{env['WEAVE_BACKEND_DB_NAME']}",
                "SPRING_DATASOURCE_USERNAME": env["WEAVE_BACKEND_DB_USERNAME"],
                "WEAVE_AGENT_RUNTIME_PROFILE_SIGNING_SECRET_ROOT": "/run/secrets/agent-runtime/profile-signing",
                "WEAVE_AGENT_RUNTIME_SECRET_ROOT": "/run/secrets/agent-runtime/workloads",
            }
        )
    if context.environment == "e2e":
        values.update(
            {
                "WEAVE_AGENT_RUNTIME_STATE_STORE_ENABLED": "true",
                "WEAVE_AGENT_RUNTIME_STATE_WRAPPING_KEY_ROOT":
                    "/run/secrets/agent-runtime/state-wrapping",
                "WEAVE_AGENT_RUNTIME_STATE_S3_ENDPOINT": "http://runtime-state:9000",
                "WEAVE_AGENT_RUNTIME_STATE_S3_REGION": "us-east-1",
                "WEAVE_AGENT_RUNTIME_STATE_S3_BUCKET": "weave-runtime-state",
                "WEAVE_AGENT_RUNTIME_STATE_S3_CREDENTIAL_REF":
                    "secretref:runtime-state/minio",
                "WEAVE_AGENT_RUNTIME_STATE_S3_ACCESS_KEY_FILE":
                    "/run/secrets/weave/runtime-state-s3-access-key",
                "WEAVE_AGENT_RUNTIME_STATE_S3_SECRET_KEY_FILE":
                    "/run/secrets/weave/runtime-state-s3-secret-key",
                "WEAVE_AGENT_RUNTIME_STATE_S3_PATH_STYLE_ACCESS": "true",
                "WEAVE_AGENT_RUNTIME_PROFILE_SIGNING_ENABLED": "true",
                "WEAVE_AGENT_RUNTIME_PROFILE_SIGNING_SECRET_ROOT":
                    "/run/secrets/agent-runtime/profile-signing",
                "WEAVE_AGENT_RUNTIME_PROFILE_TTL": "PT2M",
                "WEAVE_AGENT_RUNTIME_POLICY_ENABLED": "true",
                "WEAVE_AGENT_RUNTIME_POLICY_FILE": "/app/agent-runtime-policy.json",
                "WEAVE_AGENT_RUNTIME_ENTITLEMENT_ENABLED": "true",
                "WEAVE_AGENT_RUNTIME_ENTITLEMENT_CAPABILITIES": "files.read",
                "WEAVE_AGENT_RUNTIME_WORKLOAD_IDENTITY_ENABLED": "true",
                "WEAVE_AGENT_RUNTIME_KEYCLOAK_ADMIN_BASE_URL": keycloak_base,
                "WEAVE_AGENT_RUNTIME_ISSUER":
                    f"{env['WEAVE_AUTH_URL']}/realms/weave",
                "WEAVE_AGENT_RUNTIME_REALM": "weave",
                "WEAVE_AGENT_RUNTIME_ADMIN_CLIENT_ID": "weave-agent-runtime-admin",
                "WEAVE_AGENT_RUNTIME_ADMIN_CREDENTIAL_REF":
                    "credentialref://weave/keycloak/weave-agent-runtime-admin",
                "WEAVE_AGENT_RUNTIME_ORGANIZATION_REF": "tenant-default",
                "WEAVE_AGENT_RUNTIME_KEYCLOAK_ORGANIZATION_ALIAS":
                    env["WEAVE_ORGANIZATION_ALIAS"],
                "WEAVE_AGENT_RUNTIME_SECRET_ROOT": "/run/secrets/agent-runtime/workloads",
                "WEAVE_AGENT_RUNTIME_DEFAULT_CLIENT_SCOPES": "weaver-runtime-workload",
                "WEAVE_AGENT_RUNTIME_OPTIONAL_CLIENT_SCOPES":
                    "agent-runtime.profile.read,mcp.tools,files.read",
                "WEAVE_AGENT_RUNTIME_ACCESS_TOKEN_LIFESPAN_SECONDS": "59",
            }
        )
    if context.isolated_namespace is not None:
        run_id = os.environ.get("WEAVE_E2E_RUN_ID", "")
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{5,39}", run_id):
            raise ContractError(
                "isolated backend authorization requires the validated E2E run ID"
            )
        values.update(
            {
                "WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_CLAIM": "preferred_username",
            }
        )
    return "".join(f"{key}={value}\n" for key, value in sorted(values.items()))


def _mcp_env(context: ComposeContext, *, host_dev: bool = False) -> str:
    env = context.env
    issuer = f"{env['WEAVE_AUTH_URL']}/realms/weave"
    keycloak_base = (
        f"http://127.0.0.1:{env['WEAVE_KEYCLOAK_HOST_PORT']}"
        if host_dev
        else "http://keycloak:8080"
    )
    values = {
        "WEAVE_MCP_PORT": "8091",
        "WEAVE_OIDC_ISSUER_URI": issuer,
        "WEAVE_OIDC_JWK_SET_URI": f"{keycloak_base}/realms/weave/protocol/openid-connect/certs",
        "WEAVE_MCP_RESOURCE_URI": f"{env['WEAVE_API_ORIGIN']}/mcp",
        "WEAVE_MCP_RESOURCE_METADATA_URI": f"{env['WEAVE_API_ORIGIN']}/.well-known/oauth-protected-resource/mcp",
        "WEAVE_MCP_AUTHORIZATION_SERVER": issuer,
        "WEAVE_MCP_REQUIRED_SCOPES": "mcp.tools,files.read",
        "WEAVE_MCP_TOKEN_URI": f"{keycloak_base}/realms/weave/protocol/openid-connect/token",
        "WEAVE_MCP_EXCHANGE_CLIENT_ID": "weave-mcp-server",
        "WEAVE_MCP_EXCHANGE_CLIENT_JWK_FILE": "/run/secrets/weave/mcp-private-jwk.json",
        "WEAVE_MCP_BACKEND_RESOURCE_URI": env["WEAVE_API_URL"],
        "WEAVE_MCP_BACKEND_FILES_URI": "http://backend:8080/dav/files",
        "WEAVE_MCP_EXCHANGE_SCOPES": "files.read",
    }
    if host_dev:
        values["WEAVE_MCP_BACKEND_FILES_URI"] = "http://127.0.0.1:8080/dav/files"
        values["WEAVE_MCP_EXCHANGE_CLIENT_JWK_FILE"] = str(
            context.secret_root / "keycloak-weave-mcp-server-jwk.json"
        )
    return "".join(f"{key}={value}\n" for key, value in sorted(values.items()))


def render(context: ComposeContext) -> None:
    corpus_root, specification_commit = specification_context(context)
    examples = corpus_root / "contracts/examples"
    baseline_path = examples / "keycloak-desired-state.valid.json"
    baseline = _json(baseline_path)
    assert_revision(baseline, baseline_path)
    baseline_revision = str(baseline["provenance"]["baselineRevision"])
    overlay = _overlay(context, baseline_revision)
    desired = _render_desired(baseline, overlay)
    for name in REQUIRED_PRIVATE_FILES:
        _read_secret(context, name)
    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        for name in MATRIX_PRIVATE_FILES:
            _read_secret(context, name)
    if (
        context.env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
        or context.env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav"
    ):
        for name in NEXTCLOUD_PRIVATE_FILES:
            _read_secret(context, name)
    if "storage-s3" in context.active_profiles:
        for name in S3_PRIVATE_FILES:
            _read_secret(context, name)
    if context.environment in {"dogfood", "prod"}:
        _read_secret(context, "smtp-password")
    generated = context.generated_root
    runtime_owner = (int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"]))
    _runtime_directory(generated / "schema-init", runtime_owner)
    # Compose mounts this directory read-only into Keycloak. Create and own it
    # during rendering so Docker never materializes an absent bind source as a
    # root-owned host directory. The qualified RealmRepresentation renderer
    # will populate this directory; until then the dogfood/prod render guard
    # above keeps persistent environments fail-closed.
    _runtime_directory(generated / "keycloak/import", runtime_owner)
    provider_configtree = generated / "backend/configtree"
    _runtime_directory(provider_configtree, runtime_owner)
    _reset_provider_configtree(provider_configtree)
    _write(generated / "keycloak/overlay.json", json.dumps(overlay, indent=2, sort_keys=True) + "\n", private=False)
    _write(generated / "keycloak/desired-state.json", json.dumps(desired, indent=2, sort_keys=True) + "\n", private=False)
    secret_index = {
        "schemaVersion": "weave.keycloak-secretref-index.v1",
        "desiredStateRevision": desired["revision"],
        "entries": {key: str(context.secret_root / name) for key, name in sorted(SECRET_REF_PATHS.items()) if (context.secret_root / name).exists()},
    }
    _write(generated / "keycloak/secretref-index.json", json.dumps(secret_index, indent=2, sort_keys=True) + "\n", private=True)
    _write(generated / "caddy/Caddyfile", _caddy(context), private=False)
    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        _write(generated / "mas/config.yaml", _mas(context), private=True, runtime_owner=runtime_owner)
        _write(generated / "mas/signing.key", _read_secret(context, "mas-signing-key.pem") + "\n", private=True, runtime_owner=runtime_owner)
        _write(generated / "synapse/homeserver.yaml", _synapse(context), private=True)
        _write(generated / "synapse/appservice/registration.yaml", _appservice(context), private=True)
        _write(generated / "synapse/appservice/as-token", _read_secret(context, "matrix-appservice-as-token") + "\n", private=True)
        _write(generated / "synapse/appservice/hs-token", _read_secret(context, "matrix-appservice-hs-token") + "\n", private=True)
    if (
        context.env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav"
        or context.env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav"
    ):
        host_configtree = {
            "weave.nextcloud.files.actor-token": "nextcloud-actor-token",
            "weave.calendar.caldav.backend-token": "nextcloud-actor-token",
        }
        for property_name, secret_name in host_configtree.items():
            _write(
                generated / "backend/configtree" / property_name,
                _read_secret(context, secret_name) + "\n",
                private=True,
                runtime_owner=runtime_owner,
            )
    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        for target_name, secret_name in (
            ("matrix-as-token", "matrix-appservice-as-token"),
            ("matrix-hs-token", "matrix-appservice-hs-token"),
        ):
            _write(
                generated / "backend/configtree" / target_name,
                _read_secret(context, secret_name) + "\n",
                private=True,
                runtime_owner=runtime_owner,
            )
    _write(generated / "backend/public.env", _backend_env(context), private=False)
    if context.profile == "dev":
        _write(generated / "backend/host.env", _backend_env(context), private=False)
    _write(generated / "mcp/public.env", _mcp_env(context), private=False)
    if context.profile == "dev":
        _write(generated / "mcp/host.env", _mcp_env(context, host_dev=True), private=False)
    runtime_policy = {
        "schemaVersion": "weave.runtime-policy/v1",
        "profileTtlSeconds": 120,
        "workspace": {
            "revision": "workspace-revision:1",
            "manifestRefTemplate":
                "webdav-manifest://{organizationRef}/{personRef}/current",
            "runtimeStateStoreRefTemplate":
                "runtime-state://{organizationRef}/{personRef}/state",
        },
        "modelPolicy": {
            "allowedProviders": ["provider-neutral"],
            "allowedModels": ["model-default"],
            "fallback": [],
            "maximumContextTokens": 32768,
            "dataRegion": "eu",
        },
        "matrix": {
            "accountRefTemplate": "matrix-account://{personRef}",
            "homeserverRefTemplate": "matrix-homeserver://default",
            "credentialRefTemplate":
                "credentialref://weave/runtime/{cellRef}/matrix",
            "allowedRooms": [],
            "autoJoin": "off",
        },
        "mcp": {
            "servers": [
                {
                    "serverRef": "weave-mcp",
                    "endpoint": f"{context.env['WEAVE_API_ORIGIN']}/mcp",
                    "requestedResource": f"{context.env['WEAVE_API_ORIGIN']}/mcp",
                    "requiredScopes": ["files.read", "mcp.tools"],
                    "credentialRefTemplate":
                        "credentialref://weave/runtime/{cellRef}/{workloadClientId}/mcp",
                    "allowedToolClasses": ["files.read"],
                }
            ],
            "visibleToolClasses": ["files.read"],
        },
        "approvals": {
            "pluginRouting": {
                "enabled": True,
                "mode": "same-chat",
                "targetRefs": [],
            },
            "execMode": "ask",
            "persistentTrustPolicy": "bounded",
        },
        "sandbox": {
            "mode": "required",
            "networkPolicy": "allowlist",
            "allowedNetworkTargets": [
                urlsplit(context.env["WEAVE_API_ORIGIN"]).hostname
            ],
            "filesystemPolicy": "workspace-only",
            "approvedMountRefs": [],
        },
        "automation": {
            "heartbeatEnabled": False,
            "schedulePolicy": "disabled",
        },
    }
    _write(generated / "agent-runtime-policy.json", json.dumps(runtime_policy, indent=2, sort_keys=True) + "\n", private=False)
    manifest = {
        "schemaVersion": "weave.compose-render.v1",
        "profile": context.environment,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "specificationCommit": specification_commit,
        "baselineRevision": baseline_revision,
        "overlayRevision": overlay["revision"],
        "desiredStateRevision": desired["revision"],
        "keycloakImageDigest": overlay["imageDigest"],
        "containsSecretValues": False,
    }
    _write(generated / "render-manifest.json", json.dumps(manifest, indent=2, sort_keys=True) + "\n", private=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "dogfood", "prod", "e2e"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        render(context)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"WEAVE_RENDER_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"render: converged {args.profile} configuration (secret values withheld)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
