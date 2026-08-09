# Provider portability contract

Provider portability means Weave can replace or add provider adapters without changing the member-facing domain contract. It does not promise magical lossless migration. The release principle is **no unaccounted data loss**: every unsupported field, permission, object, or semantic difference must be reported, classified, and approved before an apply path is allowed.

## Provider Adapter Manifest

Every provider adapter must publish a support-safe manifest before it can back a product domain.

| Field | Requirement |
| --- | --- |
| `adapterKey` | Stable southbound adapter identifier, e.g. `matrix-synapse`, `openproject-primary`, `livekit`, or `nextcloud-files`; platform identity is not an adapter category. |
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

## Native OS integration boundary

Native OS integrations sit above the provider adapter layer. The governing decision is [Domain facade protocol projections](domain-facade-protocol-projections.md): Weave domain facades are product truth, while WebDAV, CalDAV/iCalendar, Matrix, OpenAPI, native OS extensions/providers, and MCP tools are projections or adapters over that truth.

## Keycloak realm lifecycle

Platform identity is not a provider-switch category. Keycloak is the fixed identity authority and follows the canonical lifecycle documented in [Keycloak realm lifecycle](keycloak-realm-lifecycle.md).

The static IAM model has one semantic source owned by `infra/weave-workspace/keycloak`. Candidate Cut binds only the environment-neutral semantic realm revision and migration-definition digest. Each environment then renders its own secret-free RealmRepresentation from that candidate definition plus environment coordinates and environment-owned public JWKS.

The post-import FGAP operation has two distinct qualification paths:

- **proven-empty Fresh Start / disposable E2E**: startup import establishes the new realm and the bounded FGAP operation is authorized by machine-verifiable empty-state or Fresh-Start plan/apply evidence; no artificial backup of the new empty realm is required;
- **existing non-empty dogfood/prod realm**: static IAM changes require an explicit versioned migration bound to current/target semantic digests, private backup, and isolated restore rehearsal before mutation.

In both cases the operation must finish with semantic readback, an empty second plan, deletion of temporary bootstrap authority, negative readback proving that authority is gone, and support-safe receipt evidence. Normal Server startup never reconciles static realm structure.

Minimum realm evidence:

- candidate `semanticRealmSourceDigest` and `migrationDefinitionDigest`;
- environment `overlayDigest` and `renderedRealmDigest`;
- realm/client/organization/group/role/scope/mapper/required-action/service-account posture;
- exact native Organization groups `/owners`, `/admins`, `/members`, `/guests` and `/capabilities/weaver`;
- workload-only client and service-account boundaries;
- semantic readback digest and `semanticReadbackVerified=true`;
- stable same-environment render evidence;
- raw secret, private key, token, cookie, SecretRef payload, and provider-payload redaction.

Routine startup verifies completed migration/readback evidence and starts product services. Destructive recovery, implicit secret rotation, general-purpose `kcadm`, startup reconciliation, and a second static IAM authority are explicitly not compatibility paths.

## Sprint 12 provider portability schema v2

Sprint 12 upgrades the portability vocabulary from coarse loss classes to field-level, machine-readable classes used by dry-run evidence, admin review, and release claim checks. The canonical classes are:

- `portable`: the field maps to a Weave canonical object without extra admin action.
- `lossy`: the field can be represented only with explicit loss in `LossyMappingReport`.
- `unsupported`: the target adapter cannot represent the field and apply remains blocked unless policy permits omission.
- `manual_review`: admin review is required before apply, usually because identity, permission, conflict, or member-impact context is incomplete.
- `vendor_locked`: the field is provider-owned and cannot be exported or replayed as a Weave canonical value.
- `archive_only`: the field is preserved in a support-safe archive but is not imported into the target provider.

The v2 contract is intentionally evidence-first: Weave promises **no unaccounted data loss**, not lossless migration. Release claims must not market “lossless migration”; every unsupported, lossy, vendor-locked, or archive-only field must be counted in support-safe evidence before apply can proceed.
