# Sprint 20 closure report — North-Star Release Trust & Operator Readiness

Date: 2026-06-02

Status: **BLOCKED for full sprint closure, but above the hard North-Star exit threshold**. Current-head local gates and Live Stack E2E now prove an honest score above 80%; the sprint must still not be closed as fully complete while #591/manual AT evidence is missing and PR/issue reconciliation is open.

## Governing specs

- Product/spec truth remains pinned by `specs/weave-specs.lock.json` with the local default corpus `/Users/flotterotter/code/weave-specs`.
- Product direction remains `docs/product-line-and-weaver-plan.md`: Weave is the provider-neutral organization suite first, admin/provider readiness second, and Weaver is an optional governed per-user PA runtime later.
- Sprint 20 milestone: `Sprint 20 — North-Star Release Trust & Operator Readiness` (`https://github.com/masssi164/weave/milestone/20`).
- Sprint 20 issues: #608, #609, #610, #611, #612, and #613.
- Carryover release blocker: #591 `epic(trust): complete manual AT release signoff and claim control`.

## North-Star maturity scoreboard

Machine-readable scoreboard path: `release/north-star-maturity-scoreboard.json`.

Current evidence-backed score: **82 / 100**.

Target score: **85 / 100**.

Hard exit threshold: **more than 80 / 100**.

Result: **above the hard exit threshold, blocked below the desired 85 target by #591/manual AT evidence**. Current-head Live Stack E2E completed successfully and its support-safe manifest is recorded in this report. The sprint remains below 85 while #591 lacks real manual assistive-technology pass evidence.

| Dimension | Weight | Current score | Conditional score | Evidence class | Rationale |
| --- | ---: | ---: | ---: | --- | --- |
| Product flow | 15 | 14 | 14 | Current-head offline/spec + local CI | Member Home-to-Space control-room loop is strongly covered by client tests and acceptance mappings. |
| Current-head release evidence | 12 | 11 | 11 | Current-head CI + passed live workflow | Local `./gradlew ci` passed on the candidate commit and Live Stack E2E run `26828148937` passed on the same commit. |
| Manual AT/accessibility signoff | 12 | 2 | 2 | Blocked manual evidence | #591 remains open. Automation and waivers do not count as manual AT pass evidence. |
| Provider portability/cutover honesty | 11 | 9 | 9 | Current-head offline/spec + contract evidence | Backend/admin dry-run evidence supports bounded, support-safe provider-switch claims without apply/cutover overclaiming. |
| Space control room dogfoodability | 10 | 9 | 9 | Current-head offline/spec + client CI | Space control-room tabs and stable states remain covered; live-runtime credit waits on Live Stack E2E. |
| Governed Weaver runtime safety | 10 | 8 | 8 | Current-head offline/spec + backend/admin/infra contract evidence | RuntimeProfile/tool-registry governance is covered; no broad live autonomous runtime claim is made. |
| Claim control | 10 | 9 | 9 | Current-head release evidence checks | Release wording and matrices remain fail-closed for unsupported public, production, migration, AT, Weaver, and operator claims. |
| Backup/restore/operator readiness | 10 | 8 | 8 | Current-head fixture/offline operator evidence | Backup/restore/support-bundle scripts and redaction tests are covered; live restore rehearsal is not claimed. |
| Scenario/live-E2E coverage | 10 | 9 | 9 | 42/42 mapping + current-head live runtime | Acceptance mapping remains 42/42 with 9 live-runtime and 33 offline-spec scenarios; Live Stack E2E passed. |

## Evidence classification

### Current-head evidence

- Candidate commit: `3ab9b3eaa09539f62200963c5bd5aeac01a7847c` (`3ab9b3e`).
- Local CI: `./gradlew ci --console=plain` passed locally and wrote `build/evidence/ci-summary.json` with `generatedAtUtc: 2026-06-02T14:59:39Z`, `commit: 3ab9b3eaa09539f62200963c5bd5aeac01a7847c`, and `build.result: passed`.
- Current-head gate coverage in that CI summary: `doctor`, `acceptanceContract`, `clientCi`, `serverCi`, `adminCi`, `infraStatic`, `docsCheck`, `releaseNotesLabelCheck`, `specContract`, `specContractTest`, `domainRegistryCheck`, `portabilityContractCheck`, `productTrustClaimMatrixCheck`, `androidReleaseIdentityCheck`, `adminDependencyPolicyCheck`, `releaseEvidenceCheck`, and `projectReadinessEvidenceCheck` passed.
- Additional local release-readiness command with explicit current-head CI summary and downloaded support-safe Live Stack artifact: `python3 tools/release_readiness_check.py --candidate-version 0.1.0-rc.1 --candidate-tag v0.1.0-rc.1 --candidate-commit 3ab9b3eaa09539f62200963c5bd5aeac01a7847c --ci-summary build/evidence/ci-summary.json --blockers-json build/evidence/release-blockers.json --live-evidence-dir weave-live-stack-acceptance-evidence`.
  - Result: `RC readiness: ready` for CI summary, release notes, offline pointers, Live Stack manifest, supplied blocker summary, and support-safe scans. This does not close GitHub #591 because the supplied local blocker summary is not authoritative for current GitHub issue state.
- Additional local gates: `./gradlew specCorpusConformance spaceAnchorCheck enterpriseReleaseGateCheck releaseReadinessCheck --console=plain` passed all listed gates except `releaseReadinessCheck`, which blocks on missing authoritative CI summary when not passed explicitly and missing Live Stack manifest. Re-running the readiness script with the current-head `build/evidence/ci-summary.json` clears the CI-summary blocker and leaves only Live Stack.
- GitHub Live Stack E2E: run `26828148937`, URL `https://github.com/masssi164/weave/actions/runs/26828148937`, branch `main`, head `3ab9b3eaa09539f62200963c5bd5aeac01a7847c`, job `Bootstrap Stack And Run App E2E`, runner `weave-live-mac-mini`, conclusion `success`.
- Live artifact: `7361726575` named `weave-live-stack-acceptance-evidence`, containing `release-evidence-manifest.json`, `acceptance-summary.md`, `scenario-mapping-results.json`, `evidence-markers.json`, and `gherkin-scenarios.json`. Manifest fields: `schemaVersion: 1`, `lane: release-candidate-live-evidence`, `runtimeEvidenceCollected: true`, `scenarioCount: 42`, `mappingCount: 42`, `liveRuntimeMappingCount: 9`, `offlineSpecMappingCount: 33`, `findings: []`, `supportSafe: true`.

### Current-head offline/spec evidence

- `e2e/scenario_mappings.json` contains 42 scenarios: 9 `live-runtime` and 33 `offline-spec`.
- Key Sprint 20 markers remain mapped:
  - `V01_SPACE_CONTROL_ROOM`
  - `V01_OPERATOR_RELEASE_PATH`
  - `V01_PROVIDER_SWITCH_PORTABILITY`
  - `V01_GOVERNED_WEAVER_TOOL_REGISTRY`
- Operator readiness current-head offline evidence:
  - `infra/weave-workspace/backup.sh`
  - `infra/weave-workspace/restore-smoke.sh`
  - `infra/weave-workspace/support-bundle.sh`
  - `infra/weave-workspace/tests/restore-smoke-artifacts-test.sh`
  - `infra/weave-workspace/tests/support-bundle-redaction-test.sh`
  - `infra/acceptance/operator_support_safety.feature`
  - `infra/docs/operator-runbook.md`
- Provider-switch honesty current-head offline evidence:
  - `docs/provider-replacement-and-anti-silo-contract.md`
  - `docs/product-trust-provider-choice-claim-matrix.md`
  - `server/src/main/java/com/massimotter/weave/backend/provider/ProviderCapabilityContracts.java`
  - `server/src/main/java/com/massimotter/weave/backend/provider/ProviderReplacementDryRunResponse.java`
  - `server/src/test/java/com/massimotter/weave/backend/controller/AdminControlPlaneControllerTest.java`
  - `admin-console/src/App.test.tsx`
- Governed Weaver current-head offline evidence:
  - `specs/0007-governed-weaver-runtime/spec.md`
  - `specs/0007-governed-weaver-runtime/traceability.yaml`
  - `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`
  - `server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java`
  - `infra/weave-workspace/tests/weaver-runtime-lifecycle-contract-test.sh`
  - `docs/governed-weaver-runtime-security-contract.md`

### Manual evidence

- Manual AT release signoff remains **missing**.
- #591 is open and release-blocking: `https://github.com/masssi164/weave/issues/591`.
- #608 cannot claim real manual AT pass evidence unless a human reviewer records reviewer, date, exact commit/build, route, role, assistive technology, browser/device, keyboard result, screen-reader result, text-scale/reflow result, support-safety result, and linked blocker for each failed row.

### Historical evidence

- Sprint 18, Sprint 19, RC3, and older Live Stack evidence remains historical unless rerun on exact candidate commit `3ab9b3eaa09539f62200963c5bd5aeac01a7847c`.
- Historical evidence can explain risk posture and continuity, but does not earn current-head Live Stack or manual AT pass credit.

### Blocked evidence

- Manual AT signoff: #591 remains open.
- Live restore rehearsal: operator scripts are deterministic offline, but no approved dogfood restore rehearsal is claimed.

## Issue DAG and current state

1. #608 `epic(trust): complete manual AT signoff and current-head release evidence`
   - Depends on #591 and current-head Live Stack E2E.
   - State: **blocked by #591 manual AT evidence**.
   - Current-head Live Stack E2E run `26828148937` passed and is counted as live release evidence.
   - Smallest next action: Human AT reviewer executes the #591 checklist on exact candidate build and records support-safe results.

2. #609 `epic(workspace): prove the daily Space control room as a current-head product loop`
   - Depends on current-head Live Stack for release-grade live credit and #591 for manual AT release signoff.
   - State: **offline/spec, local CI, and current-head live evidence satisfied; manual release evidence blocked by #591**.
   - Evidence: `clientCi`, `acceptanceContract`, Space control-room tests, `V01_SPACE_CONTROL_ROOM` mapping.

3. #610 `epic(operator): make backup restore and support bundles deterministic`
   - State: **offline/operator script determinism satisfied; live restore rehearsal not claimed**.
   - Evidence: backup/restore/support-bundle scripts, artifact-shape restore test, support-bundle redaction test, `infraStatic`.
   - Smallest stronger-evidence action: on an approved non-production operator host, run backup, restore-smoke preflight, and support bundle generation; record support-safe artifact refs without secrets.

4. #611 `epic(portability): harden provider switch honesty across domains`
   - State: **bounded provider-switch honesty evidence satisfied for current repo scope; production apply/cutover not claimed**.
   - Evidence: provider replacement dry-run backend/admin tests, provider capability contracts, `portabilityContractCheck`, `serverCi`, `adminCi`.
   - Remaining boundary: direct executable dry-run assertions are strongest for Chat switch with cross-domain impact into Files/Boards/Calendar; this does not claim separate production cutovers for every domain.

5. #612 `epic(weaver): tighten governed runtime safety for dogfood use`
   - State: **offline/spec/backend/admin/infra governance evidence satisfied; live runtime execution not claimed**.
   - Evidence: RuntimeProfile service tests, tool registry tests, infra lifecycle contract test, support-bundle redaction, governed Weaver security contract.
   - Remaining boundary: no signed external OpenClaw runtime loader enforcement or broad autonomous runtime availability is claimed.

6. #613 `epic(evidence): publish the North-Star maturity scoreboard and scenario gates`
   - State: **draft scoreboard published in repo; final closure blocked by Live Stack result and #591 accounting**.
   - Evidence: `release/north-star-maturity-scoreboard.json`, this report, acceptance mapping summary.

Dependency shape:

- #610, #611, #612, and the offline parts of #609 are parallel evidence-hardening tracks.
- #613 integrates all evidence and depends on the final state of #608/#609/#610/#611/#612.
- #608 is the release-trust gate and remains blocked by #591 and current-head Live Stack E2E.

## Boundaries and non-claims

- No production release, public release publication, production provider cutover, provider apply, live infrastructure mutation, destructive restore, secrets disclosure, raw provider diagnostics, private member content, history rewrite, or product-direction change was performed.
- No manual AT pass evidence is claimed.
- No waiver is counted as manual AT credit.
- No stale RC, Sprint 18, Sprint 19, or old Live Stack evidence is counted as current-head release proof.
- No full Files/Boards/Calendar/Meetings/Decisions provider parity is claimed beyond current evidence.
- No broad Weaver autonomous runtime availability, unrestricted tools, raw OpenClaw access, or background-agent write capability is claimed.

## Closure gate status

GitHub closure gate is **not met**:

- Sprint 20 milestone still has open issues.
- #591 remains open and release-blocking.
- The North-Star score is currently 82, above the hard exit threshold, but below the desired 85 target because #591 manual AT evidence is missing.

Next safe action: wait for run `26828148937` to conclude, ingest only support-safe evidence if it passes, update `release/north-star-maturity-scoreboard.json` and this report to a final score, then decide issue/PR closure. If it fails or remains stuck, keep Sprint 20 blocked with the exact runner/workflow blocker.
