# Chat Collaboration Blocker Study

Status: implementation decision recorded; promotion remains frozen pending exact-candidate live and physical-device evidence

Study date: 2026-07-18

Primary implementation classification: `ci-e2e`

Research classification: `investigation-only`
Frozen input: draft PR [#1153](https://github.com/masssi164/weave/pull/1153) at `0bd03616d850894d54f86207c9b6a037e5f4256c`

## Executive decision

The principal collaboration blocker is canonical bridge-ledger fault containment and recovery, not another public Matrix route or provider-selection defect. One conversation mapping can currently be promoted into provider-wide Chat failure, while completed callback deduplication and insert-only quarantine make a false-positive classification irreversible. The same transition can make later valid siblings appear unmapped.

The selected remediation preserves Weave-owned identities and provider interchangeability:

- provider, conversation, and operation health remain separate;
- an affected conversation fails closed locally while unrelated collaboration and provider proof continue;
- callback identity remains homeserver transaction ID plus provider event identity, with a versioned semantic event-set fingerprint used only to detect retry disagreement;
- private quarantine has bounded, exactly-once reconciliation and never exposes raw provider data;
- Matrix/Synapse compatibility is a versioned profile with pinned and candidate fixtures rather than a reactive global outage allowlist;
- live application, collaboration, provider proof, cleanup, and teardown outcomes remain independently attributable while one failed mandatory outcome keeps the aggregate red.

No public route, northbound schema, auth claim, canonical identifier, federation setting, or provider-selection contract changes. The governing clarification is `WEAVE-ADR-0007` in the pinned specification corpus.

## Promotion freeze

- PR #1153 stays draft and frozen at the commit above. Its remote branch is not amended by this study.
- Promotion PR [#1138](https://github.com/masssi164/weave/pull/1138) stays blocked and stale.
- No persistent dogfood deployment, promotion, federation change, or production mutation is authorized by this study.
- A new implementation package may be reviewed independently, but it cannot resume `feature -> dev -> dogfood` until the conditions in the final section are met.

## Authoritative recent failure timeline

Every listed run used the disposable Live Stack lane; cleanup/teardown evidence is called out separately so an application failure is not mistaken for leaked persistent state.

| Run and candidate | First classified failure and original signal | What independently passed | Disposition |
| --- | --- | --- | --- |
| [29590801983](https://github.com/masssi164/weave/actions/runs/29590801983), `248b5e6f6dcf` | Cleanup, `M_WEAVE_LIVE_MATRIX_REDACT_EVENT_HTTP_403`; an ordinary member attempted to redact another sender's event. | Earlier collaboration state remained inspectable. | Confirmed orchestration defect; sender-owned cleanup is the correct fix. |
| [29602696211](https://github.com/masssi164/weave/actions/runs/29602696211), `0634793ece74` | Files denial classification and an invalid outsider-owned provider fixture. | Encrypted collaboration and sender-owned redaction. | Superseded fixture defects. |
| [29607223547](https://github.com/masssi164/weave/actions/runs/29607223547), `f1070b8c54ff` | Both passes failed `outsider-chat-authorization`; provider proof correctly rejected a cross-context invitation with `403`. | All single-user E2EE gates, Calendar containment/recovery, Matrix snapshots, identity cleanup, teardown, and zero owned resources. | Superseded authorization-fixture defect; isolation evidence was valid. |
| [29611366996](https://github.com/masssi164/weave/actions/runs/29611366996), `9d3907ba17b6` | Provider proof `provider-recovery-timeout`; container process state was treated as provider readiness. | Application, both collaboration passes, Calendar, Matrix snapshots, cleanup, teardown. | Confirmed proof-orchestration defect; authenticated readiness is authoritative. |
| [29614963452](https://github.com/masssi164/weave/actions/runs/29614963452), `c8baac9762ea` | Provider proof `provider-evidence-counts-invalid`; the pre-retry snapshot requested three correlations for two committed events. | Application, collaboration, Calendar, cleanup, teardown. | Confirmed proof-count defect; superseded. |
| [29618205926](https://github.com/masssi164/weave/actions/runs/29618205926), `e16b453dddcd` | `callback-replay-failed`; capture readiness was inferred from aggregate counters. | Application, E2EE lifecycle, collaboration twice, Calendar, cleanup, teardown. | Confirmed capture-observability defect; superseded. |
| [29621130896](https://github.com/masssi164/weave/actions/runs/29621130896), `be968cd4e742` | `callback-capture-timeout`; a retried homeserver transaction was rejected because volatile `unsigned.age` changed under raw digest comparison. | Complete application/E2EE/collaboration, sender-owned cleanup, Calendar, identity cleanup, teardown. | Raw-payload retry identity disproven. |
| [29623501851](https://github.com/masssi164/weave/actions/runs/29623501851), `daf329f64da2` | `callback-capture-timeout`; Synapse also projected recalculated age at top level. | Every client, E2EE, collaboration, cleanup, Calendar, identity cleanup, teardown phase. | Enumerating presentation fields disproven as a durable solution. |
| [29625566269](https://github.com/masssi164/weave/actions/runs/29625566269), `eb6f38e495aa` | `room-device-provision` / `M_WEAVE_E2EE_ROOM_MEMBERS`; independent provider creation returned `503`. | Matrix snapshots, identity cleanup, teardown, outcome recording. | First evidence that one ordered callback queue fault blocked unrelated setup. |
| [29627193458](https://github.com/masssi164/weave/actions/runs/29627193458), `a17c19212431` | Same `M_WEAVE_E2EE_ROOM_MEMBERS` plus independent `create-room-status-503`. | Matrix snapshots, Calendar recovery, cleanup, teardown. | Reproduced; not transient routing. |
| [29628117267](https://github.com/masssi164/weave/actions/runs/29628117267), `a17c19212431` | Room creation returned `200`; first ordered callbacks returned expected race-time `503`, retried to `200`, then one `provider-state-event-type-unsupported` quarantine degraded the acknowledged mapping. The event was legitimate `m.room.canonical_alias`. Global readiness then caused member and provider-proof `503`s. | Matrix snapshots, Calendar containment/recovery, identity cleanup, stack teardown, zero owned resources. | Decisive root-cause run: classifier false positive plus canonical-ledger blast-radius defect. |

Ordinary PR CI is green at the frozen head, but no Live Stack run exists for `0bd03616d850894d54f86207c9b6a037e5f4256c`. Ordinary CI therefore does not close the live blocker.

## Canonical ledger state-machine audit

### Existing behavior

| Scope | Existing states | Confirmed gap |
| --- | --- | --- |
| Provider | cached authenticated readiness, then overridden by degraded-mapping count | One mapping can block all tenants and mask independent transport/auth evidence. |
| Conversation mapping | `pending -> acknowledged -> degraded` | Identity and health are conflated; there is no healing transition. |
| Operation | `pending -> failed_retryable -> committed` | Correctly distinct, but global readiness can prevent unrelated operations from entering it. |
| Callback transaction | absent -> `processing -> completed`; completed retry no-ops | Stored semantic digest/count were not compared on resume/replay. |
| Quarantine | insert-only observation | No lifecycle, classifier version, bounded attempt, reclassification, or reconciliation. |

### Focused hypotheses

| Hypothesis | Result | Required regression proof |
| --- | --- | --- |
| One degraded conversation makes Chat unavailable globally. | Confirmed, including cross-tenant blast radius. | Degrade conversation A; B and another tenant still create/list/read/send while A fails locally. |
| Provider proof is blocked by unrelated mapping degradation. | Confirmed through tenant/provider-wide evidence counts and earlier facade gating. | Target-scoped proof ignores unrelated mapping faults while the final mandatory verdict still records them. |
| Valid siblings survive a partially invalid callback exactly once. | Disproved for bad-first/valid-second events in the same room; degradation makes the later sibling look unmapped. | Run every valid/invalid ordering; every valid event commits once. |
| Completed callback replay can heal after a classifier fix. | Disproved; correct duplicate no-op plus insert-only quarantine strands the mapping. | Classifier N quarantines, N+1 reconciles privately, commits/heals once, later retries remain no-ops. |
| Transaction-ID replay remains safe if semantic events change. | Disproved; digest/count were persisted but not compared. | Presentation drift is accepted; changed semantic event set under the same ID fails closed. |
| Canonical alias support alone fixes the outage class. | Disproved. It fixes the observed event but leaves global blast radius and irreversibility intact. | Supported state does not degrade; unknown valid state degrades only its conversation and is recoverable. |

### Selected internal model

Provider health covers authentication, reachability, supported capability, profile-wide schema compatibility, and systemic ledger integrity. Conversation health covers one mapping/correlation/encryption violation. Operation health covers one pending, retryable, failed, or committed write.

Private quarantine states are `pending`, `reconciled`, `rejected`, and `superseded`. Records carry stable reason/category, recoverability, classifier version, observation time, bounded attempts, a support-safe correlation hash, and private-only event/transaction material needed to revalidate or refetch. Shared evidence exposes none of that private material.

Reconciliation is explicit rather than a callback replay side effect. It applies a newer classifier to a bounded pending batch, enters the normal provider-event uniqueness boundary, makes the quarantine terminal, and heals only when no unresolved conversation violation remains.

## Client crypto and SDK patch audit

The current `matrix-sdk-crypto` version remains 0.18.0, which is also the current published Matrix Rust SDK release at the study date. The full crate remains temporarily vendored with checksum provenance, but the behavioral delta is now represented as a sequential patch series that must reconstruct the vendored source exactly.

| Patch/invariant | Upstream dependency | Focused proof | Disposition |
| --- | --- | --- | --- |
| Outbound encryption does not advance the successful-decrypt timestamp. | [#3356](https://github.com/matrix-org/matrix-rust-sdk/issues/3356), [#3354](https://github.com/matrix-org/matrix-rust-sdk/issues/3354) | `olm::session::tests::test_encryption_and_decryption` | Retain temporarily with provenance; submit upstream; replace with an accepted release. |
| Session choice prefers the most recent successful decrypt, with creation time only as tie-breaker. | [#3356](https://github.com/matrix-org/matrix-rust-sdk/issues/3356), [#3354](https://github.com/matrix-org/matrix-rust-sdk/issues/3354) | `identities::device::tests::selects_session_with_latest_successful_decrypt` | Retain temporarily; submit as an independently reviewable upstream patch. |
| First observed wedge can claim immediately without a prior session; cooldown is persisted across manager recreation. | [#3427](https://github.com/matrix-org/matrix-rust-sdk/issues/3427), [#2864](https://github.com/matrix-org/matrix-rust-sdk/issues/2864), [#3354](https://github.com/matrix-org/matrix-rust-sdk/issues/3354) | `test_session_unwedging`, `test_first_unwedge_without_an_existing_session`, `test_unwedge_cooldown_survives_manager_recreation` | Retain temporarily; submit upstream; remove override only after release plus two-pass live proof. |

Duplicate one-time-key disagreement remains visible and fail-closed; synthetic success is forbidden while [#6520](https://github.com/matrix-org/matrix-rust-sdk/issues/6520) is open. Cross-process crypto-store invalidation/cursor ownership remains unresolved while [#6681](https://github.com/matrix-org/matrix-rust-sdk/issues/6681) is open.

The source crate's focused tests require two benchmark fixtures omitted from the published crate archive. This study ran them in a disposable tree with the exact files from upstream commit `1c44fb66214667c6d00acaf72ab592493653708b`; the checked-in provenance lane does not add fabricated fixture data.

Still unproven on a physical iPhone: process kill between crypto mutation and persistence, background/foreground cursor handoff under real suspension, token refresh with stable device/store, app update, unacknowledged ephemeral delivery, recovery, VoiceOver behavior, and TestFlight availability.

## Matrix/Synapse compatibility matrix

| Target | Role | Room version | Application Service contract | Result recorded by this study |
| --- | --- | --- | --- | --- |
| Synapse 1.136.0 | Current pin | Provider-reported `10`; Weave does not override it. | Exclusive opaque users/aliases, empty room namespace, `receive_ephemeral: false`. | Disposable provider probe passed: callback outage/retry preserved one transaction's semantic event set; canonical alias, `state_key`, unknown valid state, and encrypted-room policy violation were observed. [Support-safe result](synapse-compatibility-1.136.0-2026-07-18.json). Exact remediation-head full Live Stack remains pending. |
| Synapse 1.156.0 | Candidate stable release | Provider-reported `10`; no proposed pin change. | Same registration fixture. | The identical disposable provider probe passed. [Support-safe result](synapse-compatibility-1.156.0-2026-07-18.json). This is compatibility evidence, not a pin recommendation or full candidate Live Stack result; official upgrade review remains required before changing the pin. |

The compatibility fixture identifies state by `state_key` presence. Legitimate create/canonical-alias state is supported; unknown structurally valid state is recoverably quarantined at conversation scope; known state without `state_key` fails closed. A changed semantic event set under one homeserver transaction ID is an integrity/compatibility failure, while presentation-only serialization drift is a duplicate.

## Blocker register

| ID | Severity | Invariant and blast radius | Reproducibility / ownership | Recommended remedy | Required regression proof | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| LEDGER-01 | P0 | One conversation cannot block unrelated Chat or cross tenants. | Deterministic in JDBC readiness aggregation; Weave server. | Remove conversation counts from provider readiness; gate affected mapping locally. | Cross-conversation and cross-tenant create/read/send/proof. | High |
| LEDGER-02 | P0 | False-positive quarantine must be recoverable exactly once. | Deterministic schema/state-machine gap; Weave server/spec. | Versioned lifecycle plus bounded private reconciliation and conditional healing. | Completed callback, classifier N+1, one commit/heal, repeated no-op. | High |
| LEDGER-03 | P0 | Invalid event cannot poison valid siblings. | Deterministic for bad-first/same-room order; Weave server. | Preserve mapping identity while health is degraded. | All sibling order permutations and provider-event dedup. | High |
| LEDGER-04 | P0 | Same transaction ID cannot carry a changed semantic event set. | Deterministic missing comparison; Weave server. | Compare versioned semantic fingerprint and count on resume/replay. | Presentation drift pass; semantic mutation fail closed. | High |
| COMPAT-01 | P1 | Unknown valid state fails at smallest safe scope. | Reproducible with new state type; Weave adapter profile. | Versioned semantic classifier plus reconciliation, not global allowlist expansion. | Canonical alias, unknown state, missing `state_key`, classifier upgrade. | High |
| PROOF-01 | P1 | Independent provider evidence cannot be erased by unrelated mapping faults. | Reproducible in tenant/provider-wide counts; server and E2E. | Target-scope proof; independent mandatory outcome records. | Provider proof, cleanup, teardown still record when collaboration fails. | High |
| CLIENT-01 | P1 | Stable device/store/cursor across iOS lifecycle. | Simulator evidence only; Flutter/Rust plus upstream #6681. | One foreground cursor owner, crash/relaunch tests, physical signoff. | Kill, relaunch, refresh, handoff, update with stable device/store/cursor. | Medium |
| CLIENT-02 | P1 | Olm unwedge is immediate, selects working receive history, and throttles persistently. | Focused disposable Rust tests; upstream #3427/#3356/#2864/#3354. | Retain checksummed patch series temporarily and submit upstream. | Three focused Rust regressions plus collaboration twice. | High |
| CLIENT-03 | P1 | Duplicate one-time-key disagreement stays visible and fail-closed. | Upstream #6520 remains open. | No synthetic `200`; preserve diagnostic and release block. | Reproduce disagreement and assert no fabricated success. | High |
| PROVIDER-01 | P1 | Current pin and candidate must have explicit compatibility results. | Disposable 1.136.0 and 1.156.0 provider fixtures passed; full remediation-head Live Stack and any upgrade decision remain pending. | Keep the two-version fixture; require official upgrade review and exact-head Live Stack before a pin change. | Same room/state/callback/outage suite on both plus the shared concurrent callback ledger regression. | High |
| EVIDENCE-01 | P1 | Mandatory outcomes remain independently attributable. | Latest runs prove cleanup/teardown can continue, but v1 record lacked stable per-phase signatures. | Version 2 outcome records with SHA, run index, phase, category/code, signature. | Mixed pass/fail fixture keeps independent records and red aggregate. | High |
| DEVICE-01 | P0 release | Physical iPhone Chat/recovery/accessibility cannot be inferred from Simulator. | [#940](https://github.com/masssi164/weave/issues/940), [#1056](https://github.com/masssi164/weave/issues/1056); external human/device evidence. | Execute the physical lifecycle and accessibility gate after exact candidate live green. | Recorded device/update/recovery/VoiceOver signoff. | High |
| DISTRIBUTION-01 | P0 release | TestFlight availability cannot be inferred from source or CI. | [#1066](https://github.com/masssi164/weave/issues/1066); protected signing/ASC owner action. | Keep blocked until protected distribution evidence exists. | Installable TestFlight build bound to exact candidate. | High |

No recent Live Stack failure remains unclassified: superseded fixture/orchestration failures are in the timeline, canonical ledger defects are LEDGER/COMPAT/PROOF, and physical/distribution gaps remain explicit release blockers.

## Implementation packages and ownership

1. **Ledger fault isolation and reconciliation (`weave/server`)**: conversation-local degradation, systemic provider readiness, semantic replay validation, private bounded reconciliation, target-scoped evidence, migration, and focused JDBC/controller tests.
2. **Client crypto and upstream patch lane (`rust/vendor`, Flutter/Rust integration)**: checksummed three-patch series, focused crash/unwedge tests, upstream submissions, and eventual removal when an accepted SDK release proves equivalent behavior.
3. **Live evidence and provider compatibility (`.github`, `tools`, `infra/e2e`)**: per-phase v2 records, two Synapse target profiles, independent always-run proof/cleanup/teardown, and exact-candidate disposable evidence.

These packages must not absorb unrelated identity, federation, member UI, provider-routing, or release work.

## PR disposition

- **PR #1153: split/fold, do not continue as one review unit.** Correct sender cleanup, callback transaction identity, client patch behavior, and diagnostic fixes should be preserved, but a 178-file change containing a full vendored crate plus server/infra/E2E work is not one coherent stabilization package. Ledger/server work must be independently reviewable and proven. The remote draft remains frozen.
- **PR #1138: defer.** Keep stale and blocked during remediation. Refresh only after selected fixes merge to `dev` and exact-head disposable live evidence is green.
- Dependency bumps, identity/federation work, unrelated UI, and release-lane changes: defer or use separate ownership-specific PRs.

## Validation completed on the implementation worktree

- `python3 tools/spec_lint.py` in the canonical spec corpus: passed, 45 manifest entries.
- Focused JDBC ledger, Application Service controller, compatibility-profile, and adapter tests: passed.
- `./gradlew :server:test` and `./gradlew :server:check`: passed, including Flyway V010 in H2 PostgreSQL mode; the ordered SQL migration corpus and V010 columns/constraints also passed against a disposable container using the production `postgres:15` pin.
- Vendored SDK provenance reconstruction: passed; the three checksummed patches reproduce all five intentional source/packaging differences from Matrix Rust SDK 0.18.0.
- Five focused vendored crypto regressions passed in a disposable upstream-complete crate tree, including immediate first unwedge and cooldown persistence across manager recreation.
- `bash infra/weave-workspace/tests/isolated-e2e-chat-provider-proof-test.sh`: passed, including callback replay and semantic-mismatch fail-closed assertions.
- Disposable Synapse compatibility probes for pinned `1.136.0` and candidate `1.156.0`: passed against room version `10` and the same Application Service registration/profile; both injected one callback outage and observed a stable same-transaction retry without retaining raw provider evidence.
- Root `./gradlew check --no-daemon --console=plain`: passed. This covered 269 mapped acceptance scenarios, the Flutter suite, 34 shared Rust core tests, server, MCP, OpenTofu validation, infrastructure fixtures, documentation, release evidence, independent Live Stack outcome fixtures, and support-safety checks.

The root acceptance lane correctly records live-runtime scenarios as not collected. The two-version provider probe is deliberately narrower than the integrated product lane: no local command, Simulator fixture, or compatibility result is represented as an exact-candidate full Live Stack, physical-iPhone, TestFlight, dogfood deployment, or Synapse pin-upgrade decision.

## Go/no-go review

Current decision: **no-go for promotion or dogfood deployment**.

Resume `feature -> dev -> dogfood` only when all of the following are true:

1. Ledger isolation/reconciliation migrations and focused server regressions are green.
2. The exact implementation candidate passes ordinary root CI and the complete disposable Live Stack lane.
3. Single-user AppShell/E2EE lifecycle and both collaboration directions pass twice with distinct identities, devices, and sessions.
4. Provider persistence/exactly-once, outage recovery, callback replay, cleanup, and teardown all have independent green v2 outcome records.
5. Pinned Synapse has green exact-candidate evidence; candidate Synapse has a recorded disposable compatibility result before any pin recommendation.
6. Duplicate one-time-key disagreement remains fail-closed, and no plaintext/routing/auth workaround or raw support-evidence leakage is introduced.
7. PR #1138 is refreshed from the resulting `dev` head, not from the frozen research baseline.

Physical-iPhone lifecycle, recovery, accessibility, and TestFlight remain separate mandatory release gates. Until real evidence exists, they remain explicitly unproven even if automated E2E is green.
