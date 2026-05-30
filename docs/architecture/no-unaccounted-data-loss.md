# No-unaccounted-data-loss portability contract

Weave does not promise that every provider replacement is perfectly lossless. It promises that migration loss is never unaccounted: every object and field must be classified, reported, approved where needed, and audit-linked before apply can be enabled.

## Loss classes

The `LossClass` enum is shared by every portability report.

- `lossless_canonical`: represented in the target provider through a canonical Weave object or field.
- `lossless_extension`: preserved as a support-safe extension or provider-specific sidecar.
- `archive_only`: preserved in an archive/export bundle but not active in the target provider.
- `lossy_with_report`: partially mapped and listed in a Lossy Mapping Report for explicit review.
- `blocked_nonportable`: cannot be migrated; apply is blocked until an admin chooses a safe outcome.
- `provider_unexportable`: source provider cannot export it; the report records evidence and impact.

## Contract schemas

Machine-readable schemas live under `server/src/main/resources/contracts/portability/`:

- `ProviderAdapterManifest`
- `ProviderMapping`
- `ExportManifest`
- `ImportManifest`
- `LossyMappingReport`
- `ConflictReport`
- `MigrationRun`
- `MigrationAuditRef`

`./gradlew portabilityContractCheck` validates that each schema exists, uses the canonical loss classes, requires counts/hashes/provider mapping/audit references, and keeps redaction support-safe.

## Dry-run gate

Provider migration apply is impossible until a successful dry-run report exists. A `MigrationRun` follows the canonical lifecycle `discovered`, `preflight_failed`, `preflight_passed`, `exported`, `dry_run_completed`, `blocked`, `approved`, `applying`, `applied`, `verified`, `rolled_back`, and `archived`. A run with `applyAllowed: true` must be in `approved`, `applying`, `applied`, or `verified` state and must include a non-empty dry-run report reference, provider mapping reference, object counts, content hashes, audit references, admin approval, rollback/archive boundary, and post-apply verification reference. Missing reports, conflicts, unclassified losses, incomplete identity mapping, unavailable audit sink, or unsafe redaction force `applyAllowed: false`.

## Support-safe redaction

Portability artifacts must contain support-safe identifiers and hashes, not raw provider tokens, credentials, internal endpoints, or opaque downstream error payloads. Raw provider payloads stay server/operator-side and must be redacted before they enter evidence, member preview, issue comments, or docs.
