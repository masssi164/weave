# Implementation plan: Organization embedding and provider-neutral product core

**Spec**: `specs/0001-organization-embedding-product-core/spec.md`  
**Branch**: `spec/weave-0001-org-embedding`  
**Date**: 2026-05-28

## Summary

Draft the first product-core spec around organization embedding, provider-neutral categories, IDM/RBAC capability policy, readiness states, and support-safe evidence. No product implementation should start until Massimo/team resolves the open product-core questions in the spec and issue #381.

## Constitution check

- Repo truth recovered from `main`, docs, GitHub issue/PR state, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: partial; blocked by product-scope clarification
- Accessibility/supportability/auditability/deployability addressed: partial; draft requirements included, concrete gates pending
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver/OpenClaw runtime remains governed and disabled-by-default unless explicitly in scope: yes

Any `no` requires a blocker or a documented exception before implementation.

## Affected areas

- `client/`: possible future member/admin capability-state UX; no draft implementation.
- `server/`: possible future policy/readiness/domain contracts; no draft implementation.
- `admin-console/`: possible future provider-category readiness UX; no draft implementation.
- `infra/`: possible future provider/bootstrap evidence; no draft implementation.
- `e2e/`: future product-language acceptance once scope is confirmed.
- `docs/`: spec and issue traceability only in this draft slice.
- `release/`: no release behavior while draft.
- `tools/`: no tool changes while draft.

## Contracts and tests first

1. Product acceptance/Gherkin: blocked on first-slice confirmation.
2. Mapping/evidence marker: blocked on story split.
3. API/event/schema contracts: blocked on selected surface.
4. Unit/widget/backend/admin tests: blocked on selected implementation area.
5. CI/evidence artifacts: `./gradlew specContract` and `./gradlew acceptanceContract` for draft PR.

## Agent work breakdown

Use specialists only when they reduce risk or parallelize independent files. Each brief must include allowed files, stop conditions, and a required gate.

- Product/spec steward: refine wording after #381 decisions.
- Client/accessibility: only after member/admin UX scope is selected.
- Server/domain facade: only after capability/policy contract scope is selected.
- Admin/policy: likely first implementation specialist if Admin/Provider Control Plane is confirmed.
- Provider/infra: only if bootstrap/readiness evidence changes enter scope.
- QA/evidence: create Gherkin/mappings after story split.
- Docs/release: keep docs aligned with accepted scope.
- Security/privacy review: validate no raw provider/secret leakage and audit posture.

## Rollout and migration

- Backward compatibility: draft/spec-only, no runtime changes.
- Data migration: none until concrete domain model changes are accepted.
- Feature flag/capability gate: to be defined after product-core decision.
- Rollback plan: revert draft spec PR.
- Release evidence: spec/acceptance gates only while draft.

## Risks and mitigations

- Risk: Agents invent product-core decisions to make implementation easier.
  - Mitigation: keep spec `draft` with explicit `[NEEDS CLARIFICATION: ...]` markers.
  - Evidence gate: `./gradlew specContract` must allow draft markers but block them once status changes.
- Risk: Weaver runtime scope leaks into v0.1 before organization policy exists.
  - Mitigation: keep Weaver disabled/default placeholder unless Massimo/team explicitly accepts runtime scope.
  - Evidence gate: acceptance scenarios must distinguish placeholder/category from runtime behavior.

## Final gates

- `./gradlew specContract`
- `./gradlew acceptanceContract`
- Smallest area gate(s): none until implementation area selected
- `./gradlew ci` when cross-stack or release-relevant
