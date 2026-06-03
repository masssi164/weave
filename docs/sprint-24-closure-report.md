# Sprint 24 closure report — Weaver Runtime Factory

Status: ready for GitHub closure after PR merge and milestone verification.

## Governing sources

- GitHub milestone: Sprint 24 — Weaver Runtime Factory.
- Issues: #631, #632, #633, #634.
- `docs/product-reality-foundation.md` and `release/product-reality-gates.json`.
- `docs/product-trust-provider-choice-claim-matrix.md`.
- `docs/governed-weaver-runtime-security-contract.md`.
- `docs/architecture/adr-003-weaver-runtime-isolation.md`.
- `docs/sprint-23-closure-report.md`.

## Issue DAG and outcome

1. #631 runtime instances: proves admin enablement, user opt-in, RuntimeProfile creation, per-user runtime instance metadata, health, deactivation, labels, and audit evidence.
2. #632 reconciler: depends on runtime instance state and proves desired-state vs actual-state create, update, and revoke decisions with support-safe audit fields.
3. #633 isolation: depends on runtime/workspace boundaries and proves cross-user workspace/profile blocking plus support-bundle redaction for Weaver memory, raw OpenClaw config, provider secrets, and tokens.
4. #634 claim gate: depends on #631-#633 evidence and blocks broad Weaver availability, per-user PA availability, customer-ready, release-ready, and broad autonomous AI wording.

## Shipped evidence

| Issue | Evidence refs | Gate |
| --- | --- | --- |
| #631 | `release/provider-lab/weaver-runtime/per-user-runtime-proof.fixture.json`, `WeaverRuntimeServiceTest.provisionsDistinctPerUserRuntimeInstancesAndStopsOnlyDisabledUser` | `serverCi`, `weaverRuntimeFactoryCheck` |
| #632 | `release/provider-lab/weaver-runtime/desired-state-reconciliation-proof.fixture.json`, `WeaverRuntimeServiceTest.reconcilesCreateUpdateStopAndRevokeWithSupportSafeAudit` | `serverCi`, `weaverRuntimeFactoryCheck` |
| #633 | `release/provider-lab/weaver-runtime/per-user-isolation-proof.fixture.json`, `WeaverRuntimeServiceTest.blocksCrossUserWorkspaceReadsAndRedactsWeaverSupportBundle` | `serverCi`, `weaverRuntimeFactoryCheck` |
| #634 | `release/provider-lab/weaver-runtime/sprint-24-claim-gate.fixture.json`, `release/provider-lab/weaver-runtime/sprint-24-scoreboard.json`, `tools/weaver_runtime_factory_check.py` | `releaseEvidenceCheck`, `productRealityClaimGateCheck` |

## Claim boundary

Allowed wording is limited to the Sprint 24 support-safe provider-lab runtime factory fixture. The merged evidence does not claim production PA availability, customer-ready Weaver, release-ready Weaver, broad autonomous AI availability, unrestricted tools, raw provider access, production sandbox strength, or live infrastructure mutation.

## Verification plan

Required local/CI gates for closure:

- `cd server && ./gradlew test --tests com.massimotter.weave.backend.service.WeaverRuntimeServiceTest --console=plain`
- `./gradlew serverCi acceptanceContract specContract productRealityClaimGateCheck releaseEvidenceCheck --console=plain`
- `./gradlew weaverRuntimeFactoryCheck --console=plain`

## Release and RC impact

- Release notes: guarded Sprint 24 entry added to `docs/release-notes/unreleased.md`.
- Provider-lab reality: `docker-runtime` is promoted only to `configured` provider-lab fixture evidence, not `release_ready`.
- Release claim controls remain strict: only `release_ready` may be customer-ready for a named scope.

## Remaining risks / non-goals

- No production runtime/container mutation was performed.
- No production provider cutover, migration apply, rollback, or release publication was performed.
- Runtime evidence is fixture/server-contract evidence; stronger production sandbox/runtime proof remains future work.
- Sprint 25 can start from versioned RuntimeProfile customization and rollback behavior without reopening Sprint 24 lifecycle/isolation/claim-gate basics.

## GitHub closure checklist

After PR merge to `main`:

- Close #631, #632, #633, and #634 with evidence refs.
- Verify the Sprint 24 milestone has zero open issues.
- Verify the closure report exists on `origin/main`.
- Verify final `main` CI is green.
- Close the Sprint 24 milestone so Sprint 25 can start.
