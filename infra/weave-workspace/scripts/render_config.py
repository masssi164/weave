#!/usr/bin/env python3
"""Orchestrate deterministic, support-safe environment rendering."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlsplit

KEYCLOAK_MODULE_ROOT = Path(__file__).resolve().parents[1] / "keycloak"
if str(KEYCLOAK_MODULE_ROOT) not in sys.path:
    sys.path.insert(0, str(KEYCLOAK_MODULE_ROOT))

from compose_env import ComposeContext, ContractError, load_context
from realm_renderer import RealmProjectionError
from rendering.gateway import render_caddy
from rendering.io import read_secret, runtime_directory, write
from rendering.keycloak import render_keycloak
from rendering.providers import render_appservice, render_mas, render_synapse

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
    "keycloak-matrix-mas",
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


def _reset_provider_configtree(path: Path) -> None:
    if path.is_symlink() or not path.is_dir():
        raise ContractError(f"provider configtree is not a regular directory: {path}")
    entries = tuple(path.iterdir())
    unknown = sorted(entry.name for entry in entries if entry.name not in PROVIDER_CONFIGTREE_FILES)
    if unknown:
        raise ContractError("provider configtree contains unmanaged entries: " + ", ".join(unknown))
    for entry in entries:
        if entry.is_symlink() or not entry.is_file():
            raise ContractError(f"provider configtree entry is not a regular file: {entry}")
        entry.unlink()


def _render_provider_secrets(context: ComposeContext, runtime_owner: tuple[int, int]) -> None:
    provider_configtree = context.generated_root / "backend/configtree"
    runtime_directory(provider_configtree, runtime_owner)
    retired_identity_secret = (
        provider_configtree
        / "spring.security.oauth2.client.registration.weave-identity-admin.client-secret"
    )
    if (
        context.environment != "prod"
        and (retired_identity_secret.exists() or retired_identity_secret.is_symlink())
    ):
        if not retired_identity_secret.is_symlink() and not retired_identity_secret.is_file():
            raise ContractError("retired identity provider configtree entry is not a file")
        retired_identity_secret.unlink()
    _reset_provider_configtree(provider_configtree)

    if context.env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav" or context.env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav":
        for property_name in (
            "weave.nextcloud.files.actor-token",
            "weave.calendar.caldav.backend-token",
        ):
            write(
                provider_configtree / property_name,
                read_secret(context, "nextcloud-actor-token") + "\n",
                private=True,
                runtime_owner=runtime_owner,
            )

    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        for target_name, secret_name in (
            ("matrix-as-token", "matrix-appservice-as-token"),
            ("matrix-hs-token", "matrix-appservice-hs-token"),
        ):
            write(
                provider_configtree / target_name,
                read_secret(context, secret_name) + "\n",
                private=True,
                runtime_owner=runtime_owner,
            )


def _runtime_policy(context: ComposeContext) -> dict[str, object]:
    api_host = urlsplit(context.env["WEAVE_API_URL"]).hostname
    if api_host is None:
        raise ContractError("WEAVE_API_URL must contain a DNS host")
    return {
        "schemaVersion": "weave.runtime-policy/v1",
        "profileTtlSeconds": 120,
        "workspace": {
            "revision": "workspace-revision:1",
            "manifestRefTemplate": "webdav-manifest://{organizationRef}/{personRef}/current",
            "runtimeStateStoreRefTemplate": "runtime-state://{organizationRef}/{personRef}/state",
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
            "credentialRefTemplate": "credentialref://weave/runtime/{cellRef}/matrix",
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
                    "credentialRefTemplate": "credentialref://weave/runtime/{cellRef}/{workloadClientId}/mcp",
                    "allowedToolClasses": ["files.read"],
                }
            ],
            "visibleToolClasses": ["files.read"],
        },
        "approvals": {
            "pluginRouting": {"enabled": True, "mode": "same-chat", "targetRefs": []},
            "execMode": "ask",
            "persistentTrustPolicy": "bounded",
        },
        "sandbox": {
            "mode": "required",
            "networkPolicy": "allowlist",
            "allowedNetworkTargets": [api_host],
            "filesystemPolicy": "workspace-only",
            "approvedMountRefs": [],
        },
        "automation": {"heartbeatEnabled": False, "schedulePolicy": "disabled"},
    }


def render(context: ComposeContext) -> None:
    for name in REQUIRED_PRIVATE_FILES:
        read_secret(context, name)
    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        for name in MATRIX_PRIVATE_FILES:
            read_secret(context, name)
    if context.env["WEAVE_FILES_PROVIDER"] == "nextcloud-webdav" or context.env["WEAVE_CALENDAR_PROVIDER"] == "nextcloud-caldav":
        for name in NEXTCLOUD_PRIVATE_FILES:
            read_secret(context, name)
    if "storage-s3" in context.active_profiles:
        for name in S3_PRIVATE_FILES:
            read_secret(context, name)
    if context.environment == "prod":
        read_secret(context, "smtp-password")

    generated = context.generated_root
    runtime_owner = (int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"]))
    runtime_directory(generated / "schema-init", runtime_owner)

    keycloak = render_keycloak(context)
    _render_provider_secrets(context, runtime_owner)
    write(generated / "caddy/Caddyfile", render_caddy(context), private=False)

    if context.env["WEAVE_CHAT_PROVIDER"] == "matrix-synapse":
        write(generated / "mas/config.yaml", render_mas(context), private=True, runtime_owner=runtime_owner)
        write(generated / "mas/signing.key", read_secret(context, "mas-signing-key.pem") + "\n", private=True, runtime_owner=runtime_owner)
        write(generated / "synapse/homeserver.yaml", render_synapse(context), private=True)
        write(generated / "synapse/appservice/registration.yaml", render_appservice(context), private=True)
        write(generated / "synapse/appservice/as-token", read_secret(context, "matrix-appservice-as-token") + "\n", private=True)
        write(generated / "synapse/appservice/hs-token", read_secret(context, "matrix-appservice-hs-token") + "\n", private=True)

    write(generated / "agent-runtime-policy.json", json.dumps(_runtime_policy(context), indent=2, sort_keys=True) + "\n", private=False)

    manifest = {
        "schemaVersion": "weave.compose-render.v3",
        "profile": context.environment,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "specificationCommit": keycloak["specificationCommit"],
        "baselineRevision": keycloak["baselineRevision"],
        "realmIdentity": keycloak["realmIdentity"],
        "deploymentArtifacts": {
            "renderedRealmPath": "keycloak/import/weave-realm.json",
            "migrationBundleDigest": keycloak["migrationBundleDigest"],
            "migrationBundlePath": "keycloak/migrations/fresh-start-v1.json",
            "environmentRenderEvidencePath": "keycloak/realm-render-evidence.json",
            "containsSecretValues": False,
        },
        "containsSecretValues": False,
    }
    write(generated / "render-manifest.json", json.dumps(manifest, indent=2, sort_keys=True) + "\n", private=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "dogfood", "prod", "e2e"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    try:
        render(load_context(args.profile, args.root, args.env_file))
    except (ContractError, RealmProjectionError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"WEAVE_RENDER_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"render: converged {args.profile} configuration (secret values withheld)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
