# Interop Gateway and External Collaboration

## Status

This is a post-Release-1 product direction. It must not block the Release 1 gate for the core Weave app, backend, and local/self-hosted stack.

Interop work is release-compatible only when it is behind explicit feature flags and defaults to off. Release 1 validation must remain green without Slack, Teams, third-party SaaS credentials, or external admin consent.

## Strategic direction

Weave should be built as a secure primary collaboration platform with a controlled Interop Gateway, not as an isolated messenger and not as a clone of Slack or Teams.

Product promise:

> Organizations do not have to shut Slack or Teams down on day one. Weave can connect to existing collaboration systems in a controlled way, make privacy and self-administration the target state, and support gradual migration into a self-hosted collaboration platform.

## Product principles

- Weave is the system of record for workspace policy, roles, guests, audit state, and administrative decisions.
- Matrix remains the internal chat protocol/core for Weave rooms.
- External platforms are integration edges, not the primary product surface.
- The Flutter client must not contain Slack- or Teams-specific transport logic.
- Bridge behavior must be explicit, scoped, auditable, rate-limited, and reversible.
- Bridge content must not be marketed as end-to-end encrypted unless the exact end-to-end boundary has been implemented and validated.
- Direct bulk export/backup/reimplementation of Slack or Teams is not the first bridge promise.

## Recommended sequence

1. Protect Release 1.
   - Keep interop disabled by default.
   - Keep Release 1 E2E independent of external SaaS availability.
   - Keep release blockers focused on setup, sign-in, chat, files, calendar decisions, diagnostics, backup/restore, and accessibility.

2. Build the Interop Gateway foundation.
   - Token vault and secret redaction.
   - Tenant isolation.
   - Consent registry.
   - Canonical event model.
   - Idempotency and loop prevention.
   - Per-tenant/per-provider rate limiting.
   - Retry and dead-letter handling.
   - Audit events for install, scope changes, mapping changes, imports, exports, replay, disconnect, and failures.

3. Ship the Guest Portal in parallel.
   - Guest access gives immediate external-collaboration value without depending on Slack or Teams admin consent.
   - Guests must be constrained by Weave roles and room/file/calendar policies.
   - External identities should be linkable to Weave users or guests, but never silently merged.

4. Build Slack Bridge first as a narrow live on-ramp.
   - Use a Slack app with admin installation and minimal scopes.
   - Prefer Events API for new activity over history scraping.
   - Start with selected channels and opt-in mappings.
   - Support text messages in both directions for one sandbox channel/room pair.
   - Defer full history migration, private-channel migration, direct messages, complete reactions, perfect edits/deletes, and file parity.

5. Build the Migration Toolkit as inventory and dry-run first.
   - Show admins what can be migrated, what cannot, which scopes are required, likely duration, conflicts, and user/channel mappings.
   - Treat actual import/backfill as a later, resumable, idempotent job.

6. Build Teams Bridge after Slack hardening.
   - Use Teams app/bot integration and Microsoft Graph where appropriate.
   - Prefer Resource-Specific Consent for scoped pilots.
   - Use tenant-wide admin consent only for clearly separated migration/export packages.
   - Use Graph change notifications and delta patterns instead of polling.

7. Build Connector SDK late.
   - Do not create a public connector marketplace before two real connectors prove the internal framework.
   - Start with private, capability-scoped, sandboxed connectors.
   - Require signed packages, pinned dependencies, secret brokering, quotas, audit, and review/governance before sharing connectors broadly.

## Architecture

Start the Interop Gateway as a backend-owned module inside `weave-backend`. Split it into a separate service only when scale, deployment, or blast-radius constraints require it.

Suggested backend module boundaries:

- `interop-core`
  - canonical event model
  - connection lifecycle
  - consent and capabilities
  - channel/room mappings
  - idempotency
  - audit and status
- `interop-slack`
  - Slack OAuth/install flow
  - Slack Events API webhook handling
  - Slack signing-secret verification
  - Slack channel/user mapping
- `interop-teams`
  - Teams app/bot integration
  - Graph subscription/change notification handling
  - Resource-Specific Consent and admin consent state
- `interop-migration`
  - inventory
  - dry-run
  - import jobs
  - reports
- `interop-connectors`
  - later private connector runtime/SDK boundary

The app should consume backend APIs only. It should show admin setup, status, mappings, degraded states, and audit-friendly messages without owning provider secrets or provider transport behavior.

## Data model concepts

- `ExternalConnection`
  - provider (`slack`, `teams`, later connector id)
  - tenant/workspace id
  - status (`draft`, `pending_consent`, `active`, `degraded`, `disabled`)
  - granted scopes/capabilities
  - encrypted credential references
  - created/updated/admin actor metadata

- `BridgeChannelMapping`
  - Weave room / Matrix room id
  - external channel/team/conversation id
  - mode (`import_only`, `mirror`, `announce_only`)
  - direction policy
  - guest visibility policy

- `ExternalPrincipal`
  - provider user id
  - optional matched Weave user id
  - optional guest identity id
  - display name snapshot
  - mapping/consent status

- `BridgeMessage`
  - Matrix event id
  - external provider message id
  - thread id
  - idempotency key
  - edit/delete state
  - attachment references
  - source timestamp and provenance

- `WebhookEvent`
  - provider event id
  - signature validation result
  - processing status
  - retry count
  - dead-letter reason

## Slack bridge MVP

Minimum viable slice:

- Admin installs Slack app.
- Backend stores Slack tokens via encrypted secret references.
- Backend validates Slack request signatures.
- Admin maps one Slack channel to one Weave room.
- Slack text message appears in the mapped Weave room.
- Weave text message appears in the mapped Slack channel.
- The bridge prevents message loops and duplicates.
- Degraded/rate-limited state is visible in admin status.
- Disconnect revokes or invalidates the connection and stops delivery.
- Release 1 still passes with the Slack bridge disabled.

Explicit Slack non-goals for the first slice:

- full history migration
- private channel migration by default
- direct messages
- full file migration
- perfect thread/reaction/edit/delete parity
- Slack client replacement
- Slack backup/export positioning

Rationale: Slack rate limits and marketplace guidelines make a bulk-export or Slack-replication story risky as the first bridge promise. Treat Slack first as a live on-ramp and controlled notification/action surface.

## Teams bridge MVP

Minimum viable slice after Slack hardening:

- Teams app/bot package.
- Admin consent flow with clear scope package.
- Resource-Specific Consent where possible for scoped teams/chats.
- Graph change notifications for new activity.
- Mapping between selected Teams channel/conversation and Weave room.
- Text-message proof of flow in a test tenant.
- Visible degraded state for expired subscriptions, throttling, consent changes, and delivery failures.

Teams migration/import must be treated separately from live bridge behavior because Microsoft Graph import and migration APIs have strict modes, permissions, and lifecycle constraints.

## Migration Toolkit

Start with a trust-building preflight:

- inventory source workspace/team/channel structure
- estimate message/file/user counts
- identify unmappable content
- propose Weave user/guest mappings
- show required scopes and consent package
- estimate duration and rate-limit budget
- produce a dry-run report before any import

Later import jobs must be:

- idempotent
- resumable
- auditable
- provenance-preserving
- safe to cancel
- bounded by explicit admin consent

## Security and privacy requirements

- Provider tokens and signing secrets live only in backend-controlled secret storage.
- Support bundles must redact all provider credentials and webhook secrets.
- Every provider action must be least-privilege and capability-scoped.
- Scope changes require admin review and audit entries.
- Bridge mappings are opt-in per room/channel.
- Private channels, direct messages, and history backfill require separate explicit consent.
- External identities must be labeled clearly in Weave UI.
- Imported or bridged messages must retain provenance metadata.
- Retry/backfill workers must respect `429` and `Retry-After` behavior.
- Dead-lettered events must be inspectable without exposing secrets.

## Release gates for interop work

An interop slice is mergeable only when:

- it is behind a feature flag and disabled by default unless explicitly promoted;
- core Release 1 tests remain green without external provider credentials;
- provider webhooks validate signatures;
- token storage and support-bundle redaction are covered;
- loop prevention and idempotency are tested;
- rate-limit/degraded behavior is visible;
- admin-facing copy explains scope and data movement clearly;
- accessibility requirements remain met for admin setup/status flows.

## Useful primary references

- Slack Web API rate limits: https://docs.slack.dev/apis/web-api/rate-limits/
- Slack 2025 rate-limit changes for non-Marketplace apps: https://docs.slack.dev/changelog/2025/05/29/rate-limit-changes-for-non-marketplace-apps/
- Slack Events API: https://docs.slack.dev/apis/events-api/
- Slack Marketplace guidelines: https://docs.slack.dev/slack-marketplace/slack-marketplace-app-guidelines-and-requirements/
- Slack Audit Logs API: https://docs.slack.dev/admins/audit-logs-api/
- Microsoft Teams Resource-Specific Consent: https://learn.microsoft.com/en-us/microsoftteams/platform/graph-api/rsc/resource-specific-consent
- Microsoft Entra admin consent: https://learn.microsoft.com/en-us/entra/identity/enterprise-apps/grant-admin-consent
- Microsoft Graph throttling guidance: https://learn.microsoft.com/en-us/graph/throttling
- Microsoft Teams import external messages: https://learn.microsoft.com/en-us/microsoftteams/platform/graph-api/import-messages/import-external-messages-to-teams
