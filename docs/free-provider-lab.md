# Free provider lab

Status: Sprint 22 operator and developer runbook.

The free provider lab is the local, self-hosted proof bed for Sprint 22. It exists to prepare Sprint 23 Chat Provider Switch work without making unsupported release or customer-ready claims. The honest Sprint 22 claim is a reproducible provider lab smoke/fixture environment.

## Claim boundary

The lab proves only these facts when `./gradlew providerLabCheck` is green:

- the checked Docker Compose topology names the Sprint 22 target providers and keeps heavy/bootstrap-sensitive providers profile-gated where needed;
- each target provider has one manifest with a valid Sprint 21 reality level;
- the canonical chat fixture has the exact required counts and history status expectations;
- the Sprint 23 entry scoreboard agrees with the gate output;
- support-safe evidence omits secrets, credential-bearing URLs, raw provider payloads, message bodies, attachment contents, and private provider identifiers.

The lab does not prove provider interchangeability, production SSO, production-grade file storage, production rollback, migration apply readiness, Weaver availability, per-user PA runtime availability, full history preservation, release readiness, or customer-ready status.

## Provider scope

Identity providers:

- Keycloak for local HTTP-only OIDC, SAML, role, and group mapping foundation. The lab uses `start-dev`; production hostname, TLS/proxy, and external database posture are out of Sprint 22.
- Authentik for OIDC, SAML, role, and group mapping foundation with PostgreSQL and Redis. SMTP is disabled/dummy, and Docker socket Outposts are intentionally out of scope.

Chat providers:

- Matrix/Synapse for a local non-federated homeserver path and Sprint 23 migration proof. `server_name` is fixed at first config generation; federation, TURN, bridges, and robust media are out of scope.
- Zulip as the second free chat provider for provider-switch fixtures. Zulip is profile-gated because the official Docker deployment is maintenance-heavy, requires a bootstrap step, and must not be presented as one-command parity with simpler providers.

Files providers:

- Nextcloud Files for WebDAV-oriented file facade proof using the Apache image plus PostgreSQL and named volumes. Redis, SMTP, reverse proxy hardening, and object-storage primary storage are separate concerns.
- MinIO through the S3 adapter boundary as standalone local S3-compatible object storage only. Distributed/erasure-coded production durability and AWS parity are out of scope.

Calendar providers:

- Nextcloud CalDAV.
- Radicale CalDAV.

Boards provider:

- OpenProject as a local boards/work-package fixture using the Docker image. The Sprint 22 claim is local lab boot/readiness only, not production deployment or repository integration.

Weaver boundary:

- Docker Runtime boundary only. Sprint 22 does not enable a per-user PA runtime.

## Start, inspect, stop, and reset

Use the wrapper script from the repository root:

```bash
infra/provider-lab/scripts/lab.sh verify
infra/provider-lab/scripts/lab.sh synapse-config
infra/provider-lab/scripts/lab.sh start
infra/provider-lab/scripts/lab.sh ps
infra/provider-lab/scripts/lab.sh health
infra/provider-lab/scripts/lab.sh stop
infra/provider-lab/scripts/lab.sh reset
```

Synapse requires generated static config; dynamic env-only config is not the Sprint 22 contract. Run `synapse-config` before the first `start` for a fresh `synapse-data` volume, using the final local `WEAVE_LAB_SYNAPSE_SERVER_NAME` value because Matrix server names cannot be changed safely after bootstrap.

Zulip is not started by the default `start` command. If the heavier Zulip fixture is needed on a host with enough resources and non-rootless Docker, run:

```bash
infra/provider-lab/scripts/lab.sh zulip-init
infra/provider-lab/scripts/lab.sh start-zulip
```

This profile uses the current official `ghcr.io/zulip/zulip-server` image and keeps organization creation/login as a documented local bootstrap path. Sprint 22 must not claim Zulip one-command parity unless a later PR proves it with live evidence.

The reset command runs Docker Compose down with volumes for the `weave-provider-lab` project only, including optional profiles. It is intended to return the local lab to an empty state. Do not point these commands at production infrastructure.

For local secrets, copy `infra/provider-lab/.env.example` to `infra/provider-lab/.env` or export environment variables in your shell. The checked-in defaults are local-only placeholders and are not production credentials.

## Validation commands

CI-safe validation does not start live providers:

```bash
./gradlew providerLabCheck
./gradlew productRealityClaimGateCheck releaseEvidenceCheck docsCheck --console=plain
```

The provider lab gate validates:

- `release/provider-lab/manifests/*.json`;
- `fixtures/provider-lab/chat-fixture.json`;
- `release/provider-lab/support-redaction-report.json`;
- `release/provider-lab/sprint-23-entry-scoreboard.json`;
- `release/provider-lab/health-report.sample.json`.

## Troubleshooting

If a provider container fails to start, inspect only local container status and logs. Do not paste secrets, tokens, cookies, raw provider payloads, credential-bearing URLs, message bodies, or attachment contents into issues or closure evidence.

Provider-specific checks should stay honest:

- Keycloak: local `/realms/master` and optional health endpoints if enabled; seeded test realm/client/users only when deterministic scripts exist.
- Authentik: container health and initial setup/login page; no Docker socket Outposts or external SMTP.
- Matrix/Synapse: `/_matrix/client/versions` after static config generation; local users/rooms only when seeded with a local registration secret.
- Zulip: profile starts only after documented bootstrap; organization creation/login is a manual/local fixture until automated evidence exists.
- Nextcloud: `/status.php`, login page, and optional WebDAV/`occ status`; no silent MinIO primary-storage claim.
- MinIO: API/console reachability and seeded bucket only when an init step exists; explicit endpoint/region/path-style settings are required for adapter tests.
- OpenProject: local HTTP readiness with explicit non-production HTTP posture.

If the gate fails on manifest reality levels, use only the Sprint 21 ladder: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, or `release_ready`.

If the fixture gate fails, regenerate or edit the fixture so the stable IDs match the counts for spaces, channels, people, messages, threads, reactions, attachments, edits, deletes, pinned decisions, and the E2EE unsupported-history fixture.
