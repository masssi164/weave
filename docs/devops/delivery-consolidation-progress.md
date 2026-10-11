# Delivery consolidation progress

Implementation/evidence record for weave#1564–#1567 and weave-specs#153. This is not normative specification authority.

## Authority and preserved work

- Canonical delivery change: weave-specs PR #154; implementation continues draft weave PR #1563.
- Remote Dev at investigation: 5597dfb6399d8c35312fba40ed299506c5c27a0c; corpus pin c726993168651f1109259f9a80cc23117d24a37f.
- Local primary checkout is unrelated files/v7-mutation-journal; sibling corpus checkouts contain unrelated Files changes. Isolated worktrees preserve them.
- ADR 0022 superseded ADR 0019 and Candidate Cut/Fresh Start in 46f3f886a08a5537134f8bc667c9405c12db70dc. Historical enterprise gates cannot redefine current delivery.

## Decisions and evidence

- Task classification: ci-e2e. Corpus owns lifecycle; Weave owns executable evidence and implementation.
- Spec PR reconciles steering, ADR, both acceptance features and current-release post-Main evidence semantics. Active pipeline changes wait for acceptance/merge and exact pin.
- Existing testApp already executes real native macOS journeys. Reuse its disposable users and Compose lifecycle for a separate iOS simulator runner, preserving physical-device validation.
- Draft impact selection omits generated outputs, generator verification tools and architecture inputs. Complete-input selection and independent consumer gates are required.
- Full Compose run 38116092539 failed before product execution: 18 GiB free versus 20 GiB required. Capacity guard remains mandatory; this is infrastructure failure, not product regression.
- One Core CI baseline spans approximately 30 minutes; contract freshness about 1m59s and consumers about 10m29s. No measured improvement exists yet.

## Validation and blockers

- Investigation: simulator xcrun tests 7 passed, iOS distribution contract passed, runner-capacity tests 4 passed.
- Corpus lint/semantic examples, vector check, Ready rejection, links, Chen model, OpenAPI and actionlint passed; exact-head remote CI pending.
- Project #2 token lacks read:project. User selected enabling access, but credential remains unchanged. Project fields and automation must be inspected before edits.
- No deployment, reset, physical-device install, human acceptance or production publication authorized/performed by this work.
- Keep #1470 open; #1481 and actual release acceptance remain separate closure requirements.

## Next safe actions

1. Verify and merge accepted spec PR #154 through ordinary checks; pin exact merged revision.
2. Correct #1563 deterministic CI/consumer impact and evidence checks; add native simulator qualification.
3. Update governance and Project projection after access; migrate protection only after replacements operate.
4. Run exact-candidate integrated validation; request authorization before live deployment/physical preparation.

## Accepted contract and implemented reconciliation

Spec PR #154 merged through ordinary checks; weave-specs#153 is closed. Accepted corpus commit is `b02bd60620b1a0a7585756798ba5330155fc0ee2`; this branch pins it exactly. Canonical-corpus and bundle-signability passed on the PR and post-merge corpus conformance/signing passed.

| Conflict | Authority/history | Affected consumer | Resolution |
|---|---|---|---|
| Normal PR Full Compose requirement vs fast Dev | Current delivery steering/ADR 0022; older branch instructions retained E2E premerge | live-stack workflow, branching docs, protected checks | Dev deterministic gates; protected Dogfood push qualifies. Protection migration pending operational replacement. |
| Dogfood readiness conflated with human acceptance | Delivery/human Gherkin; previous single-gate wording | HIL preparation, Main verifier, active docs | Independent native/platform evidence plus deployment precedes physical preparation; explicit owner human result follows preparation. |
| Current Dev tree equality invalidates unchanged Dogfood | Existing Main gate implementation | Main promotion | Compare candidate tree/spec with qualified Dogfood; Dev may advance independently. |
| Combined consumer regeneration vs scoped freshness | Draft #1563 incomplete impact roots | generated User/Admin clients, Gradle CI | Export always checked without rewriting source; independent complete-input consumer gates; unknown impact runs all. |
| Candidate Cut/Fresh Start/OpenTofu Core authority | ADR0019 superseded by ADR0022 in `46f3f886a08a5537134f8bc667c9405c12db70dc` | historical release fixtures and active agent/docs | Preserve fixtures/provenance; current direct Compose delivery governs active instructions. |
| Project issue status implies candidate success | Historical sprint board conventions | Project #2 | No inferred readiness; actual configuration inspection blocked by token scope. No live fields changed. |

Implementation adds mandatory macOS and Apple Silicon iOS simulator execution to the existing disposable product runner. Native evidence binds commit/tree/spec and successful cleanup. Physical preparation binds the qualification run and attempt, and must begin after qualification/deployment. Main consumes bounded exact-attempt artifacts and explicit latest owner acceptance; no repeated integration execution is added.

## Executed validation after implementation

- Pinned corpus conformance: 155 entries passed (27 seconds).
- Actual code-first API freshness: passed (48 seconds); Admin consumer gate passed (40 seconds, 24 tasks, 14 up to date). These local warm-cache results are not a comparable hosted CI improvement measurement.
- Main evidence negatives: 15 passed, including stale source, missing native result, failed cleanup and qualification retry ordering.
- Native regressions: 6 passed. Locked Rust 1.95 simulator cross-build, standalone XCTest build, Flutter simulator app build, actual install/launch, FRB arm64 IOSSIMULATOR metadata and run-owned simulator deletion all passed. Build/launch proof explicitly is not functional qualification.
- Actionlint, candidate pipeline, iOS distribution, runner hygiene, action runtime, core docs and strict MkDocs passed.
- Independent review caught backend-job conditions disagreeing with aggregate skip expectations; repaired with workflow linkage regression. Review also caught preparation reuse across qualification attempts; repaired with attempt binding and ordering regression.
- Live Compose/macOS/iOS authenticated journeys have not executed: capacity remains below mandatory 20 GiB after removing only task-owned temporary outputs. Shared caches and persistent Dogfood resources were preserved.

## Remaining operational boundaries

Both Project authorization device-code attempts expired without completion. Token still lacks Project scopes. Actual fields/options/automations remain uninspected. #1565 cannot be considered complete.

Branch protection has not changed. Existing required Full Compose checks on Dev/Dogfood must migrate deliberately only after replacement deterministic CI operates successfully. The draft implementation is not deployed or released. No physical-device install or human acceptance has occurred. #1470 remains open until real exact-candidate automated, physical/human and release acceptance requirements are fulfilled.

Follow-up verification: actual acceptanceContract + User consumer + independent client boundary passed in 5m29s, including User TypeScript (130 files), Dart freshness, JVM reproducibility, temporal contracts in UTC/Berlin/Apia (21 tests) and five architecture boundaries. A discovered SDK-only acceptance guard was unnecessarily invoking Flutter native hooks; direct Dart execution preserves all 252 mappings, and the optimized acceptance + 14 CI regressions passed in four seconds. Full client deterministic checks remain in progress.

First implementation commit: `8c211ea2e1882e6c98f7706319346adc6a7a2bb7`, pushed to existing draft #1563. Hosted run 38126926410 passed API freshness, impact, core architecture, Admin consumer, Admin deterministic, core documentation, release label and documentation; remaining jobs still running. Infrastructure static failed an obsolete assertion requiring the retired combined consumer job. The assertion now checks separate consumers and executable fail-closed aggregation; full local infraStatic passed (14 seconds). Follow-up remote validation is required after this repair.

Native diagnostic refinement: seven regression tests now pass. Infrastructure tool/build/boot failures and started browser/product assertion failures emit controlled categories and allowlisted stage markers; raw credentials/transcripts remain private. Revised XCTest safe-stage instrumentation compiled successfully. No live functional success is inferred from this refinement.

Native Provider Gate consolidation remains staged: Core CI now runs the inherited source invariants in its required Core documentation job on every ordinary PR, including docs-only changes. Core server tests cover the historical focused native-provider subset, and matrixRustCoreTest covers both historical Cargo suites plus vendor provenance. Generated build artifacts are not source authority; diagnostics name only rule, path, line and prohibited identifier. The historical workflow remains active until the new guard and replacement Server/Rust checks pass remotely for the same head; no trigger or protected check has been removed.

Final local client rerun passed in 1m16s after narrowly updating the physical/simulator safety assertion: 632 Flutter tests passed, one skipped; 469 formatted files unchanged; analysis clean; screenshot/generated drift clean; ten offline mapping and four endpoint tests passed. Four focused safety tests passed. Task-owned hook/build outputs were removed after validation; capacity preflight still fails at 18 GiB versus required 20 GiB. No owned simulator remains.

Baseline timing was revalidated against run 38116092598 **attempt 1**: deterministic first-job-to-aggregate 29m59s, full attempt 30m02s, export job 1m59s, combined consumer job 10m29s. That attempt's real verification jobs passed; release-label failure was repaired by attempt 2. Latest run metadata describes the label-only retry and cannot be used as a full baseline. Core run 38127326679 at `d7aaadb8c057251ec9385e411c85507b6c53c4ce` has passed replacement server, PostgreSQL, Rust, consumers, infra, docs and acceptance; Flutter/aggregate completion remains pending at this note.

Pure codegen investigation did not justify a launcher change. The locked build_runner 2.13.1 direct Dart VM entry successfully generated a minimal fixture, but the actual client still spawned `dart run .dart_tool/build/entrypoint/build.dart.dill`, discovered Flutter Rust Bridge hooks and began a release Rust build. The probe was stopped after 32.65 seconds; generated Dart file hashes were unchanged, and only probe-owned hook outputs were removed. The fixture omitted the full resolved package graph and therefore was insufficient evidence for native-hook avoidance. Existing build_runner invocation and real Flutter native tests are retained. Supported AOT bootstrapping still uses `dart compile aot-snapshot`; no supported native-hook suppression option was identified. Further latency work requires an upstream-supported tool-only bootstrap or measured reuse of normal native build outputs, rather than private bootstrap patches or dependency workspace changes.

A second cold-client codegen probe scoped Cargo’s standard `CARGO_PROFILE_RELEASE_OPT_LEVEL=0` only to `dart run build_runner build --delete-conflicting-outputs`. It did not reach the native hook: the actual Cargo process lacked that variable and live rustc commands retained `opt-level=3`. Dart hooks supplied their sanitized environment; the hook input also had no declared profile override. The probe was terminated after 61.42 seconds (exit 255 from interrupted hook), with all generated Dart hashes unchanged. Newly created probe-owned hook outputs were removed after its processes stopped. No Gradle prefix was added; an undeclared profile override would additionally require cache provenance protection before any normal Flutter or native qualification could reuse its output.

Core CI run 38127326679 completed successfully on `d7aaadb8c057251ec9385e411c85507b6c53c4ce`; stable required `Gradle CI` emitted and passed. Full run 09:08:50→09:30:29 = 21m39s versus baseline attempt 1's 30m02s (about 28% shorter). This is two observed runs, not a controlled benchmark; current coverage additionally includes full Flutter, infra, docs, pinned-spec and Rust checks. Native Provider Gate 38127326685 also passed. Mandatory protected Dogfood integration, physical preparation and human acceptance remain unexecuted.

Project access remains blocked after renewed device authorization expired. Browser fallback displayed unauthenticated GitHub 404; no authenticated browser was available. Available connector tools do not expose Project fields/GraphQL. No actual Project options or automations were assumed.


Native reproducibility follow-up verifies the existing unique Flutter CI pin against the installed SDK release tag/framework HEAD and engine source, derives Rust channel/target from the crate toolchain, and enforces the supported Xcode 26.5/17F42/Simulator SDK 26.5 pair. Actual metadata inspection passed (Flutter 3.41.6 stable, framework db50e20168db8fee486b9abf32fc912de3bc5b6a, engine 425cfb54d01a9472b3e81d9e76fd63a4a44cfbcb, Dart 3.11.4, Rust 1.95.0). Both child runners inherit pinned rustup proxies. Toolchain identities extend the existing native evidence object; Main rejects missing/malformed/inconsistent metadata without introducing another pin authority. Nine native and sixteen Main regression tests passed; all 37 Main/impact/provider tests passed together. Native regressions are now wired into Core documentation CI. Independent review passed. Large native builds were not repeated for this metadata change.

Focused actionlint passed for the changed CI, Dogfood, physical-preparation and Main-promotion workflows. A repository-wide actionlint scan additionally reports existing SC2251 shellcheck diagnostics in the untouched supplemental `native-persistence-closure.yml` workflow; that workflow was not rewritten as part of delivery consolidation. Workflow immutable-action/runtime validation passed.

Native Provider Gate is now retired after same-head Core run 38128850305 verified the transferred source rules, Server protocol foundation and Rust module replacement at `1a9da0b38a8db8be67f34c9b82da613b653ad13e`. No branch required that supplemental check; protections remain unchanged. The persistence lane audit found its runtime tests fully covered by Core Server/PostgreSQL, but intermediate shell negations and an ignored positive assertion were fail open. The intended nonempty V1–V6 migration, SQL schema, Flyway, production DDL, snapshot removal and positive validation rules now execute in the existing Core source guard, with ten guard tests. Forty Main/impact/source-guard tests and nine native tests pass; candidatePipelineCheck+infraStatic passed in eleven seconds. The persistence workflow remains active until the strengthened guard and replacement Server/PostgreSQL checks pass remotely on the same commit.

Current #1470 issue-state audit: #1479 and #1480 are closed; #1481 remains open. Those closed story states are not current exact-candidate release evidence. The remaining release path requires actual protected Dogfood qualification/deployment, same-candidate physical preparation and explicit human acceptance, then normal Main promotion and #1481's approved licences/provenance/accurate READMEs plus final remote mainline checks. Existing Weaver #43/#45 must be revalidated rather than recreated; no licensing or upstream ancestry mutation was performed in this delivery task. #1498 migration remains independent.

Read-only current-state negative verification: the new qualification-only verifier rejected protected Dogfood `e4350263d72b5efee71309c2016cbac870a68803` with “No successful latest exact Dogfood qualification/deployment run” (exit 1). The API identity lookup completed; this was an evidence denial, not an authorization failure. No preparation, promotion or deployment was triggered.

Persistence duplicate retired only after Core run 38129656146 on exact `c0c6a95dd13bdfb222e16a49412021e5e736ab9c` passed strengthened source guard/Core documentation, PostgreSQL (09:55:28 UTC) and Server (09:58:28 UTC). Its supplemental identity was absent from all protected-check lists. The guard and runtime replacement remain in required Core CI. Full repository actionlint now passes, including ShellCheck; action runtime policy, actual source guard, ten guard tests, Core docs, candidatePipelineCheck and infraStatic passed (latter twelve seconds). Existing migration/provider history and evidence links are preserved. No protected requirement was removed. Protected Dogfood still pins c726993168651f1109259f9a80cc23117d24a37f; draft implementation pins accepted b02bd60620b1a0a7585756798ba5330155fc0ee2.
