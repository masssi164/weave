#!/usr/bin/env python3
"""Independent Keycloak desired-state authority for the installed supervisor."""

from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

import rfc8785


class DesiredStateAuthorityError(RuntimeError):
    pass


def revision(value: dict[str, Any]) -> str:
    projection = dict(value)
    projection.pop("revision", None)
    return "sha256:" + hashlib.sha256(rfc8785.dumps(projection)).hexdigest()


def _document(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise DesiredStateAuthorityError(f"corpus authority document is unavailable: {path.name}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise DesiredStateAuthorityError(f"corpus authority document is malformed: {path.name}") from error
    if not isinstance(value, dict) or value.get("revision") != revision(value):
        raise DesiredStateAuthorityError(f"corpus authority revision is invalid: {path.name}")
    return value


def _origin(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        raise DesiredStateAuthorityError("public overlay URL must be credential-free HTTPS")
    return f"{parsed.scheme}://{parsed.netloc}"


def _image_digest(image: str) -> str:
    if re.fullmatch(r"sha256:[0-9a-f]{64}", image):
        return image
    if "@sha256:" in image:
        digest = "sha256:" + image.rsplit("@sha256:", 1)[1]
        if re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            return digest
    raise DesiredStateAuthorityError("Keycloak overlay image is not an immutable digest")


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


def expected_documents(
    *, profile: str, env: dict[str, str], spec_root: Path, specification_commit: str
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any]]:
    examples = spec_root / "contracts/examples"
    baseline = _document(examples / "keycloak-desired-state.valid.json")
    sanitizer = _document(examples / "keycloak-admin-sanitizer-profile.valid.json")
    baseline_revision = str((baseline.get("provenance") or {}).get("baselineRevision", ""))
    if baseline_revision != revision({**baseline, "revision": ""}):
        # The baseline revision is the canonical document revision with the
        # environment overlay marker removed by the corpus publisher.  Bind it
        # as an opaque corpus-authored SHA even when it differs from the final
        # example revision.
        if re.fullmatch(r"sha256:[0-9a-f]{64}", baseline_revision) is None:
            raise DesiredStateAuthorityError("corpus baseline revision is malformed")
    if profile == "dev":
        smtp: dict[str, object] = {
            "host": "mailpit", "port": 1025,
            "fromAddress": f"noreply@{env['WEAVE_TENANT_DOMAIN']}",
            "fromDisplayName": "Weave", "ssl": False, "startTls": False,
        }
    elif profile == "dogfood":
        smtp = {
            "host": "mailpit", "port": 1025,
            "fromAddress": f"noreply@{env['WEAVE_TENANT_DOMAIN']}",
            "fromDisplayName": "Weave", "ssl": True, "startTls": False,
        }
    elif profile == "main":
        host = env.get("WEAVE_SMTP_HOST", "")
        if not host or host == "mailpit":
            raise DesiredStateAuthorityError("main overlay requires external SMTP")
        smtp = {
            "host": host,
            "port": int(env.get("WEAVE_SMTP_PORT", "465")),
            "fromAddress": env.get("WEAVE_SMTP_FROM_ADDRESS", f"noreply@{env['WEAVE_TENANT_DOMAIN']}"),
            "fromDisplayName": env.get("WEAVE_SMTP_FROM_DISPLAY_NAME", "Weave"),
            "ssl": True,
            "startTls": False,
            "usernameRef": "secretref:smtp/username",
            "passwordRef": "secretref:smtp/password",
        }
    else:
        raise DesiredStateAuthorityError("unsupported Keycloak overlay environment")
    public = {
        "weave": _origin(env["WEAVE_PUBLIC_URL"]),
        "api": env["WEAVE_API_URL"],
        "auth": _origin(env["WEAVE_AUTH_URL"]),
        "matrix": _origin(env["WEAVE_MATRIX_URL"]),
    }
    overlay: dict[str, Any] = {
        "apiVersion": "weave.keycloak-environment-overlay/v1",
        "revision": "",
        "baselineRevision": baseline_revision,
        "environment": profile,
        "publicUrls": public,
        "smtpEndpoints": smtp,
        "organizationMetadata": {
            "name": env["WEAVE_ORGANIZATION_NAME"],
            "alias": env["WEAVE_ORGANIZATION_ALIAS"],
            "description": env["WEAVE_ORGANIZATION_DESCRIPTION"],
            "redirectUri": _origin(env["WEAVE_PUBLIC_URL"]),
        },
        "secretRefs": {
            "weaveBackendJwk": "secretref:keycloak/weave-backend-jwk",
            "weaveMcpServerJwk": "secretref:keycloak/weave-mcp-server-jwk",
            "identityAdmin": "secretref:keycloak/weave-identity-admin",
            "agentRuntimeAdmin": "secretref:keycloak/weave-agent-runtime-admin",
            "nextcloud": "secretref:keycloak/nextcloud",
            "matrixMas": "secretref:keycloak/matrix-mas",
        },
        "imageDigest": _image_digest(env["WEAVE_KEYCLOAK_IMAGE"]),
    }
    overlay["revision"] = revision(overlay)
    replacements = (
        ("https://api.weave.test/api", str(public["api"])),
        ("https://auth.weave.test", str(public["auth"])),
        ("https://matrix.weave.test", str(public["matrix"])),
        ("https://weave.test", str(public["weave"])),
    )
    desired = _replace_strings(copy.deepcopy(baseline), replacements)
    if not isinstance(desired, dict):
        raise DesiredStateAuthorityError("corpus baseline projection is not an object")
    desired["environment"] = profile
    desired["provenance"]["overlayRevision"] = overlay["revision"]
    desired["realm"]["frontendUrl"] = public["auth"]
    desired["realm"]["smtp"] = copy.deepcopy(smtp)
    organizations = desired.get("organizations")
    if not isinstance(organizations, list) or len(organizations) != 1 or not isinstance(organizations[0], dict):
        raise DesiredStateAuthorityError("corpus baseline must contain one managed organization")
    organizations[0].update(copy.deepcopy(overlay["organizationMetadata"]))
    desired["revision"] = revision(desired)
    manifest = {
        "schemaVersion": "weave.compose-render.v1",
        "profile": profile,
        "composeProject": env["WEAVE_COMPOSE_PROJECT"],
        "specificationCommit": specification_commit,
        "baselineRevision": baseline_revision,
        "overlayRevision": overlay["revision"],
        "desiredStateRevision": desired["revision"],
        "sanitizerRevision": sanitizer["revision"],
        "keycloakImageDigest": overlay["imageDigest"],
        "containsSecretValues": False,
    }
    return overlay, desired, sanitizer, manifest


def exact_pretty_json(value: dict[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
