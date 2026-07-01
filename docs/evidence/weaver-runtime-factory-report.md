# Sprint 24 Weaver Runtime Factory evidence

Status: Sprint 24 provider-lab evidence for issues #631-#634.

## Claim boundary

Sprint 24 proves a support-safe provider-lab Weaver Runtime Factory fixture for admin enablement, user opt-in, per-user Docker runtime lifecycle, health, audit, revoke, desired-state reconciliation, and per-user isolation checks. It does not claim production PA availability, customer-ready Weaver, release-ready Weaver, broad autonomous AI availability, unrestricted tools, raw provider access, or production sandbox strength.

## Issue evidence

| Issue | Evidence | Result |
| --- | --- | --- |
| #631 runtime instances | `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`, `release/provider-lab/weaver-runtime/per-user-runtime-proof.fixture.json` | Alice and Bob receive distinct support-safe runtime instances; disabling Alice stops Alice while Bob remains running. |
| #632 reconciliation | `WeaverRuntimeServiceTest.reconcilesCreateUpdateStopAndRevokeWithSupportSafeAudit`, `release/provider-lab/weaver-runtime/desired-state-reconciliation-proof.fixture.json` | Create, update, and revoke decisions include desired state, actual state, action, outcome, and support-safe audit payloads. |
| #633 isolation | `WeaverRuntimeServiceTest.blocksCrossUserWorkspaceReadsAndRedactsWeaverSupportBundle`, `release/provider-lab/weaver-runtime/per-user-isolation-proof.fixture.json` | Cross-user workspace/profile access is blocked; support bundles exclude Weaver memory, raw OpenClaw config, provider secrets, tokens, and raw payloads. |
| #634 claim gate | `release/provider-lab/weaver-runtime/sprint-24-claim-gate.fixture.json`, `release/provider-lab/weaver-runtime/sprint-24-scoreboard.json`, `tools/weaver_runtime_factory_check.py` | Scoped provider-lab runtime wording passes; broad Weaver availability, per-user PA availability, customer-ready, release-ready, and broad autonomous AI claims are rejected. |

## Evidence artifacts

- `release/provider-lab/weaver-runtime/per-user-runtime-proof.fixture.json`
- `release/provider-lab/weaver-runtime/desired-state-reconciliation-proof.fixture.json`
- `release/provider-lab/weaver-runtime/per-user-isolation-proof.fixture.json`
- `release/provider-lab/weaver-runtime/sprint-24-claim-gate.fixture.json`
- `release/provider-lab/weaver-runtime/sprint-24-scoreboard.json`
- `release/provider-lab/manifests/docker-runtime.json`
- `release/provider-lab/health-report.sample.json`

## Verification

- `cd server && ./gradlew test --tests com.massimotter.weave.backend.service.WeaverRuntimeServiceTest --console=plain`
- `./gradlew weaverRuntimeFactoryCheck --console=plain`
- `./gradlew serverCi acceptanceContract specContract productRealityClaimGateCheck releaseEvidenceCheck --console=plain`

## Remaining non-goals

- No production runtime/container mutation was performed.
- No live-infrastructure provider cutover or production release was performed.
- No customer-ready or release-ready Weaver claim is made.
