# Provider portability contract

Provider portability means Weave can replace or add provider adapters without changing the member-facing domain contract. It does not promise magical lossless migration. The release principle is **no unaccounted data loss**: every unsupported field, permission, object, or semantic difference must be reported, classified, and approved before an apply path is allowed.

## Provider Adapter Manifest

Every provider adapter must publish a support-safe manifest before it can back a product domain.

| Field | Requirement |
| --- | --- |
| `adapterKey` | Stable adapter identifier, e.g. `keycloak-realm`, `openproject-primary`, `livekit`, or `nextcloud-files`. |
| `domain` | One canonical domain, or an explicit list when the adapter spans domains. |
| `apiProfile` | Standards/API profile used by the adapter, such as OIDC, SAML, SCIM, CalDAV, WebDAV, Matrix Client-Server, OpenProject REST, LiveKit token API, or provider Graph API. |
| `canonicalObjects` | Canonical object kinds the adapter can read, write, or link. |
| `capabilityKeys` | Stable Weave capability keys, split into read, write, admin, and runtime/tool grants. |
| `readinessChecks` | Backend-owned checks that prove credentials, connectivity, scopes, schema, policy, and audit safety. |
| `unsupportedFields` | Provider features or fields not represented in the canonical contract. |
| `migrationLimits` | Known lossy areas, rate limits, export/import gaps, and conflict rules. |
| `auditEvents` | Support-safe audit event names emitted for dry-run, apply, rollback, policy decision, and diagnostics. |
| `secretBoundary` | Statement that credentials, raw provider payloads, and endpoints are never exposed to normal members. |

## Provider Mapping Table

Each domain-backed category must keep a mapping from provider object kinds to canonical object kinds.

| Mapping column | Description |
| --- | --- |
| Source provider object | Provider-native kind and immutable identifier strategy. |
| Canonical object | Weave domain object and stable reference. |
| Source of truth | Provider-owned, Weave-owned, or shared with explicit precedence. |
| Read support | Supported fields, pagination, filters, and consistency guarantees. |
| Write support | Supported mutations, idempotency, optimistic locking, and rollback/compensation. |
| Lossy fields | Unsupported fields or semantics that must appear in a lossy report. |
| Conflict rule | How duplicate, stale, deleted, renamed, or concurrently changed objects are handled. |
| Audit reference | Event names and evidence references for dry-run/apply/rollback. |

## Required reports

Provider changes and migrations must produce machine-readable and reviewer-readable evidence.

- **Export report**: source objects discovered, immutable IDs, counts, redacted samples, permissions, attachments/binaries, versions, references, rate-limit notes, and unsupported provider features.
- **Import report**: target feasibility, object mapping, generated target IDs, skipped objects, idempotency keys, and post-import validation.
- **Lossy report**: every unsupported field, permission, workflow transition, recurrence rule, comment, attachment, version, lock, or provider-native feature that cannot be preserved as-is.
- **Conflict report**: duplicate identities, existing target objects, renamed/deleted source objects, concurrent updates, last-admin guards, membership mismatches, and required admin choices.
- **Rollback/retention report**: rollback feasibility, retained provider data, retention/legal-hold boundary, and manual remediation steps.

A provider switch may be shown to members only as stable capability state and impact copy. Admins/operators see the reports, readiness cards, and next actions in the Admin Console or operator evidence bundle.

## Keycloak desired-state dry-run direction

Sprint 8 identity work uses Keycloak as the concrete desired-state dry-run profile. The dry-run must compare a desired realm/client/role/group mapping with the current support-safe snapshot and report planned create/update/delete/no-op actions without mutating a live realm.

Minimum dry-run evidence:

- realm/client presence and redirect/origin policy status;
- role/group mapping to Weave capability profiles;
- last-admin and lockout protection;
- immutable subject strategy and email-rename handling;
- raw secret and provider-payload redaction;
- audit event names for dry-run and any future guarded apply.

Apply remains out of scope until readiness, approval, rollback, and audit gates exist.
