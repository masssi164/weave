# No-unaccounted-data-loss portability contract

Weave does not promise that every provider replacement is perfectly lossless. It promises that migration loss is never unaccounted: every object and field must be classified, reported, approved where needed, and audit-linked before apply can be enabled.

## Loss classes

The Sprint 12 provider portability schema v2 `LossClass` enum is shared by every portability report.

- `portable`: represented in the target provider through a canonical Weave object or field.
- `lossy`: partially mapped and listed in a `LossyMappingReport` for explicit review.
- `unsupported`: cannot be represented by the target adapter; apply is blocked unless an approved policy chooses a safe outcome.
- `manual_review`: requires admin review before apply because identity, permission, conflict, or impact evidence is incomplete.
- `vendor_locked`: source provider cannot export or replay it as a Weave canonical value; the report records evidence and impact.
- `archive_only`: preserved in an archive/export bundle but not active in the target provider.

## Contract schemas

Machine-readable schemas live under `server/src/main/resources/contracts/portability/`:

- `ProviderAdapterManifest`
- `ProviderMapping`
- `ExportManifest`
- `ImportManifest`
- `ImportFeasibilityReport`
- `LossyMappingReport`
- `ConflictReport`
- `PermissionImpactReport`
- `ArchiveManifest`
- `RollbackRetentionReport`
- `MigrationRun`
- `MigrationAuditRef`

`./gradlew portabilityContractCheck` validates that each schema exists, uses the canonical v2 loss classes, requires counts/hashes/provider mapping/audit references, and keeps redaction support-safe.

## Dry-run gate

Provider migration apply is impossible until a successful dry-run report exists. A `MigrationRun` follows the canonical lifecycle `discovered`, `preflight_failed`, `preflight_passed`, `exported`, `dry_run_completed`, `blocked`, `approved`, `applying`, `applied`, `verified`, `rolled_back`, and `archived`. A run with `applyAllowed: true` must be in `approved`, `applying`, `applied`, or `verified` state and must include a non-empty dry-run report reference, provider mapping reference, object counts, content hashes, audit references, admin approval, rollback/archive boundary, and post-apply verification reference. Missing reports, conflicts, unclassified losses, incomplete identity mapping, unavailable audit sink, or unsafe redaction force `applyAllowed: false`.

## Attach-existing portability plan MVP

The attach-existing path starts with read-only discovery for organizations that already run a provider landscape and want Weave to reduce hyperscaler dependence without redeploying or destructively migrating current systems. `specs/0006-portability-contract/attach-existing-files-portability-plan-mvp.json` is the bounded Files-domain fixture for this product slice.

The fixture keeps exactly one active Files binding: the existing cloud Files adapter remains `active`, a second handle may be `discovery_read_only`, and the self-hosted/sovereign Files adapter remains `candidate` until separate migration evidence exists. Other canonical binding states remain available for future plans: `migration_source`, `migration_target`, and `coexistence_preflight`.

The AdapterMapper output is intentionally admin/operator-facing. It records a capability map, permission-impact report ref, loss report ref, conflict report ref, audit refs, a recommended self-hosted/sovereign target where the evidence supports it, and cutover/rollback next steps. Discovery mode must not rotate credentials, mutate the provider, import data, delete data, or claim release readiness. Member/App clients consume only stable provider-neutral capability states such as `available`, `degraded`, or `coming_later`; provider names, opaque configuration handles, report refs, and target recommendations stay behind Admin/Operator surfaces.

## Support-safe redaction

Portability artifacts must contain support-safe identifiers and hashes, not raw provider tokens, credentials, internal endpoints, or opaque downstream error payloads. Raw provider payloads stay server/operator-side and must be redacted before they enter evidence, member preview, issue comments, or docs.

The contract phrase is no-unaccounted data loss: no loss may be hidden, unclassified, or unaudited.
