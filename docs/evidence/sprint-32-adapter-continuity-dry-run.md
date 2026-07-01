# Sprint 32 adapter continuity dry-run evidence

Issue: #831
Domain path: Boards/Tasks provider continuity dry-run

The server migration dry-run response now includes a deterministic `continuityReports` entry for the `boards` domain. It records canonical object counts, stable ID/mapping strategy, provenance references, lossy-field and permission-impact reports, conflicts, unsupported objects, abort/rollback posture, and a boolean no-unaccounted-data-loss assertion.

Release evidence reference: `migration:{jobId}:boards:no-unaccounted-data-loss-report` in the persisted migration run artifact refs.

Validation:

- `MigrationDryRunServiceTest.dryRunIncludesSupportSafeChatAndFilesMappingEvidence` asserts the Boards continuity report content and support-safe provenance.
- `./gradlew serverCi`
- `./gradlew acceptanceContract`

Boundary:

This is dry-run/report evidence only. It performs no target writes and does not claim lossless live provider replacement. Apply remains blocked until admin approval, identity mapping, audit sink, rollback archive, and restore-smoke evidence are present.
