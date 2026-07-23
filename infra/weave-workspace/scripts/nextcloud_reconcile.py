#!/usr/bin/env python3
"""Idempotently prepare the Nextcloud Files/Calendar/CardDAV provider boundary."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

from compose_env import ComposeContext, ContractError, compose_environment, load_context


APPS = {
    "user_oidc": (
        "8.10.1",
        "https://github.com/nextcloud-releases/user_oidc/releases/download/v8.10.1/user_oidc-v8.10.1.tar.gz",
        "49ced1fe192302f4540b869438b6ccb9ca0d69b717b76ed7075a70aa5cf666fd",
    ),
    "calendar": (
        "5.5.21",
        "https://github.com/nextcloud-releases/calendar/releases/download/v5.5.21/calendar-v5.5.21.tar.gz",
        "432aae725159becc40af397d918c4fbf4e85bc024ee9118997f6efae74cd7c8d",
    ),
    "contacts": (
        "7.3.18",
        "https://github.com/nextcloud-releases/contacts/releases/download/v7.3.18/contacts-v7.3.18.tar.gz",
        "4c7d404b3b572d026c0c20b0bdc0bf2babbab3ee877b565d357825e8956ba546",
    ),
}


def _compose(context: ComposeContext, *arguments: str) -> None:
    subprocess.run(
        [*context.compose_base_command, *arguments],
        cwd=context.root,
        env=compose_environment(context),
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def _exec(
    context: ComposeContext,
    *arguments: str,
    user: str = "www-data",
    input_bytes: bytes | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    command = ["docker", "exec"]
    if input_bytes is not None:
        command.append("--interactive")
    command.extend(("--user", user, f"{context.env['WEAVE_RESOURCE_PREFIX']}-nextcloud", *arguments))
    result = subprocess.run(command, input=input_bytes, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and result.returncode != 0:
        raise ContractError("Nextcloud rejected a support-safe provider reconciliation operation")
    return result


def _occ(context: ComposeContext, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    return _exec(context, "php", "occ", *arguments, check=check)


def _read_secret(path: Path) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ContractError(f"provider SecretRef is missing or not mode-0600: {path.name}")
    value = path.read_bytes().strip()
    if not value or b"\x00" in value or b"\n" in value or b"\r" in value:
        raise ContractError(f"provider SecretRef has an invalid single-line value: {path.name}")
    return value


def _wait(context: ComposeContext) -> None:
    for _ in range(120):
        result = _occ(context, "status", "--output=json", check=False)
        if result.returncode == 0:
            try:
                value = json.loads(result.stdout)
            except json.JSONDecodeError:
                value = {}
            if value.get("installed") is True and value.get("maintenance") is False:
                return
        time.sleep(1)
    raise ContractError("Nextcloud did not become installed and maintenance-free")


def _install_app(context: ComposeContext, app: str, version: str, url: str, digest: str) -> None:
    existing = _occ(context, "app:getpath", app, check=False)
    if existing.returncode != 0:
        script = r'''set -eu
app="$1"; version="$2"; url="$3"; digest="$4"
archive="/tmp/${app}-${version}.tgz"
curl -fsSL "$url" -o "$archive"
printf '%s  %s\n' "$digest" "$archive" | sha256sum -c - >/dev/null
tar -xzf "$archive" -C /var/www/html/custom_apps
chown -R www-data:www-data "/var/www/html/custom_apps/${app}"
rm "$archive"
'''
        _exec(context, "/bin/sh", "-euc", script, "sh", app, version, url, digest, user="0")
    _occ(context, "app:enable", app)
    listing = json.loads(_occ(context, "app:list", "--output=json").stdout)
    observed = (listing.get("enabled") or {}).get(app)
    if observed != version:
        raise ContractError(f"Nextcloud app {app} is {observed!r}; pinned provider contract requires {version}")


def _configure_ca(context: ComposeContext) -> None:
    _exec(
        context,
        "/bin/sh", "-euc",
        "install -m 0644 /certs/ca.pem /usr/local/share/ca-certificates/weave-compose-ca.crt && update-ca-certificates >/dev/null",
        user="0",
    )


def _boolean_output(result: subprocess.CompletedProcess[bytes]) -> bool | None:
    if result.returncode != 0:
        return None
    value = result.stdout.strip().lower()
    if value in {b"1", b"true", b"yes"}:
        return True
    if value in {b"0", b"false", b"no", b""}:
        return False
    return None


def _managed_oidc_projection(
    context: ComposeContext, secret_fingerprint: str
) -> dict[str, object] | None:
    result = _occ(context, "user_oidc:provider", "keycloak", "--output=json", check=False)
    if result.returncode != 0:
        return None
    try:
        provider = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise ContractError("Nextcloud OIDC managed projection is malformed") from error
    settings = provider.get("settings") if isinstance(provider, dict) else None
    if not isinstance(provider, dict) or not isinstance(settings, dict):
        raise ContractError("Nextcloud OIDC managed settings projection is unavailable")
    return {
        "identifier": provider.get("identifier"),
        "clientId": provider.get("clientId"),
        "discoveryEndpoint": provider.get("discoveryEndpoint"),
        "scope": provider.get("scope"),
        "groupProvisioning": settings.get("groupProvisioning"),
        "checkBearer": settings.get("checkBearer"),
        "bearerProvisioning": settings.get("bearerProvisioning"),
        "allowLocalRemoteServers": _boolean_output(
            _occ(context, "config:system:get", "allow_local_remote_servers", check=False)
        ),
        "oidcProviderBearerValidation": _boolean_output(
            _occ(
                context,
                "config:system:get",
                "user_oidc",
                "oidc_provider_bearer_validation",
                check=False,
            )
        ),
        "allowInsecureHttp": _boolean_output(
            _occ(context, "config:app:get", "user_oidc", "allow_insecure_http", check=False)
        ),
        "clientSecretFingerprint": secret_fingerprint,
    }


def _configure_oidc(context: ComposeContext) -> tuple[int, str]:
    client_secret = _read_secret(context.secret_root / "keycloak-nextcloud")
    php = (
        'require_once "/var/www/html/lib/base.php"; '
        'try { $m=OC::$server->get(OCA\\UserOIDC\\Db\\ProviderMapper::class); '
        '$c=OC::$server->get(OCP\\Security\\ICrypto::class); '
        '$p=$m->findProviderByIdentifier("keycloak"); } '
        'catch (OCP\\AppFramework\\Db\\DoesNotExistException $e) { exit(44); } '
        'if ($p->getClientId() !== "nextcloud") { exit(45); } '
        'echo hash("sha256", $c->decrypt($p->getClientSecret()));'
    )
    observed = _exec(context, "php", "-r", php, check=False)
    expected_fingerprint = hashlib.sha256(client_secret).hexdigest().encode("ascii")
    if observed.returncode not in (0, 44):
        raise ContractError("Nextcloud OIDC provider identity/credential proof failed")
    if observed.returncode == 0 and observed.stdout.strip() != expected_fingerprint:
        raise ContractError(
            "existing Nextcloud OIDC client secret differs from the selected SecretRef; "
            "ordinary reconciliation refuses an implicit credential rotation"
        )
    created = observed.returncode == 44
    issuer = context.env["WEAVE_AUTH_URL"] + "/realms/weave"
    secret_fingerprint = "sha256:" + expected_fingerprint.decode("ascii")
    expected_projection: dict[str, object] = {
        "identifier": "keycloak",
        "clientId": "nextcloud",
        "discoveryEndpoint": issuer + "/.well-known/openid-configuration",
        "scope": "openid email profile",
        "groupProvisioning": True,
        "checkBearer": True,
        "bearerProvisioning": True,
        "allowLocalRemoteServers": True,
        "oidcProviderBearerValidation": True,
        "allowInsecureHttp": False,
        "clientSecretFingerprint": secret_fingerprint,
    }
    current = None if created else _managed_oidc_projection(context, secret_fingerprint)
    provider_keys = {
        "identifier", "clientId", "discoveryEndpoint", "scope",
        "groupProvisioning", "checkBearer", "bearerProvisioning", "clientSecretFingerprint",
    }
    provider_drift = created or current is None or any(
        current.get(key) != expected_projection[key] for key in provider_keys
    )
    mutations = 0
    script = r'''set -eu
IFS= read -r client_secret
exec php occ user_oidc:provider keycloak \
  --clientid=nextcloud \
  --clientsecret="$client_secret" \
  --discoveryuri="$1/.well-known/openid-configuration" \
  --group-provisioning=1 --check-bearer=1 --bearer-provisioning=1
'''
    if current is None or current.get("allowLocalRemoteServers") is not True:
        _occ(context, "config:system:set", "allow_local_remote_servers", "--type=bool", "--value=true")
        mutations += 1
    if current is None or current.get("oidcProviderBearerValidation") is not True:
        _occ(context, "config:system:set", "user_oidc", "oidc_provider_bearer_validation", "--type=boolean", "--value=true")
        mutations += 1
    if current is None or current.get("allowInsecureHttp") is not False:
        _occ(context, "config:app:set", "--type=boolean", "--value=0", "user_oidc", "allow_insecure_http")
        mutations += 1
    if provider_drift:
        _exec(context, "/bin/sh", "-euc", script, "sh", issuer, input_bytes=client_secret + b"\n")
        mutations += 1
    verified = _managed_oidc_projection(context, secret_fingerprint)
    if verified != expected_projection:
        raise ContractError("Nextcloud OIDC managed projection did not converge exactly")
    digest = "sha256:" + hashlib.sha256(
        json.dumps(verified, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    return mutations, digest


def _configure_actor(context: ComposeContext) -> tuple[str, list[str], int]:
    username = context.env["WEAVE_NEXTCLOUD_ACTOR_USERNAME"]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.@-]{1,63}", username):
        raise ContractError("Nextcloud actor username is outside the closed contract")
    credential = _read_secret(context.secret_root / "nextcloud-actor-token")
    exists = _occ(context, "user:info", username, check=False).returncode == 0
    script = r'''set -eu
IFS= read -r OC_PASS
export OC_PASS
exec php occ "$@"
'''
    if exists:
        status = _dav_probe(context, username, f"/remote.php/dav/files/{username}/")
        if status not in (200, 207):
            raise ContractError(
                "existing Nextcloud actor credential differs from the selected SecretRef; "
                "ordinary reconciliation refuses an implicit rotation—run the reviewed credential rotation/adoption operation"
            )
    else:
        _exec(
            context,
            "/bin/sh", "-euc", script, "sh", "user:add", "--password-from-env",
            "--display-name=Weave Backend Service Account", username,
            input_bytes=credential + b"\n",
        )
    suffix = "" if context.isolated_namespace is None else "-" + context.isolated_namespace
    calendars = [f"weave-workspace{suffix}", "weave-team-engineering", "weave-channel-engineering-general"]
    for calendar in calendars:
        result = _occ(context, "dav:create-calendar", username, calendar, check=False)
        if result.returncode != 0 and not re.search(rb"already exists|calendar.*exists|duplicate", result.stdout + result.stderr, re.I):
            raise ContractError("Nextcloud could not converge a canonical Calendar collection")
    addressbook = _occ(context, "dav:create-addressbook", username, "weave-contacts", check=False)
    if addressbook.returncode != 0 and not re.search(rb"already exists|addressbook.*exists|duplicate", addressbook.stdout + addressbook.stderr, re.I):
        raise ContractError("Nextcloud could not converge the canonical CardDAV collection")
    return username, calendars, int(not exists)


def _dav_probe(context: ComposeContext, username: str, target: str) -> int:
    credential = _read_secret(context.secret_root / "nextcloud-actor-token")
    config = (
        f'user = "{username}:{credential.decode("utf-8")}"\n'
        f'url = "http://127.0.0.1{target}"\n'
        'request = "PROPFIND"\nheader = "Depth: 0"\n'
        'silent\nshow-error\noutput = "/dev/null"\nwrite-out = "%{http_code}"\n'
    ).encode("utf-8")
    result = _exec(context, "curl", "--config", "-", input_bytes=config)
    if credential in result.stdout + result.stderr:
        raise ContractError("provider credential reached DAV probe output")
    try:
        return int(result.stdout.decode("ascii"))
    except ValueError as error:
        raise ContractError("Nextcloud DAV probe did not return an HTTP status") from error


def reconcile(context: ComposeContext) -> dict[str, object]:
    _wait(context)
    _configure_ca(context)
    for app, (version, url, digest) in APPS.items():
        _install_app(context, app, version, url, digest)
    oidc_mutations, oidc_projection_digest = _configure_oidc(context)
    username, calendars, actor_mutations = _configure_actor(context)
    webdav = _dav_probe(context, username, f"/remote.php/dav/files/{username}/")
    caldav = _dav_probe(context, username, f"/remote.php/dav/calendars/{username}/{calendars[0]}/")
    carddav = _dav_probe(context, username, f"/remote.php/dav/addressbooks/users/{username}/weave-contacts/")
    if any(value not in (200, 207) for value in (webdav, caldav, carddav)):
        raise ContractError("authenticated Nextcloud DAV provider readiness failed")
    return {
        "schemaVersion": "weave.nextcloud-provider-readiness.v1",
        "profile": context.profile,
        "composeProject": context.env["WEAVE_COMPOSE_PROJECT"],
        "checkedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "apps": {name: version for name, (version, _url, _digest) in sorted(APPS.items())},
        "collections": {"calendars": calendars, "addressBook": "weave-contacts"},
        "webdavStatus": webdav,
        "caldavStatus": caldav,
        "carddavStatus": carddav,
        "actorCredentialFingerprint": "sha256:" + hashlib.sha256(
            _read_secret(context.secret_root / "nextcloud-actor-token")
        ).hexdigest(),
        "credentialMutationCount": oidc_mutations + actor_mutations,
        "oidcManagedProjectionDigest": oidc_projection_digest,
        "containsSecretValues": False,
        "supportSafe": True,
        "ready": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=("dev", "dogfood", "main"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--env-file")
    args = parser.parse_args()
    try:
        context = load_context(args.profile, args.root, args.env_file)
        # OIDC discovery is deliberately performed through the declared public
        # HTTPS issuer.  Starting Caddy also starts and waits for its complete
        # provider dependency tier, so no internal issuer alias can drift from
        # the member-visible authority.
        _compose(context, "up", "--detach", "--wait", "caddy")
        value = reconcile(context)
        output = context.generated_root / "nextcloud/readiness.json"
        output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = output.with_suffix(".tmp")
        temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.chmod(temporary, 0o600)
        os.replace(temporary, output)
    except (ContractError, OSError, ValueError, KeyError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"WEAVE_NEXTCLOUD_RECONCILE_ERROR {error}", file=os.sys.stderr)
        return 1
    print(f"nextcloud-reconcile: provider ready; evidence={output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
