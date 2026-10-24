# Implementation brief: integrate folder browsing with native Back navigation

Status: planning only; implementation and runtime evidence are pending.
Task: [#1706](https://github.com/masssi164/weave/issues/1706).
This is a bounded implementation handoff, not a second product specification or a release-readiness record.

## Validated starting point

- Implementation: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`; tree: `4616bed6a53a9363aec51371732eea8455aac604`.
- Accepted corpus: `7129075b7bf3f9f3763ec71a907395b007604c6f`, as pinned by `specs/weave-specs.lock.json`.
- Target lane: `dev`. Verify current source/spec and review only relevant deltas before coding.
- The linked issue owns this task's acceptance checklist; the pinned corpus owns product meaning. Update the issue and this handoff together when scope changes. Do not infer new authority from this plan.

## Governing requirements

- [domains/files/spec.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/domains/files/spec.md)
- [steering/product-constitution.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/product-constitution.md)
- [steering/release-2026-10-product-consolidation.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/release-2026-10-product-consolidation.md)

#1470; bounded navigation follow-up to #947, #1666 and #956. Separate from the move/delete API work and #962 copy repair.

## Observed problem

Entering a folder only changes Files provider state through openDirectory. goUp manipulates the path, while the route exposes one FilesScreen(initialPath). The user has to find the bespoke “Open parent folder” action. Folder browsing is not represented as native navigation history.

The owner explicitly requests normal Back behavior. This can be delivered independently of the larger Files mutation API slice.

## Implementation sequence

1. Represent folder browsing in the existing Files navigation branch with selected root and canonical folder identity. Reuse the established router; do not invent a second navigation system or treat display paths as resource authority.

2. Support the app-bar Back action, relevant platform Back/gesture and accessible activation. Preserve expected shell behavior at the Files root.

3. Restore the parent scroll position and focus to the originating folder row when possible. Handle removed/moved/revoked locations by recovering to an authorized location.

4. Keep deep links, tab switching, account/root changes and current permissions coherent. Remove the custom parent-opening action as the required way back.

## Bounded starting paths

- `client/lib/core/router/app_router.dart`
- `client/lib/features/files/presentation/files_screen.dart`
- `client/lib/features/files/presentation/providers/files_provider.dart`
- `client/test/features/files/files_screen_test.dart`
- `client/test/features/files/presentation/providers/files_provider_test.dart`
- `client/test/features/shell/app_shell_test.dart`
- `client/lib/l10n/app_de.arb`
- `client/lib/l10n/app_en.arb`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #1706](https://github.com/masssi164/weave/issues/1706). Do not close it from this planning change.

Run targeted Files/router/provider widget and semantics tests, localization generation, format/analyzer, then affected `clientCi`/acceptance checks. Exercise actual native Back and folder focus in the existing simulator journey; automated semantics do not claim physical VoiceOver acceptance.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Independent of #1705. Sequence overlapping files_screen.dart/localization edits after #962 or integrate isolated worktrees deliberately.

Independent functional slice: no dependency on move/delete implementation. Coordinate files_screen.dart/localization ownership with #962 and the Files actions issue; rebase on the integrated predecessor before overlapping UI edits.

No file mutation API, personal/shared visibility change, provider data migration, or bespoke Back control replacing platform behavior.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.
