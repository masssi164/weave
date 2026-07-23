# Single-host operator guide

This guide describes the release-capable single-host Weave path: one Linux host, public DNS,
trusted HTTPS, Docker Engine with Compose v2, explicit operator-managed SecretRefs, persistent
named volumes, an externally installed protected Keycloak supervisor, and a verification workflow.

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

Start from `weave-workspace/environments/main.env.example`. Copy it to a private root-owned file
outside the checkout, replace every public placeholder, and pin every image to an approved
`@sha256` digest. The closed parser accepts public URLs, ports, volume/network names, organization
metadata, image references, and absolute generated/secret/TLS roots. It rejects credential-shaped
variables.

Credentials are individual mode-0600 files under `WEAVE_SECRET_ROOT`; TLS material lives under
`WEAVE_TLS_ROOT`. Supply `WEAVE_ENV_FILE`, `WEAVE_CANDIDATE_COMMIT`, and the absolute immutable
`WEAVE_KEYCLOAK_SUPERVISOR` path to lifecycle commands. Never source the reviewed environment into
the shell as a secret bag and never commit a filled copy.

## Calendar boundary

The Calendar facade uses the backend actor's canonical `weave-workspace` CalDAV collection for the
workspace projection.

Do not configure private personal calendar paths unless a later contract explicitly provisions sharing, delegated user access, revocation, and support-safe diagnostics.

## TLS source

Use publicly trusted certificates, for example Let's Encrypt or a certificate issued by your edge platform. Do not rely on the generated local CA outside development.

Recommended pattern:

1. Issue a SAN certificate for the five canonical hostnames.
2. Place the cert and key on the host with restrictive permissions.
3. Install the certificate, key, and optional CA as named files below `WEAVE_TLS_ROOT`.
4. Keep private keys mode `0600`; expose only the public CA to clients that require it.

## Image source

Pin runtime images instead of relying on floating local defaults:

- Pin every image in the reviewed main environment to an immutable digest.
- Keep the Keycloak image and sanitizer digests identical to the externally approved supervisor generation.
- Change MAS, Synapse, Nextcloud, PostgreSQL, or Caddy only after the scheduled compatibility/conformance lane passes.
- Verify deployed container image IDs/digests against the reviewed model before readiness is accepted.
- Record the chosen image set in the deployment change or release note.

## Persistence expectations

These are release data and must survive host replacement or operator error:

- PostgreSQL service data for Keycloak, MAS, Synapse, Nextcloud, and Weave backend.
- Nextcloud application data volume for files/calendar storage.
- Matrix/Synapse media and local data volume.
- Caddy data/config volumes when using ACME-managed certificates.
- Generated bootstrap env, TLS material, Matrix/MAS/backend config, signing material, and local secret files needed for reprovisioning.

Before go-live, decide whether persistence is host-local snapshots, attached volume snapshots, or backup export. The key requirement is that the choice is explicit and restore-tested.

## Guarded S3/MinIO Files target

The `weave-s3-minio` Files adapter is a migration target, not a second live data plane. It is disabled by default and must not be enabled alongside an active Nextcloud binding merely to test connectivity.

Provider-lab configuration uses backend-only values under `WEAVE_FILES_S3_*`: `ENABLED`, `ENDPOINT`, `REGION`, `BUCKET`, `ACCESS_KEY`, `SECRET_KEY`, and `PATH_STYLE`. Keep credentials in environment-scoped secret storage; never place them in RuntimeProfile, WebDAV content, client configuration, evidence, or support bundles.

An operator may activate the adapter only through the portability cutover flow after deterministic dry-run, fidelity review, copy, delta synchronization, verification, and rollback evidence. The provider-binding compare-and-swap is the authority switch. Toggling `WEAVE_FILES_S3_ENABLED` alone must never be treated as a provider change or a `Ready` claim.

## Install flow

1. Provision DNS for the public hostnames.
2. Copy a filled-in release env file onto the host.
3. Stage TLS material on disk.
4. Install and approve one immutable root-owned Keycloak supervisor generation.
5. Set `WEAVE_ENV_FILE`, `WEAVE_CANDIDATE_COMMIT`, and `WEAVE_KEYCLOAK_SUPERVISOR` for the unprivileged operator process.
6. Run `bash weave-workspace/install.sh main`.
7. Run `bash weave-workspace/release-verify.sh`.
8. Run `bash weave-workspace/operator-check.sh`.
9. Rerun `compose.sh main keycloak-plan` and require zero diff.

## Verify after install

Use `weave-workspace/release-verify.sh` with:

- `WEAVE_API_BASE_URL`.
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
- explicit proof that disposable E2E identities and isolated namespaces are absent from main.

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
