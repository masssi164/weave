# Sprint 12 closure report — Provider Portability & Lifecycle Readiness

Date: 2026-05-31
Milestone: Sprint 12 — Provider Portability & Lifecycle Readiness
Umbrella: #499

## Scope outcome

Sprint 12 is incorporated through PR #517, merged to protected `main` as commit `56b378d25d0ccc8d9304ecaa9f8f37c81cab13d2`.

The sprint added the provider-portability and lifecycle-readiness contracts needed before Weave can honestly promise provider replacement or later Weaver execution. It deliberately does not claim lossless migration, live production migration apply, broad Weaver runtime execution, or provider-specific UI ownership.

## Issue DAG final state

| Issue | Scope | Final state |
| --- | --- | --- |
| #500 | Provider portability schema v2 and adapter mapping reports | Closed by PR #517. |
| #501 | Dry-run evidence contract for Files, Calendar, Boards, and Chat | Closed by PR #517. |
| #502 | First Office/WOPI provider path and security model | Closed by PR #517 as ADR-only posture; Nextcloud-hosted WOPI with Collabora/CODE remains the first path, implementation gated by spike evidence. |
| #503 | Identity lifecycle: SCIM, reconcile, offboarding, recertification | Closed by PR #517 as executable server contract tests plus docs. |
| #504 | Matrix E2EE, export, retention, and support diagnostics boundaries | Closed by PR #517 as operator/product documentation and release evidence mapping. |
| #505 | Weaver sandbox and per-user runtime isolation ADR | Closed by PR #517 as ADR-only posture; Docker rootless alone is not sufficient for broader runtime claims. |
| #506 | Weaver signed skills/tools registry and SecretRef/OAuth contract | Closed by PR #517 as preflight fixtures/evidence while runtime execution remains disabled by default. |
| #507 | Permanent accessibility release-promotion gate | Closed by PR #517 through `release/accessibility-gate.json`, docs, and release gate checks. |
| #508 | Provider-aware backup, restore, upgrade, and schema migration contract | Closed by PR #517 through infra docs and release evidence checks. |
| #499 | Sprint 12 program umbrella | Closed after this closure report and release evidence are merged/published. |

Dependency order was architecture/contracts first (#500, #502, #505), executable evidence next (#501, #503, #506, #507, #508), support/operator boundaries alongside (#504, #508), then closure/release.

## Merged PRs

1. #517 — `feat: add Sprint 12 portability lifecycle contracts` (`release-notes-feature`), merged 2026-05-31 to `56b378d25d0ccc8d9304ecaa9f8f37c81cab13d2`.

## Evidence and gates

Local integration evidence before PR #517 merge:

- `./gradlew specCorpusConformance specContract domainRegistryCheck portabilityContractCheck acceptanceContract docsCheck releaseEvidenceCheck infraStatic --console=plain` — passed.
- `./gradlew clientCi --console=plain` — passed.
- `./gradlew serverCi adminCi --console=plain` — passed.

GitHub evidence:

- PR #517 `Release Notes Label Check` — passed with exactly one label: `release-notes-feature`.
- PR #517 `Gradle CI` — passed in run `26715802515`.
- Post-merge `main` CI is required before final release publication; record the run id in the release evidence when publishing the next RC.

## Release impact

Release-facing changes are documented in `docs/release-notes/unreleased.md` and include:

- provider portability schema v2 with loss classes `portable`, `lossy`, `unsupported`, `manual_review`, `vendor_locked`, and `archive_only`;
- no-unaccounted-data-loss dry-run evidence and support-safe portability artifacts;
- lifecycle/offboarding/recertification contracts;
- accessibility as a permanent release-promotion gate;
- honest ADR posture for Office/WOPI and Weaver runtime isolation.

Next release action: publish the next prerelease candidate from protected `main` after closure report merge and green `main` CI. Production release still requires explicit production approval and release-owner signoff.

## Non-goals and follow-ups

- No production provider migration apply path was enabled.
- No claim of lossless migration was introduced; the release promise is no unaccounted data loss.
- No broad Weaver runtime execution was enabled; stronger sandbox evidence such as gVisor/runsc or Firecracker remains required before widening runtime claims.
- Office/WOPI remains a documented provider path, not a shipped live Office editor surface.
