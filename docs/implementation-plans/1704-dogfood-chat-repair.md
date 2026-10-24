# Implementation brief: diagnose and restore ordinary Dogfood Chat on preserved device state

Status: bounded client implementation is present in the draft PR; exact-candidate CI and physical acceptance remain pending.
Task: [#1704](https://github.com/masssi164/weave/issues/1704).
This is a bounded implementation handoff, not a second product specification or a release-readiness record.

## Validated starting point

- Implementation: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`; tree: `4616bed6a53a9363aec51371732eea8455aac604`.
- Accepted corpus: `7129075b7bf3f9f3763ec71a907395b007604c6f`, as pinned by `specs/weave-specs.lock.json`.
- Target lane: `dev`. Verify current source/spec and review only relevant deltas before coding.
- The linked issue owns this task's acceptance checklist; the pinned corpus owns product meaning. Update the issue and this handoff together when scope changes. Do not infer new authority from this plan.

## Governing requirements

- [domains/chat/spec.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/domains/chat/spec.md)
- [steering/release-2026-10-product-consolidation.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/release-2026-10-product-consolidation.md)
- [acceptance/features/human-testing-readiness.feature](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/acceptance/features/human-testing-readiness.feature)

#1666 owns first-use/preparation; #1592 owns two-client collaboration; #1470/#1481 own release acceptance.

## Observed problem

The owner still cannot open Chat on the physical iPhone. The 2026-10-24 screenshots show an unavailable Chat view while Home advertises Chat as available. File/folder creation and Calendar event creation now work according to the owner; do not repeat the obsolete claim that all three features fail.

The original physical Chat failure remains unproven. PR #1677 already added typed operation/stage/category/status diagnostics. Current Dev selects specific Chat failure copy, while the screenshots show the older generic connection retry text. This is consistent with the retained build 19, but does not identify its commit. PR #1702 repairs preparation, not the original Chat failure.

## Implementation sequence

1. Read back the installed candidate/version/build and deployed candidate/spec before comparing the physical result with Dev. Preserve the retained owner installation, session, device identity and encrypted local store.

2. Use existing ChatOperationDiagnostics to find the first failing operation: admission, local binding/store, native activation, initial sync or room listing. Correlate support-safe server evidence where possible; do not infer a transport failure from generic UI copy.

3. Repair the smallest owning boundary and add a behavioral regression reproducing the diagnosed defect. Reuse the existing ordinary direct functional smoke and native journeys.

4. Keep normal OIDC/PKCE and the Weave Matrix Client-Server facade. Files/Calendar use the generated User API; this task does not replace native Matrix with proprietary Chat REST.

5. Coordinate Home's visible status with the Home follow-up. Keep authorization/admission independent of the runtime-health result it enables.

## Bounded starting paths

- `client/lib/features/chat/presentation/providers/chat_provider.dart`
- `client/lib/features/chat/data/repositories/native_matrix_chat_repository.dart`
- `client/lib/features/chat/presentation/chat_admission_gate.dart`
- `client/lib/integrations/rust_matrix_core/data/services/weave_member_matrix_session_coordinator.dart`
- `client/lib/integrations/rust_matrix_core/data/services/chat_operation_diagnostics.dart`
- `tools/dogfood_functional_smoke.py`
- `client/tool/run_native_qualification.py`
- `client/test/features/chat/data/repositories/native_matrix_chat_repository_test.dart`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #1704](https://github.com/masssi164/weave/issues/1704). Do not close it from this planning change.

Run the targeted Chat/coordinator regression and affected native checks, then applicable `specCorpusConformance`, `acceptanceContract`, `clientCi` and `releaseEvidenceCheck`. Use the existing final candidate qualification, not substitute mocks or local preparation for release proof.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Coordinate first-failure proof with #1703 and status projection with #1707. #1666 remains the separate preparation dependency.

Static diagnosis and regression work can proceed alongside runtime-parity work. Disposable member A recovery remains a separate #1666 prerequisite for blocked two-member preparation, not an established cause of the owner's Chat failure. This task grants no additional recovery authority or live-device mutation permission.

No owner reinstall/store deletion, provider bypass, authorization relaxation, general Chat rewrite, new diagnostic framework, or implicit credential-recovery approval.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.

## Implementation update — 2026-10-24

The owner's current priority is a usable ordinary client on the existing Mac mini setup. This draft now contains actual client repairs.

- Share refresh work for the same saved session across repository instances; serialize the current store read and refresh admission while leaving network work outside the storage queue.
- Reuse an already renewed session for restore; preserve explicit refresh, logout and replacement-account safeguards.
- Propagate missing current-session admission as a retryable error and preserve all twelve backend capability states, including denied admin access.
- Add deterministic concurrency, delayed-read, account isolation and current-session/Space regressions. The existing ordinary default-Space setup remains the preparation path.

### Verification of the implementation

- 74 auth/Chat-coordinator tests passed (17 auth + 57 existing coordinator tests). The exact final auth regressions produce 6 expected failures against unchanged Dev and all pass with the fix.
- 35 workspace/session/setup tests passed. Both added admission regressions fail against the exact original Dev provider.
- Focused Dart analysis, formatting and diff checks passed. Full Flutter analysis passed on the combined auth/Space/Home/Files-copy source.
- Acceptance mapping guard passed; E2E structure guard passed, with the pinned-manifest cross-check unavailable locally.

Independent review examined the final queued refresh admission, delayed-read tests, logout/new-account guards and complete capability merge; no blocking source finding remains.

The original physical Chat failure is not yet attributed to this defect. Keep #1704 open until the existing Mac mini candidate and preserved phone state demonstrate ordinary authenticated Chat. Exact-branch CI and Dogfood/physical evidence are pending.
