# Implementation brief: implement authorized move/delete through the User API and accessible entry actions

Status: planning only; implementation and runtime evidence are pending.
Task: [#1705](https://github.com/masssi164/weave/issues/1705).
This is a bounded implementation handoff, not a second product specification or a release-readiness record.

## Validated starting point

- Implementation: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`; tree: `4616bed6a53a9363aec51371732eea8455aac604`.
- Accepted corpus: `7129075b7bf3f9f3763ec71a907395b007604c6f`, as pinned by `specs/weave-specs.lock.json`.
- Target lane: `dev`. Verify current source/spec and review only relevant deltas before coding.
- The linked issue owns this task's acceptance checklist; the pinned corpus owns product meaning. Update the issue and this handoff together when scope changes. Do not infer new authority from this plan.

## Governing requirements

- [domains/files/spec.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/domains/files/spec.md)
- [steering/release-2026-10-product-consolidation.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/release-2026-10-product-consolidation.md)
- [steering/product-constitution.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/product-constitution.md)

#1470 release scope; bounded current-API/action follow-up to #947 and #1666. Readiness terminology remains in #962; native folder history has its own follow-up. Provider portability remains #1498/#1477 and is not required by this slice.

## Observed problem

Members can create files/folders but cannot move or delete entries. At the inspected source, FilesUserItemsController has no move/delete operations; BackendFilesRepository.moveEntry/deleteEntry unconditionally throw unsupportedPlatform; the row UI exposes preview/replace/export only. Adding buttons alone would create nonfunctional controls.

The pinned Files contract already requires these operations where the active provider supports their real semantics. Current folder creation is limited to atomic root creation. Destination support must be proven rather than inferred from a folder being visible.

## Implementation sequence

1. Implement the smallest complete move/delete vertical slice through existing canonical Files services/ports and the code-first User API. Regenerate affected clients with the existing generation tasks; do not hand-edit generated transports.

2. Reuse stable FileId, current source/destination authorization, strong preconditions, durable operation intent and ambiguous-result reconciliation. Inspect the active provider's actual folder semantics before advertising destinations or recursive operations.

3. Provide a keyboard- and screen-reader-accessible per-entry actions button for files and folders. Offer supported operations from current server permissions/capabilities, with destination selection and explicit delete confirmation.

4. Keep personal/Space scope explicit. A move must not silently widen visibility or turn a private file into a shared one; reject unsupported scope changes before effects.

5. Refresh authoritative listings after confirmed completion and recover focus/status without duplicate announcements or ghost rows.

## Bounded starting paths

- `server/src/main/java/com/massimotter/weave/backend/controller/FilesUserItemsController.java`
- `server/src/main/java/com/massimotter/weave/backend/service/files/FilesUserApiService.java`
- `server/src/main/java/com/massimotter/weave/backend/files/application/FilesMutationIntentService.java`
- `server/src/main/java/com/massimotter/weave/backend/files/port/FilesProviderPort.java`
- `client/lib/features/files/data/repositories/backend_files_repository.dart`
- `client/lib/features/files/presentation/providers/files_provider.dart`
- `client/lib/features/files/presentation/files_screen.dart`
- `server/src/test/java/com/massimotter/weave/backend/service/files/FilesUserApiServiceTest.java`
- `client/test/features/files/files_screen_test.dart`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #1705](https://github.com/masssi164/weave/issues/1705). Do not close it from this planning change.

Run targeted Files service/intent/provider and HTTP authorization tests; `generatedApiContractCi`, affected `generatedUserApiConsumerCi`, `specCorpusConformance`, `acceptanceContract` and relevant client checks. Then prove the real supported operations in the existing product/native path.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Integrate overlapping Files UI edits deliberately with #962 and #1706. Backend work is independent; portability stays #1498.

Server work can proceed independently. Sequence edits to files_screen.dart with #962 and the native-folder-navigation follow-up, or use isolated worktrees and integrate deliberately. Do not make Back navigation wait for move/delete backend completion.

No copy/bulk operations, provider selection/migration, client DAV, invented trash semantics or private-files removal. If atomic folder/provider semantics need a material new contract, record the precise prerequisite instead of weakening integrity.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.
