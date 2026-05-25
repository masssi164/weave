# Build, Evidence & Delivery System charter

Sprint 2 turns Weave's engineering loop into one professional, reproducible path: one root command, one task graph, one sanitized evidence model, and one documented GitFlow.

## North Star

Root Gradle is the build and delivery source of truth. `./gradlew ci` is the canonical local and CI command. Make remains only as a temporary compatibility shim during migration and must not contain long-term duplicated logic.

## Non-negotiables

- Same task names locally and in CI/CD.
- Evidence is generated as artifacts under `build/**`, not reconstructed from chat or raw logs.
- Default checks do not mutate tracked source; mutations happen only through explicit update tasks.
- Default checks require no secrets, live services, nondeterministic GitHub API calls, or remote CDN assets.
- Flutter/Dart and admin/npm checks are orchestrated through typed Gradle `Exec` tasks first; do not force Flutter Android into root Gradle prematurely.
- Live E2E remains opt-in and preserves explicit operator/budget confirmation semantics.

## Stable Gradle command surface

| Task | Contract |
| --- | --- |
| `doctor` | Fail fast with actionable missing-tool messages. |
| `acceptanceContract` | Verify Gherkin scenario mappings and acceptance contracts. |
| `clientCi` | Run the cheap Flutter/offline client gate. |
| `serverCi` | Run server tests. |
| `adminCi` | Run admin console checks. |
| `infraStatic` | Run static infrastructure checks. |
| `docsBuild` | Build user/admin manuals deterministically. |
| `releaseEvidenceCheck` | Validate release labels, README release markers, and generated-release evidence behavior. |
| `ci` | Canonical aggregate for default PR-safe checks. |

## Branch map for small PRs

Use short-lived branches from `origin/main`:

- `build/gradle-root-ssot` — Gradle wrapper/foundation, doctor, and Make delegation.
- `build/gradle-ci-parity` — parity tasks where `./gradlew ci` equals the current cheap gate set.
- `build/evidence-artifacts` — sanitized `build/evidence/ci-summary.json` and PR checklist integration.
- `docs/mkdocs-help-foundation` — pinned MkDocs user/admin manual build outputs.
- `release/evidence-automation` — release evidence checks, generated release-note artifacts, explicit README update task.
- `build/ci-gradle-migration` — GitHub Actions migration to `./gradlew ci` and least permissions.
- `build/make-transition-reduction` — remove or reduce Make after Gradle parity is proven.

## Stop condition

If `./gradlew ci` cannot prove parity with the existing cheap gates, Sprint 2 is blocked. Do not claim completion from partial local-only or hidden evidence.
