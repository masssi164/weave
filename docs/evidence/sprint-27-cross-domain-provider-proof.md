# Sprint 27 cross-domain provider proof evidence

Status: support-safe provider-lab evidence for #643, #644, #645, and #646.

This report records the Sprint 27 domain-proof slice independently from repository delivery. It proves Calendar, Files, and Identity at `migration_dry_run` reality level and keeps broad provider-neutrality, release-ready, customer-ready, and production rollback wording blocked.

## Evidence artifacts

| Issue | Domain | Provider boundary | Reality level | Artifact |
| --- | --- | --- | --- | --- |
| #643 | Calendar | Nextcloud CalDAV -> Radicale | `migration_dry_run` | `release/provider-lab/cross-domain-provider-proof/calendar-nextcloud-radicale.fixture.json` |
| #644 | Files | Nextcloud Files -> MinIO S3 | `migration_dry_run` | `release/provider-lab/cross-domain-provider-proof/files-nextcloud-minio.fixture.json` |
| #645 | Identity | Keycloak -> Authentik | `migration_dry_run` | `release/provider-lab/cross-domain-provider-proof/identity-keycloak-authentik.fixture.json` |
| #646 | Gate | Provider neutrality beyond chat | scoped claim gate | `release/provider-lab/cross-domain-provider-proof/sprint-27-provider-neutrality-claim-gate.fixture.json` |

The aggregate scoreboard is `release/provider-lab/cross-domain-provider-proof/sprint-27-cross-domain-scoreboard.json`.

## Calendar proof (#643)

The Calendar fixture covers `WeaveCalendar`, `WeaveEvent`, `WeaveRecurrence`, `WeaveAttendee`, `WeaveResource`, `WeaveAvailability`, and `ProviderRef`. It reports preserved event identity, schedule, recurrence, attendee identity/status, and provider provenance. Lossy or limited fields are explicitly listed for resource booking policy, free/busy source details, and attendee delegation chain. The member UI remains `Weave Calendar`; provider names and mapping limits stay admin/operator visible.

## Files proof (#644)

The Files fixture covers `WeaveDrive`, `WeaveFolder`, `WeaveFile`, `WeaveVersion`, `WeaveShare`, `WeavePermission`, `WeaveLock`, `WeaveQuota`, and `ProviderRef`. It validates file metadata and permissions while reporting lossy public-link policy, lock owner semantics, and provider-native restore tokens. Silent permission drops are forbidden.

## Identity proof (#645)

The Identity fixture covers OIDC, SAML, roles, groups, user mapping, session boundaries, and rollback limits for Keycloak -> Authentik. Evidence contains no secrets, raw assertions, bearer tokens, or raw provider payloads. It preserves stable subject, group, role, and effective policy refs while naming session continuity, composite-role expression, and SAML override risks.

## Provider-neutrality gate (#646)

The claim gate blocks chat-only evidence from broad provider-neutrality claims. The scoreboard shows each domain reality level separately: Calendar, Files, and Identity are `migration_dry_run`; Chat remains scoped to the existing Sprint 23 provider-lab switch evidence; setup-flow evidence is named separately.

Historical #665 evidence is obsolete and excluded from the scoreboard. GitHub protected delivery-lane evidence remains separate and is not used to block the Calendar/Files/Identity aggregate proof in this slice.

## Gate

Run:

```sh
./gradlew crossDomainProviderProofCheck acceptanceContract releaseEvidenceCheck
```

Expected marker output includes `SPRINT27_CALENDAR_PROVIDER_BOUNDARY_PROOF`, `SPRINT27_FILES_PROVIDER_BOUNDARY_PROOF`, `SPRINT27_IDENTITY_PROVIDER_BOUNDARY_PROOF`, `SPRINT27_PROVIDER_NEUTRALITY_SCOREBOARD`, and `SPRINT27_PROVIDER_NEUTRALITY_CLAIM_GATE`.
