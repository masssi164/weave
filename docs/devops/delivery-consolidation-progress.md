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

Project authorization device code expired; a new authorization request is pending. Token still lacks Project scopes. Actual fields/options/automations remain uninspected. #1565 cannot be considered complete.

Branch protection has not changed. Existing required Full Compose checks on Dev/Dogfood must migrate deliberately only after replacement deterministic CI operates successfully. The draft implementation is not deployed or released. No physical-device install or human acceptance has occurred. #1470 remains open until real exact-candidate automated, physical/human and release acceptance requirements are fulfilled.
