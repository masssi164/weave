# Implementation brief: show truthful localized member state and actionable recovery

Status: planning only; implementation and runtime evidence are pending.
Task: [#1707](https://github.com/masssi164/weave/issues/1707).
This is a bounded implementation handoff, not a second product specification or a release-readiness record.

## Validated starting point

- Implementation: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`; tree: `4616bed6a53a9363aec51371732eea8455aac604`.
- Accepted corpus: `7129075b7bf3f9f3763ec71a907395b007604c6f`, as pinned by `specs/weave-specs.lock.json`.
- Target lane: `dev`. Verify current source/spec and review only relevant deltas before coding.
- The linked issue owns this task's acceptance checklist; the pinned corpus owns product meaning. Update the issue and this handoff together when scope changes. Do not infer new authority from this plan.

## Governing requirements

- [steering/product-constitution.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/product-constitution.md)
- [steering/release-2026-10-product-consolidation.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/release-2026-10-product-consolidation.md)

#1470; bounded current-source follow-up to #946, #955, #956 and #1666. Files readiness copy remains #962.

## Observed problem

Home says Chat is available while the Chat view fails. The capability snapshot combines backend entitlement, local authentication and Space admission, but not the current Chat runtime outcome. WorkspaceHomeService supplies English prose that ShellWorkspaceStatus renders inside the German app.

The service aggregates disabled Boards/other sections into global degradation and generic “Review availability” advice. The client collapses the summary into one large semantics label; displayed action text is not interactive. The pinned constitution treats Boards/Tasks as Expansion, so deliberate disablement must not itself look like a broken Core product.

## Implementation sequence

1. Separate enabled/authorized capability, not-yet-checked/stale runtime state, actual last feature result, denied access and intentional disablement in the member view. Keep Chat admission authorization independent of runtime health to avoid circular gating.

2. Use stable typed state/action identifiers and localized product copy. Map existing typed IDs where sufficient; any needed API model change must be explicit, code-first and regenerated. Do not silently redefine diagnostic readiness semantics or parse English prose.

3. Show concise member summaries and only usable recovery actions. Optional disabled capabilities should not cause irrelevant contact-admin/retry prompts for Core.

4. Give headings, states and actual buttons/links separate useful semantics. Preserve genuine service failures and make navigation remain usable.

5. Recheck large-text tab labels on the delivered candidate: current Dev already has adaptive navigation, so the screenshot alone does not prove that fix is absent.

## Bounded starting paths

- `server/src/main/java/com/massimotter/weave/backend/service/WorkspaceHomeService.java`
- `server/src/test/java/com/massimotter/weave/backend/service/WorkspaceHomeServiceTest.java`
- `client/lib/features/shell/presentation/shell_workspace_status.dart`
- `client/lib/features/app/domain/entities/workspace_home_snapshot.dart`
- `client/lib/features/app/presentation/providers/workspace_connection_provider.dart`
- `client/test/features/app/presentation/providers/workspace_connection_provider_test.dart`
- `client/test/features/shell/app_shell_test.dart`
- `client/lib/l10n/app_de.arb`
- `client/lib/l10n/app_en.arb`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #1707](https://github.com/masssi164/weave/issues/1707). Do not close it from this planning change.

Run targeted Home service, state-composition, widget and semantics tests; localization generation and affected client/server gates. If the API projection changes, run generated API/affected consumer freshness checks. Map changed behavior to existing acceptance before review readiness.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Coordinate typed runtime state with #1704 without coupling it to authorization. Keep Files terminology repair in #962.

Can proceed alongside Chat diagnosis; agree on the existing typed runtime-state boundary without adding health requirements to authorization. Coordinate shared localization edits with Files slices.

No generic Home redesign, new monitoring subsystem, false all-green readiness, raw provider/API diagnostic prose in member paths, or completion claims for all historical Home issues.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.
