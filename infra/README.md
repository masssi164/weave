# Weave Infrastructure

**Repeatable self-hosted Weave stack for operators.**

`infra/` provisions the Docker/OpenTofu foundation for a self-hosted Weave deployment: identity, chat, files/calendar storage, backend API routing, local HTTPS, provider-readiness checks, backups, restore smoke, and support diagnostics.

This directory is the operator layer inside the Weave monorepo. The daily user experience lives in `../client`, product contracts and provider facades live in `../server`, and this layer makes the stack verifiable and recoverable without exposing secrets.

## What it provisions

- Caddy as the HTTPS gateway.
- Keycloak realm, clients, scopes, roles, and first-party Weave app contract.
- Matrix/Synapse with Matrix Authentication Service delegated auth.
- Nextcloud as the technical/admin/protocol backing service for files and calendar.
- `weave-backend` behind the canonical API route.
- PostgreSQL databases and persisted Docker volumes.
- Default Matrix workspace space/rooms for local/dev and smoke evidence.
- Optional provider-stack config for OpenProject, ONLYOFFICE/Collabora, DevOps candidates, and other guarded seams.
- Install, teardown, release verification, operator checks, backup/restore smoke, and support-bundle scripts.

## What it does not claim yet

- Multi-host high availability.
- Managed SaaS installer behavior.
- Automatic offsite backups.
- A complete Slack/Teams replacement.
- Production connector/provider writes.
- Completed Matrix E2EE product readiness.
- LiveKit is the active meeting/video-call provider contract; TURN/SFU hardening and recording/caption policy remain promotion gates.
- Agent/automation runtime.

Those are later product or operations tracks and must stay behind explicit contracts and evidence.

## Quick start: local/dev stack

Add local host entries before opening browser-facing URLs:

```text
127.0.0.1 weave.local api.weave.local auth.weave.local files.weave.local matrix.weave.local
```

Bootstrap the stack:

```bash
cd weave-workspace
./install.sh
```

`install.sh` defaults to a shared-host-safe isolated port block, runs preflight checks, generates missing local secrets and TLS material, applies both OpenTofu stages, waits for backend readiness, and bootstraps the Nextcloud `user_oidc` app. Generated local inputs are persisted in `weave-workspace/.generated/bootstrap.env`; a no-secrets app summary is written to `weave-workspace/.generated/app-config.env`.

For TLS trust, port modes, smoke-test inputs, and native app contracts, see [Local bootstrap](docs/local-bootstrap.md).

## Single-host operator path

For a real single-host deployment, start here:

- [Single-host operator guide](docs/single-host-operator-guide.md): target shape, public contract, required inputs, TLS/image/persistence expectations, and verify flow.
- [release.env.example](weave-workspace/release.env.example): operator-facing environment template.
- [Operator runbook](docs/operator-runbook.md): install/upgrade, rotation, backup, restore, destructive reset, and triage guidance.
- [CalDAV/CardDAV external clients](docs/calendar-caldav-external-clients.md): DAV discovery, safe external-client credential path, and blocked private calendar/addressbook/profile flows.
- [Connector runtime guardrails](docs/connector-runtime-guardrails.md): disabled-by-default connector runtime, callback, secret, and support-bundle boundaries.
- [OpenProject Boards runtime](docs/openproject-boards-runtime.md): optional read-only validation setup and live E2E gate; off by default.

After installation, verify public and host-local state:

```bash
bash weave-workspace/release-verify.sh
bash weave-workspace/operator-check.sh
```

## Public contract

Default local names resolve to loopback; non-local installs derive the same pattern from `<tenant_domain>`:

- `https://<tenant_domain>`: Weave product gateway, including `/files` and `/calendar` product routes.
- `https://api.<tenant_domain>/api`: canonical backend API origin.
- `https://auth.<tenant_domain>`: Keycloak.
- `https://matrix.<tenant_domain>`: Matrix/Synapse/MAS behind the matrix hostname.
- `https://files.<tenant_domain>`: raw Nextcloud technical/admin/protocol fallback.

Product clients should prefer Weave routes and backend APIs where they exist. Raw Nextcloud remains a technical/admin/protocol fallback, not the customer-facing files/calendar UX.

## Provider-stack posture

Optional providers are fail-closed by default:

- OpenProject is an optional read-only Boards validation path, not a visible product UX.
- ONLYOFFICE/Collabora are optional office candidates behind backend-owned capabilities and launch checks.
- DevOps providers such as GitLab/Forgejo stay disabled unless an explicit runtime contract configures them.
- Missing provider credentials must produce support-safe unavailable/not-configured readiness, not insecure fallback behavior.
- Support bundles redact tokens, cookies, app passwords, signing keys, provider URLs, raw provider errors, and generated secrets.
- Nextcloud Contacts/CardDAV and Forms seams are visible to the backend but stay disabled/fail-closed until the backend contracts are merged and live-validated.
- Private personal calendar/addressbook access is blocked unless a later contract adds explicit sharing, provisioning, or delegated-token behavior.

## Repo compass

- `README.md`: operator overview and entry points.
- `AGENTS.md`: repository navigation notes for maintainers.
- `Makefile`: local helper targets such as `make dev-hosts` and `make smoke`.
- `../.github/workflows/ci.yml`: OpenTofu/shell validation plus manual full-stack smoke.
- `KEYCLOAK_CONTRACT.md`: realm, client, scope, claim, and audience contract.
- `docs/local-bootstrap.md`: local port modes, TLS trust, integration test inputs, and native app contract.
- `docs/single-host-operator-guide.md`: single-host deployment target.
- `docs/operator-runbook.md`: operations, backup/restore, rotation, and triage.
- `docs/matrix-default-workspace.md`: default Matrix space/room provisioning.
- `docs/matrix-e2ee-posture.md`: current honest E2EE posture.
- `docs/calendar-caldav-external-clients.md`: CalDAV/CardDAV discovery, revocable client credentials, and fail-closed profile boundaries.
- `docs/openproject-boards-runtime.md`: optional OpenProject read-only setup and promotion gates.
- `weave-workspace/install.sh`: end-to-end bootstrap for local and single-host runs.
- `weave-workspace/teardown.sh`: non-destructive cleanup by default; destructive volume reset requires explicit confirmation.
- `weave-workspace/release-verify.sh`: public endpoint verification for non-local single-host installs.
- `weave-workspace/operator-check.sh`: host-local container and health checks.
- `weave-workspace/backup.sh`, `restore-smoke.sh`, `support-bundle.sh`: operator support and recovery helpers.
- `weave-workspace/01-infrastructure`: Docker runtime, generated config, and service modules.
- `weave-workspace/02-keycloak-setup`: Keycloak tenant configuration stage.

## Validation

Repository-safe validation used by CI:

```bash
tofu -chdir=weave-workspace/01-infrastructure validate
tofu -chdir=weave-workspace/02-keycloak-setup validate
tofu -chdir=weave-workspace/01-infrastructure plan -refresh=false
bash -n weave-workspace/install.sh
```

Local/full-stack validation when Docker and the optional test user flow are available:

```bash
TF_VAR_create_test_user=true bash weave-workspace/install.sh
bash weave-workspace/smoke-test.sh
```

GitHub Actions runs deterministic repository checks on pushes and pull requests. Docker-backed full-stack smoke is manual-only and asks the dispatcher to confirm power/storage budget before it starts.

## Operator safety

- `teardown.sh` is non-destructive by default: it removes Weave containers/network but preserves persistent Docker volumes and generated local secrets/config.
- Destructive local reset requires both `WEAVE_REMOVE_VOLUMES=true` and `WEAVE_CONFIRM_DESTRUCTIVE_RESET=<tenant/workspace slug>`.
- Create an operator-owned backup before destructive maintenance:

```sh
bash weave-workspace/backup.sh /var/backups/weave
```

- Run restore smoke after restoring or reprovisioning from backup artifacts:

```sh
bash weave-workspace/restore-smoke.sh /var/backups/weave/<weave-backup-timestamp>
```

- Create a redacted diagnostics bundle before sharing logs manually:

```sh
bash weave-workspace/support-bundle.sh
```

Support bundles are not backups. Keep backup artifacts private; they contain databases, volume archives, and generated config/secrets.
