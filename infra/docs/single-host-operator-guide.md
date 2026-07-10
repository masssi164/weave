# Single-host operator guide

This guide describes the first non-local Weave deployment path: one Linux host, public DNS, trusted HTTPS, Docker Engine, OpenTofu, explicit operator-managed secrets, persistent volumes, and a verification workflow.

It is intentionally narrower than a later high-availability, Kubernetes, or managed-SaaS story.

## Target shape

The host runs:

- Caddy as the public HTTPS entry point.
- Keycloak for OIDC identity.
- Matrix Synapse plus Matrix Authentication Service.
- Nextcloud for files/calendar storage foundations.
- Weave backend as the product API/BFF.
- PostgreSQL with separate per-service databases.
- Optional provider runtimes only when their gates are explicitly configured.

## Public contract

Expose these HTTPS origins:

- `https://<tenant_domain>` for the Weave product gateway plus `/files` and `/calendar` product routes.
- `https://api.<tenant_domain>/api` for the canonical Weave backend API.
- `https://auth.<tenant_domain>` for Keycloak.
- `https://matrix.<tenant_domain>` for Matrix/Synapse/MAS.
- `https://files.<tenant_domain>` as the raw Nextcloud technical/admin/protocol fallback.

Example shape:

- `https://weave.example`
- `https://api.weave.example/api`
- `https://auth.weave.example`
- `https://matrix.weave.example`
- `https://files.weave.example`

Do not expose older public aliases for Keycloak, Nextcloud, or gateway API routes. Keep backend, app, Caddy, and operator docs aligned to the canonical contract above.

## Required operator inputs

Set these OpenTofu-compatible `TF_VAR_*` inputs before the first apply:

- `TF_VAR_tenant_domain`
- `TF_VAR_auth_subdomain`
- `TF_VAR_api_subdomain`
- `TF_VAR_matrix_subdomain`
- `TF_VAR_nextcloud_subdomain`
- `TF_VAR_public_scheme=https`
- `TF_VAR_caddy_tls_cert_file`
- `TF_VAR_caddy_tls_key_file`
- `TF_VAR_caddy_tls_ca_file` only when using a private CA
- `TF_VAR_weave_backend_image`
- `TF_VAR_nextcloud_backend_actor_username`
- `TF_VAR_nextcloud_backend_actor_token`
- all admin, database, MAS, Keycloak, and backend secrets consumed by `install.sh`

Start from `weave-workspace/release.env.example`, copy it to a local untracked file, then replace every placeholder. Do not commit filled-in env files.

## Calendar boundary

The current Calendar facade uses the backend actor's own `personal` CalDAV collection as the temporary Weave-managed workspace calendar fallback while team/channel scopes are implemented.

Do not configure private personal calendar paths unless a later contract explicitly provisions sharing, delegated user access, revocation, and support-safe diagnostics.

## TLS source

Use publicly trusted certificates, for example Let's Encrypt or a certificate issued by your edge platform. Do not rely on the generated local CA outside development.

Recommended pattern:

1. Issue a SAN certificate for the five canonical hostnames.
2. Place the cert and key on the host with restrictive permissions.
3. Set `TF_VAR_caddy_tls_cert_file` and `TF_VAR_caddy_tls_key_file` to absolute paths.
4. Leave `TF_VAR_caddy_tls_ca_file` unset unless your issuer is private and clients must trust an extra CA.

## Image source

Pin runtime images instead of relying on floating local defaults:

- Pin `TF_VAR_weave_backend_image` to a version or immutable digest.
- Pin OpenTofu-managed service images when module variables expose them.
- Keep `TF_VAR_mas_image` on the default unless an override was validated against generated `synapse_modern` config and localpart-conflict policy.
- Keep `TF_VAR_synapse_image` on Synapse 1.136.0 or later so MAS delegated auth can call the homeserver MAS API.
- Record the chosen image set in the deployment change or release note.

## Persistence expectations

These are release data and must survive host replacement or operator error:

- PostgreSQL service data for Keycloak, MAS, Synapse, Nextcloud, and Weave backend.
- Nextcloud application data volume for files/calendar storage.
- Matrix/Synapse media and local data volume.
- Caddy data/config volumes when using ACME-managed certificates.
- Generated bootstrap env, TLS material, Matrix/MAS/backend config, signing material, and local secret files needed for reprovisioning.

Before go-live, decide whether persistence is host-local snapshots, attached volume snapshots, or backup export. The key requirement is that the choice is explicit and restore-tested.

## Install flow

1. Provision DNS for the public hostnames.
2. Copy a filled-in release env file onto the host.
3. Stage TLS material on disk.
4. Export the `TF_VAR_*` values from the env file.
5. Run `bash weave-workspace/install.sh`.
6. Run `bash weave-workspace/release-verify.sh`.
7. Run `bash weave-workspace/operator-check.sh`.
8. If local-only test-user bootstrap was enabled accidentally, disable it and re-apply before production use.

## Verify after install

Use `weave-workspace/release-verify.sh` with:

- `WEAVE_API_BASE_URL` or legacy-compatible `WEAVE_BASE_URL`.
- `WEAVE_PUBLIC_BASE_URL`.
- `WEAVE_OIDC_ISSUER_URL`.
- `WEAVE_NEXTCLOUD_BASE_URL`.
- `WEAVE_MATRIX_HOMESERVER_URL` for the northbound facade on the API origin.
- `WEAVE_MATRIX_PROVIDER_URL` for the southbound Matrix provider.
- optional `WEAVE_TLS_CA_FILE` when a private CA is required.

The script checks:

- Keycloak discovery on the public issuer URL.
- Weave product gateway plus `/files` and `/calendar` routes.
- Backend readiness through `/api/health/ready` on the canonical public API base.
- Nextcloud install status through the raw files origin.
- Backend-owned Nextcloud actor env on `weave-backend`, plus the matching Nextcloud user.
- Matrix delegated auth discovery, client versions, and `/authorize` reachability.

## Operational minimums

Operators need:

- secret inventory and rotation plan;
- backup and restore procedure for Postgres-backed service data, Nextcloud data, Matrix/Synapse media, Caddy/TLS state, and generated config/secrets;
- image upgrade procedure with rollback point;
- post-deploy verification using `release-verify.sh`;
- host-local verification using `operator-check.sh`;
- restore rehearsal ending with `restore-smoke.sh`;
- redacted diagnostics via `support-bundle.sh` before sharing logs;
- explicit note on whether test users are forbidden or temporarily enabled.

Use [Operator runbook](operator-runbook.md) for install, verification, rotation, backup, restore, and first-line triage.

## Not covered yet

This path does not yet provide:

- automated backup or restore jobs;
- secret manager integration;
- zero-downtime upgrades or HA;
- public monitoring, metrics, or alert routing;
- fully declarative Nextcloud OIDC bootstrap;
- managed SaaS installer UX.

## Sprint 12 restore-smoke and upgrade evidence

Before promoting a release, operators must record artifact-only restore smoke evidence for Keycloak, MAS/Synapse, Nextcloud, OpenProject/Boards, LiveKit/TURN, Weave backend/admin/client assets, databases, data volumes, secrets/TLS references, and generated config. A live rehearsal is required before production-strength claims. Provider schema changes must attach dry-run, backup, rollback/archive, and post-migration readiness evidence.
