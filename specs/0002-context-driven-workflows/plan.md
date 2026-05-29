# Implementation plan: Context-driven workflow primitives

**Spec**: `specs/0002-context-driven-workflows/spec.md`  
**Branch**: `issue-218-workflow-contract`  
**Date**: 2026-05-29

## Summary

Add a preview-only workflow contract that keeps workflows provider-neutral, linear-first, Context Graph based, and agent-governed. The first implementation stays in client domain/presentation preview code and documents the required backend/server ownership before any persisted workflow execution.

## Constitution check

- Repo truth recovered from `main`, docs, GitHub issue/PR state, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: yes
- Accessibility/supportability/auditability/deployability addressed: yes
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver/OpenClaw runtime remains governed and disabled-by-default unless explicitly in scope: yes

## Affected areas

- `client/`: workflow preview domain entities, preview facade, provider/widget tests.
- `server/`: not changed in MVP; must own future persisted templates/runs and Context Graph authorization.
- `admin-console/`: not changed in MVP; future policy preview may expose workflow execution controls.
- `infra/`: not changed.
- `e2e/`: not changed for preview-only MVP.
- `docs/`: repo-local spec under `specs/0002-context-driven-workflows/`.
- `release/`: not changed.
- `tools/`: not changed.

## Contracts and tests first

1. Product acceptance/Gherkin: deferred until persisted backend/user journey slice.
2. Mapping/evidence marker: deferred until product journey is executable.
3. API/event/schema contracts: future backend workflow/context endpoint required before execution.
4. Unit/widget/backend/admin tests: `client/test/features/workflows/workflow_preview_provider_test.dart`, `client/test/features/workflows/workflow_preview_panel_test.dart`.
5. CI/evidence artifacts: local Flutter workflow tests and `./gradlew specContract`.

## Agent work breakdown

- Product/spec steward: validate `specs/0002-context-driven-workflows/spec.md` issue #218 acceptance coverage.
- Client/accessibility: keep `WorkflowPreviewPanel` linear, semantic, non-drag, and text-first.
- Server/domain facade: later define persisted workflow endpoint and Context Graph authorization.
- Admin/policy: later define workflow execution and agent dry-run policy keys.
- Provider/infra: no MVP work.
- QA/evidence: add Gherkin/mapping when backend journey exists.
- Docs/release: link PR to issue #218 and use `release-notes-feature`.
- Security/privacy review: confirm no raw provider payloads or silent agent writes.

## Rollout and migration

- Backward compatibility: preview-only domain classes; no stored data.
- Data migration: none.
- Feature flag/capability gate: future workflow capability before persisted rollout.
- Rollback plan: remove preview surface/classes; no provider migration.
- Release evidence: local workflow tests and spec contract gate.

## Risks and mitigations

- Risk: client preview becomes product contract without server-owned canonical ids.
  - Mitigation: spec marks backend ownership and fail-closed Context Graph resolution as required before execution.
  - Evidence gate: architecture review plus future backend contract tests.
- Risk: agent workflows imply autonomous writes.
  - Mitigation: MVP flags agent steps as dry-run/proposal-only with approval and audit requirements.
  - Evidence gate: workflow provider tests.
- Risk: workflow UI regresses to visual-only builder assumptions.
  - Mitigation: linear preview first; widget test covers non-drag accessible panel.
  - Evidence gate: workflow panel widget test.

## Final gates

- `./gradlew specContract`
- `./gradlew acceptanceContract` when Gherkin/mappings change or before PR review
- `flutter test test/features/workflows/workflow_preview_provider_test.dart test/features/workflows/workflow_preview_panel_test.dart`
- `./gradlew clientCi` before merge for client changes
