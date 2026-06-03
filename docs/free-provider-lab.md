# Free provider lab

Status: Sprint 22 operator and developer runbook.

The free provider lab is the local, self-hosted proof bed for Sprint 22. It exists to prepare Sprint 23 Chat Provider Switch work without making unsupported release or customer-ready claims.

## Claim boundary

The lab proves only these facts when `./gradlew providerLabCheck` is green:

- the checked Docker Compose topology names the Sprint 22 target providers;
- each target provider has one manifest with a valid Sprint 21 reality level;
- the canonical chat fixture has the exact required counts and history status expectations;
- the Sprint 23 entry scoreboard agrees with the gate output;
- support-safe evidence omits secrets, credential-bearing URLs, raw provider payloads, message bodies, attachment contents, and private provider identifiers.

The lab does not prove provider interchangeability, production rollback, migration apply readiness, Weaver availability, per-user PA runtime availability, full history preservation, or customer-ready release status.

## Provider scope

Identity providers:

- Keycloak for OIDC, SAML, role, and group mapping foundation.
- Authentik for OIDC, SAML, role, and group mapping foundation.

Chat providers:

- Matrix/Synapse for the existing self-hosted chat path and Sprint 23 migration proof.
- Zulip as the second free chat provider for provider-switch fixtures.

Files providers:

- Nextcloud Files for WebDAV-oriented file facade proof.
- MinIO through the S3 adapter boundary.

Calendar providers:

- Nextcloud CalDAV.
- Radicale CalDAV.

Boards provider:

- OpenProject.

Weaver boundary:

- Docker Runtime boundary only. Sprint 22 does not enable a per-user PA runtime.

## Start, inspect, stop, and reset

Use the wrapper script from the repository root:

```bash
infra/provider-lab/scripts/lab.sh start
infra/provider-lab/scripts/lab.sh ps
infra/provider-lab/scripts/lab.sh health
infra/provider-lab/scripts/lab.sh stop
infra/provider-lab/scripts/lab.sh reset
```

The reset command runs Docker Compose down with volumes for the `weave-provider-lab` project only. It is intended to return the local lab to an empty state. Do not point these commands at production infrastructure.

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

If the gate fails on manifest reality levels, use only the Sprint 21 ladder: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, or `release_ready`.

If the fixture gate fails, regenerate or edit the fixture so the stable IDs match the counts for spaces, channels, people, messages, threads, reactions, attachments, edits, deletes, pinned decisions, and the E2EE unsupported-history fixture.
