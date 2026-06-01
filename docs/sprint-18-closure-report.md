# Sprint 18 closure report — Masterpiece Loop: Live Workspace, Migration Proof & Release Trust

Date: 2026-06-01

## Governing specs

- Product/spec truth remains pinned by `specs/weave-specs.lock.json` and validated by `./gradlew specCorpusConformance --console=plain` against `/Users/flotterotter/code/weave-specs` at commit `24c746c674da`.
- Sprint delivery maps to GitHub milestone 18 and issues #589, #590, #591, and #592. Evidence is carried by repo paths, gates, and PR/CI status rather than unreviewed transcript memory.

## Scope delivered

- #589 Workspace loop: the Live Stack E2E path now exercises and records a provider-neutral Space/Channel → Chat → Files → Board → Calendar → Decision loop with support-safe markers including `workspaceLoopComplete`, `workspaceLoopDecisionId`, and `workspaceLoopSupportSafe`. The evidence mapping keeps this as Weave domain/facade language and blocks RC promotion when required markers are missing.
- #590 Bounded Matrix Chat migration proof: the Admin Control Plane and migration services now expose dry-run evidence, bounded fixture-only apply/cutover/rollback posture, no-unaccounted-data-loss reporting, release blockers, redaction/retention posture, required evidence refs, and fail-closed apply gates. Production provider cutover remains explicitly blocked.
- #591 Accessibility and release trust: the release evidence gate now distinguishes blocked manual AT evidence from ceremonial signoff, links the Sprint 18 manual AT blocker, and upgrades claim-control checks so open blockers, missing support-safe evidence, and unsupported claims block release readiness.
- #592 Governed Weaver/MCP hardening: RuntimeProfile projection now includes signed/fetch-by-hash posture, same-user hash lookup, runtime token/ref expiry boundaries, per-user/org policy checks, internal endpoint refs, overbroad grant rejection, fail-closed MCP invocation, and support-safe audit refs without raw profile/token/provider leakage.

## Implementation evidence

- Workspace/live E2E: `client/integration_test/live_stack_app_e2e_test.dart`, `client/test/live_stack_feature_mapping_test.dart`, `e2e/features/live_stack_app.feature`, `e2e/scenario_mappings.json`, `server/src/main/java/com/massimotter/weave/backend/controller/WorkspaceController.java`.
- Migration proof: `server/src/main/java/com/massimotter/weave/backend/service/AdminControlPlaneService.java`, `server/src/main/java/com/massimotter/weave/backend/service/migration/MigrationDryRunService.java`, `server/src/main/java/com/massimotter/weave/backend/service/migration/MigrationApplyGateService.java`, `server/src/main/java/com/massimotter/weave/backend/model/admin/ProviderReplacementDryRunResponse.java`, `server/src/test/java/com/massimotter/weave/backend/service/migration/MigrationApplyGateServiceTest.java`, `specs/0006-portability-contract/matrix-synapse-chat-bounded-apply-cutover-rollback-proof.json`, `docs/matrix-chat-migration-proof.md`.
- Release trust/accessibility: `release/accessibility-gate.json`, `docs/accessibility-release-gate.md`, `docs/evidence/accessibility/sprint-18-manual-at-blocker.md`, `docs/product-trust-provider-choice-claim-matrix.md`, `tools/release_gate_check.py`, `tools/release_trust_claim_control_check.py`.
- Weaver/MCP: `server/src/main/java/com/massimotter/weave/backend/service/WeaverRuntimeService.java`, `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`, `infra/weave-mcp/src/weave_mcp/schemas/common.py`, `infra/weave-mcp/tests/test_weave_mcp.py`.

## Gates run locally

Passing on 2026-06-01 before PR creation:

- `./gradlew serverCi --console=plain --rerun-tasks`
- `./gradlew adminCi --console=plain`
- `./gradlew acceptanceContract specContract portabilityContractCheck releaseEvidenceCheck --console=plain`
- `./gradlew docsCheck --console=plain`
- `./gradlew infraStatic --console=plain`
- `./gradlew specCorpusConformance --console=plain`
- `./gradlew clientCi --console=plain` after committing the Sprint 18 source diff

## Issue DAG final state

- #589 — closed by #593: workspace loop markers, Live Stack mapping, and support-safe evidence.
- #590 — closed by #593: bounded fixture-only apply/cutover/rollback proof, no production cutover claim.
- #591 — closed by #593: release trust gates plus explicit manual AT blocker evidence.
- #592 — closed by #593: signed/fetch-by-hash RuntimeProfile and fail-closed MCP posture.

## PR / CI / milestone status

- Delivery branch: `feat/s18-workspace-live-e2e-589`.
- Merged PR: #593, `feat: prove Sprint 18 workspace migration trust loop`, with exactly one release-notes label: `release-notes-feature`.
- Merge commit on `main`: `5b0fc458fc75e0ccfddab2dba0c7b8b1f2553eb6`.
- PR checks at merge: 6 successful, 0 requiring attention.
- GitHub issues #589, #590, #591, and #592 are closed.
- GitHub milestone 18 is closed with 0 open issues and 4 closed issues.

## Boundaries and non-claims

- No production Matrix/provider cutover, live infra mutation, Terraform/live service change, RC tag, public release, or production release was performed.
- The migration proof is a deterministic bounded/local fixture proof named `fixture_only_matrix_synapse_chat_sprint18`; it does not claim production provider replacement readiness.
- Manual assistive-technology release signoff remains blocked by `docs/evidence/accessibility/sprint-18-manual-at-blocker.md`; release gates correctly prevent treating this as completed AT evidence.
- Runtime profiles expose only support-safe refs/receipts. Raw OpenClaw/provider secrets, raw MCP endpoints, raw downstream payloads, OAuth refresh tokens, cookies, and credentials remain out of member/admin profiles and release artifacts.

## Remaining risks / carryovers

- Actual manual AT evidence must be collected before public/production release signoff.
- Production Matrix cutover requires explicit Massimo approval and fresh live-provider evidence beyond this bounded Sprint 18 proof.
- Broader runtime rollout still requires production-grade isolation evidence before stronger sandbox claims.
