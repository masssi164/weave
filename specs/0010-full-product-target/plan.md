# Implementation plan: Full Weave target product contract

**Spec corpus commit/ID**: `specs/weave-specs.lock.json / WEAVE-STEERING-PRODUCT-CONSTITUTION, WEAVE-STEERING-DOMAIN-CONTEXT-MAP, WEAVE-DOMAIN-AGENT-RUNTIME-CONTROL`
**Repo conformance spec**: `specs/0010-full-product-target/spec.md`
**Branch**: `docs/northstar-spec-coverage-complete`
**Date**: 2026-06-13

## Summary

Create a target-product Spec Kit bridge that turns the pinned corpus plus Massimo's waterfall answers into implementation-ready acceptance and task slices. Keep canonical fachliche truth in `../weave-specs`; use this repo spec as transition/conformance and task orchestration.

## Constitution check

- Spec corpus truth recovered from `specs/weave-specs.lock.json` and relevant corpus files: yes
- Repo truth recovered from branch status, docs, GitHub issue/PR state, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: yes
- Accessibility/supportability/auditability/deployability addressed: yes
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver runtime remains governed and disabled-by-default unless explicitly in scope: yes

## Affected areas

- `client/`: Space, domain, and Weaver UX slices after acceptance mapping.
- `server/`: domain facades, provider readiness/migration, Weaver runtime profile and audit slices.
- `admin-console/`: organization setup, provider policy, Weaver control, readiness.
- `infra/`: provider adapter and live evidence only after contract tasks.
- `e2e/`: target acceptance features and mapping.
- `docs/`: product/developer/admin docs and evidence notes.
- `release/`: no release claim in this spec; later evidence gates only.
- `tools/`: conformance guards if new spec lint/mapping checks are required.

## Contracts and tests first

1. Product acceptance/Gherkin: create target-product setup/space/provider-change/evidence examples, then split domain-specific scenarios.
2. Mapping/evidence marker: add `weave_spec_0010_*` markers before implementation claim.
3. API/event/schema contracts: provider readiness/migration reports, Decision/Evidence contracts, signed RuntimeProfile, signed single-use ApprovalDecisionEvidence, and immutable ActionEvidence where not already covered.
4. Unit/widget/backend/admin tests: per task slice.
5. CI/evidence artifacts: `specCorpusConformance`, `specContract`, `acceptanceContract`, plus domain gates.

## Agent work breakdown

- Product/spec steward: update corpus domain/steering text and repo Spec Kit artifacts.
- Client/accessibility: derive Space and Weaver UX slices after scenarios exist.
- Server/domain facade: prove provider-neutral facades, migration evidence, decisions/evidence, and Weaver profile policy.
- Admin/policy: prove Control flows for setup, provider changes, and Weaver eligibility/policy.
- Provider/infra: add only support-safe provider evidence and no live mutation without approval.
- QA/evidence: add Gherkin/mappings and fixture evidence.
- Docs/release: document target/product boundaries without release/v0.1 claims.
- Security/privacy review: verify no OpenClaw private runtime leakage and no secret-bearing artifacts.

## Rollout and migration

- Backward compatibility: target artifacts guide future work; no runtime behavior changes by this spec alone.
- Data migration: provider-change tasks must require dry-run/no-loss evidence.
- Feature flag/capability gate: Agent Runtime Control is gated by authoritative Keycloak group membership deriving `agent-runtime.entitled`; RuntimeProfile v2 never grants authorization.
- Rollback plan: provider changes and profile changes require rollback/revocation paths.
- Release evidence: target spec is not a release claim.

## Risks and mitigations

- Risk: target spec duplicates canonical corpus.
  - Mitigation: mark repo artifact as conformance projection and update corpus first.
  - Evidence gate: `./gradlew specCorpusConformance` and corpus lint.
- Risk: Weaver leaks local OpenClaw runtime concepts.
  - Mitigation: forbid personal paths, private model routing, allowlists, and `weave-co-leader` product vocabulary.
  - Evidence gate: grep/review plus Spec Kit traceability.

## Final gates

- `./gradlew specCorpusConformance`
- `./gradlew specContract`
- `./gradlew acceptanceContract`
- Smallest area gate(s): docs/spec inspection for this slice
- `./gradlew ci` when cross-stack or release-relevant
