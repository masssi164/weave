from __future__ import annotations

import copy
import json
import re
import stat
from pathlib import Path
from urllib.parse import urlsplit

from compose_env import ComposeContext, ContractError, assert_revision, revision, specification_context
from realm_renderer import MACHINE_KEY_PROJECTIONS, fresh_start_migration_bundle, pretty_json, project_realm, sha256_digest, validate_public_jwks
from rendering.io import json_object, read_secret, runtime_directory, write

SECRET_REF_PATHS = {
    "secretref:keycloak/weave-backend-jwk": "keycloak-weave-backend-jwk.json",
    "secretref:keycloak/weave-mcp-server-jwk": "keycloak-weave-mcp-server-jwk.json",
    "secretref:keycloak/weave-identity-admin-jwk": "keycloak-weave-identity-admin-jwk.json",
    "secretref:keycloak/weave-agent-runtime-admin-jwk": "agent-runtime/workloads/weave/keycloak/weave-agent-runtime-admin",
    "secretref:smtp/password": "smtp-password",
}


def _origin(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise ContractError(f"expected HTTPS public URL: {value}")
    return f"{parsed.scheme}://{parsed.netloc}"


def _image_digest(context: ComposeContext) -> str:
    image = context.env["WEAVE_KEYCLOAK_IMAGE"]
    match = re.fullmatch(r"[A-Za-z0-9._-]+(?::[0-9]+)?/[A-Za-z0-9._/-]+@(sha256:[0-9a-f]{64})", image)
    if match:
        return match.group(1)
    if context.environment == "e2e" and context.isolated_namespace is not None and re.fullmatch(r"sha256:[0-9a-f]{64}", image):
        return image
    raise ContractError(f"{context.profile} render requires one immutable downstream Keycloak OCI digest")


def _overlay(context: ComposeContext, baseline_revision: str) -> dict[str, object]:
    profile = context.environment
    if profile == "dev":
        smtp: dict[str, object] = {"host":"mailpit","port":1025,"fromAddress":f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}","fromDisplayName":"Weave","ssl":False,"startTls":False}
    elif profile in {"dogfood", "e2e"}:
        if context.env.get("WEAVE_MAILPIT_REQUIRE_TLS", "").lower() != "true":
            raise ContractError(f"{profile} SMTP requires Mailpit implicit TLS")
        smtp = {"host":"mailpit","port":1025,"fromAddress":f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}","fromDisplayName":"Weave","ssl":True,"startTls":False}
    elif profile == "prod":
        host = context.env.get("WEAVE_SMTP_HOST", "")
        username = context.env.get("WEAVE_SMTP_USERNAME", "")
        if not host or host == "mailpit" or not username:
            raise ContractError("prod requires external SMTP host and username")
        smtp = {"host":host,"port":int(context.env.get("WEAVE_SMTP_PORT", "465")),"fromAddress":context.env.get("WEAVE_SMTP_FROM_ADDRESS", f"noreply@{context.env['WEAVE_TENANT_DOMAIN']}"),"fromDisplayName":context.env.get("WEAVE_SMTP_FROM_DISPLAY_NAME", "Weave"),"ssl":True,"startTls":False,"username":username,"passwordVaultRef":"${vault.smtp-password}"}
    else:
        raise ContractError(f"unsupported render environment: {profile}")
    value: dict[str, object] = {
        "apiVersion": "weave.keycloak-environment-overlay/v3",
        "revision": "",
        "baselineRevision": baseline_revision,
        "environment": profile,
        "publicUrls": {"weave": _origin(context.env["WEAVE_PUBLIC_URL"]), "api": context.env["WEAVE_API_URL"], "auth": _origin(context.env["WEAVE_AUTH_URL"])},
        "smtpEndpoints": smtp,
        "organizationMetadata": {"name":context.env["WEAVE_ORGANIZATION_NAME"],"alias":context.env["WEAVE_ORGANIZATION_ALIAS"],"description":context.env["WEAVE_ORGANIZATION_DESCRIPTION"],"redirectUri":_origin(context.env["WEAVE_PUBLIC_URL"])},
        "secretRefs": {"weaveBackendJwk":"secretref:keycloak/weave-backend-jwk","weaveMcpServerJwk":"secretref:keycloak/weave-mcp-server-jwk","identityAdmin":"secretref:keycloak/weave-identity-admin-jwk","agentRuntimeAdmin":"secretref:keycloak/weave-agent-runtime-admin-jwk"},
        "imageDigest": _image_digest(context),
    }
    value["revision"] = revision(value)
    return value


def _replace_strings(value: object, replacements: tuple[tuple[str, str], ...]) -> object:
    if isinstance(value, str):
        for source, target in replacements:
            value = value.replace(source, target)
        return value
    if isinstance(value, list):
        return [_replace_strings(item, replacements) for item in value]
    if isinstance(value, dict):
        return {key: _replace_strings(item, replacements) for key, item in value.items()}
    return value


def _desired(baseline: dict[str, object], overlay: dict[str, object]) -> dict[str, object]:
    desired = copy.deepcopy(baseline)
    public = overlay["publicUrls"]
    assert isinstance(public, dict)
    desired = _replace_strings(desired, (("https://api.weave.test/mcp", f"{_origin(str(public['api']))}/mcp"),("https://api.weave.test/api", str(public["api"])),("https://auth.weave.test", str(public["auth"])),("https://weave.test", str(public["weave"]))))
    assert isinstance(desired, dict)
    if desired.get("apiVersion") != "weave.keycloak-desired-state/v3" or desired.get("keycloakVersion") != "26.7.1":
        raise ContractError("canonical Keycloak desired-state v3/26.7.1 required")
    if "groups" in desired:
        raise ContractError("legacy human realm groups are forbidden")
    groups = desired.get("organizationGroups")
    if not isinstance(groups, list):
        raise ContractError("native organizationGroups required")
    paths = {group.get("path") for group in groups if isinstance(group, dict)}
    required = {"/owners","/admins","/members","/guests","/capabilities","/capabilities/weaver"}
    if paths != required:
        raise ContractError("canonical organization group topology mismatch")
    realm = desired.get("realm")
    if not isinstance(realm, dict) or realm.get("adminPermissionsEnabled") is not True:
        raise ContractError("Keycloak admin permissions must be enabled")
    desired["environment"] = overlay["environment"]
    provenance = desired["provenance"]
    assert isinstance(provenance, dict)
    provenance["overlayRevision"] = overlay["revision"]
    realm["frontendUrl"] = public["auth"]
    smtp = overlay["smtpEndpoints"]
    assert isinstance(smtp, dict)
    realm["smtpServer"] = {k:v for k,v in smtp.items() if k != "passwordVaultRef"}
    if smtp.get("passwordVaultRef"):
        realm["smtpServer"]["password"] = smtp["passwordVaultRef"]
    organizations = desired.get("organizations")
    if not isinstance(organizations, list) or len(organizations) != 1 or not isinstance(organizations[0], dict):
        raise ContractError("exactly one bootstrap organization required")
    metadata = overlay["organizationMetadata"]
    assert isinstance(metadata, dict)
    organizations[0].update({"name":metadata["name"],"alias":metadata["alias"],"description":metadata["description"],"redirectUrl":metadata["redirectUri"]})
    desired["revision"] = revision(desired)
    return desired


def render_keycloak(context: ComposeContext) -> dict[str, object]:
    corpus_root, specification_commit = specification_context(context)
    baseline_path = corpus_root / "contracts/examples/keycloak-desired-state.valid.json"
    baseline = json_object(baseline_path)
    assert_revision(baseline, baseline_path)
    baseline_revision = str(baseline["provenance"]["baselineRevision"])
    overlay = _overlay(context, baseline_revision)
    desired = _desired(baseline, overlay)
    public_keys: dict[str, dict[str, object]] = {}
    for secret_ref, (_private_name, public_name) in MACHINE_KEY_PROJECTIONS.items():
        path = context.generated_root / "keycloak/public-jwks" / public_name
        if path.is_symlink() or not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o644:
            raise ContractError(f"public JWKS projection is unavailable: {path}")
        public_keys[secret_ref] = validate_public_jwks(json_object(path), owner=secret_ref)
    realm_payload = pretty_json(project_realm(desired, public_keys))
    rendered_digest = sha256_digest(realm_payload)
    migration_payload = pretty_json(fresh_start_migration_bundle(desired, rendered_digest))
    migration_digest = sha256_digest(migration_payload)
    generated = context.generated_root
    runtime_owner = (int(context.env["WEAVE_RUNTIME_UID"]), int(context.env["WEAVE_RUNTIME_GID"]))
    runtime_directory(generated / "keycloak/import", runtime_owner)
    write(generated / "keycloak/overlay.json", json.dumps(overlay, indent=2, sort_keys=True)+"\n", private=False)
    write(generated / "keycloak/desired-state.json", json.dumps(desired, indent=2, sort_keys=True)+"\n", private=False)
    write(generated / "keycloak/import/weave-realm.json", realm_payload, private=False)
    write(generated / "keycloak/migrations/fresh-start-v1.json", migration_payload, private=False)
    secret_index = {"schemaVersion":"weave.keycloak-secretref-index.v1","desiredStateRevision":desired["revision"],"entries":{key:str(context.secret_root/name) for key,name in sorted(SECRET_REF_PATHS.items()) if (context.secret_root/name).exists()}}
    write(generated / "keycloak/secretref-index.json", json.dumps(secret_index, indent=2, sort_keys=True)+"\n", private=True)
    return {"specificationCommit":specification_commit,"baselineRevision":baseline_revision,"overlay":overlay,"desired":desired,"renderedRealmDigest":rendered_digest,"migrationBundleDigest":migration_digest}
