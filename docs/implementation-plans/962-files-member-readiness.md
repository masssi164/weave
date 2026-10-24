# Implementation brief: replace provider connection copy with member file context

Status: planning only; implementation and runtime evidence are pending.
Task: [#962](https://github.com/masssi164/weave/issues/962).
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

Implements the current-source remainder of existing #962. #963 retains broader Settings separation; #1470 governs the release.

## Observed problem

Existing #962 exactly covers the owner's report: “Weave-Dateien / Verbunden als Weave files” suggests a separate provider account although normal Files uses the generated Weave User API. Current BackendFilesRepository still uses the synthetic accountLabel 'Weave files', and FilesScreen formats it as a connected identity. Failure paths can also show filesProductBoundaryBody, an explanation of User API/provider internals rather than a useful member action.

The old issue's endpoint examples describe historical code; current work targets /api/files/items and the current generated client. Do not recreate deprecated endpoints or a direct-provider connection.

## Implementation sequence

1. Replace synthetic connected-as/account wording with the actual selected personal/Space file context and relevant Weave session/capability state.

2. Remove any independent provider connect/disconnect affordance for backend-owned Files where no such member session exists. Keep normal Weave sign-in and actual retry/recovery actions.

3. Remove developer-facing API/provider-boundary prose from normal member states, including unavailable/error notices; retain support diagnostics behind their existing support surface.

4. Preserve the accepted private owner-only and shared-Space semantics, existing data visibility and selected scope. Personal-area naming/default changes are not silently bundled into this repair.

## Bounded starting paths

- `client/lib/features/files/presentation/files_screen.dart`
- `client/lib/features/files/data/repositories/backend_files_repository.dart`
- `client/lib/features/files/presentation/providers/files_repository_provider.dart`
- `client/lib/l10n/app_de.arb`
- `client/lib/l10n/app_en.arb`
- `client/test/features/files/files_screen_test.dart`
- `client/test/features/files/presentation/providers/files_backend_facade_provider_test.dart`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #962](https://github.com/masssi164/weave/issues/962). Do not close it from this planning change.

Run `flutter gen-l10n` and the targeted Files screen/backend-facade tests, then affected client format/analyzer checks and existing provider-boundary checks. Native smoke remains pending until implementation is on the delivered candidate.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Prefer integrating this small UI repair before overlapping #1705/#1706 edits. #1707 owns the separate Home status slice.

Small independent repair, preferably integrate before the two Files UI slices to reduce conflicts in files_screen.dart/localizations. No dependency on missing move/delete APIs.

No removal or automatic sharing of private files, changed default scope, new provider login, broad Settings rewrite, or duplicate issue for #962.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.
