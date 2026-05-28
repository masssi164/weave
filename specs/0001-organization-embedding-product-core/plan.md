# Implementation plan: Admin-Suite and provider-neutral product core

**Spec**: `specs/0001-organization-embedding-product-core/spec.md`  
**Branch**: `spec/weave-0001-org-embedding`  
**Date**: 2026-05-28

## Summary

Integrate Massimo's WEAVE-SPEC-0001 product decisions into an accepted product-core baseline. The sprint establishes Weave as a provider-neutral collaboration platform whose Admin-Suite owns provider/adapter setup, readiness, switching, and recovery while member clients see stable Weave capabilities only.

This PR remains spec/issue-DAG work. Implementation follows in short PRs from the recorded issue DAG.

## Constitution check

- Repo truth recovered from `main`, docs, GitHub issues/PRs, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: yes (#389)
- Accessibility/supportability/auditability/deployability addressed: yes as release blockers
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver/OpenClaw runtime remains out of scope for Spec 0001: yes

Any future `no` requires a blocker or documented exception before implementation.

## Affected areas

- `specs/0001-organization-embedding-product-core/`: accepted product-core baseline.
- GitHub #381: decision record source.
- GitHub #386-#389: implementation/acceptance issue DAG.
- `client/`: future member-safe capability manifest consumption and invite/SSO/passkey flow evidence.
- `server/`: future provider-neutral facade, capability manifest, switch/evidence contracts.
- `admin-console/`: future Admin-Suite readiness/setup/switch UX contracts.
- `infra/`: future provider bootstrap/readiness evidence only when needed.
- `e2e/`: future acceptance scenario mapping (#389).
- `docs/`: future closure/release documentation as implementation lands.
- `release/`: no tag/publish from this spec-only PR.

## Issue DAG / PR train

1. #386 — provider-neutral domain and capability model. Sequential root.
2. #387 — Admin-Suite readiness and setup UX contract. Depends on #386.
3. #388 — provider switch and portable export/import contract. Depends on #386 and #387.
4. #389 — acceptance and evidence mapping. Can run in parallel after #386 contract shape is stable.

Preferred PR train:

1. Spec update and decision record (`release-notes-skip`).
2. Capability/domain model contracts (`release-notes-feature` once behavior changes).
3. Admin readiness/setup UX contract.
4. Provider switch/export-import/rollback contract.
5. Acceptance/evidence mapping and sprint closure.

## Contracts and tests first

1. Product acceptance/Gherkin: tracked by #389.
2. Mapping/evidence marker: tracked by #389.
3. API/event/schema contracts: tracked by #386 and #388.
4. Admin/member UX contracts: tracked by #387.
5. CI/evidence artifacts: every PR runs the smallest meaningful local gate and inspects GitHub CI.

## Agent work breakdown

Use specialists only for narrow slices with explicit files, stop conditions, and evidence gates.

- Product/spec steward: keep WEAVE-SPEC-0001 wording aligned with Massimo's decision record.
- Server/domain facade: own #386 capability/domain contracts and backend-owned provider-neutral manifests.
- Admin/policy: own #387 setup assistant/readiness/switch UX contracts.
- Provider/infra: assist #388 only for portable export/import and readiness evidence boundaries.
- QA/evidence: own #389 Gherkin/mappings/evidence.
- Security/privacy review: validate no raw provider/secret leakage and safe adapter/switch posture.
- Docs/release: closure reports and release notes only after integrated evidence.

## Rollout and migration

- Backward compatibility: spec-only baseline; no runtime behavior changes in this PR.
- Data migration: v0.1 promises portable export/import contracts; full migration automation is later work.
- Feature flag/capability gate: implementation PRs must define capability state behavior before member exposure.
- Rollback plan: revert spec-only PR or individual implementation PRs; switch/cutover PRs require explicit rollback evidence.
- Release evidence: spec/acceptance gates for this PR; implementation PRs add area gates and CI.

## Risks and mitigations

- Risk: Provider-neutrality becomes a thin settings UI with hidden lock-in.
  - Mitigation: #388 requires portable export/import, cutover, and rollback contract.
- Risk: Normal members are exposed to provider/admin details.
  - Mitigation: member-safe capability manifest and Admin-Suite boundary are hard requirements.
- Risk: Weaver/AI runtime sneaks into first product-core scope.
  - Mitigation: Weaver/AI runtime is explicit non-goal for WEAVE-SPEC-0001.
- Risk: Dynamic adapters become unsafe arbitrary plugins.
  - Mitigation: validated adapter packages and rollback, not uncontrolled runtime plugin model.

## Final gates

- `./gradlew specContract`
- `./gradlew specContractTest`
- `./gradlew acceptanceContract`
- `./gradlew ci` only once implementation becomes cross-stack/release-relevant.
