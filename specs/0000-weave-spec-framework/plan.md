# Implementation plan: Weave spec-driven development framework

**Spec**: `specs/0000-weave-spec-framework/spec.md`  
**Branch**: local implementation branch or short-lived PR branch  
**Date**: 2026-05-28

## Summary

Add a lightweight Weave-specific Spec Kit layer without importing a heavy workflow wholesale. The framework stores versioned specs in the repo, validates lifecycle metadata with a local guard, and documents repo-safe AI-assisted delivery orchestration.

## Constitution check

- Repo truth recovered from `main`, docs, GitHub issue/PR state, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: yes
- Accessibility/supportability/auditability/deployability addressed: yes
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver/OpenClaw runtime remains governed and disabled-by-default unless explicitly in scope: yes

## Affected areas

- `.specify/`: constitution and Weave templates.
- `specs/`: framework spec and convention README.
- `docs/`: spec-driven development guide and navigation.
- `tools/`: deterministic spec contract guard.
- `build.gradle`/`Makefile`: local gate wiring.
- `.github/pull_request_template.md`: spec/evidence traceability prompt.
- `AGENTS.md`: point agents at repo-local specs and fallback review reality.

## Contracts and tests first

1. Product acceptance/Gherkin: not applicable for process-only framework.
2. Mapping/evidence marker: not applicable.
3. API/event/schema contracts: not applicable.
4. Tooling tests: `python3 tools/spec_contract_check.py` and `python3 tools/spec_contract_check_test.py`.
5. CI/evidence artifacts: Gradle `specContract` and CI summary wiring.

## Agent work breakdown

- Product/spec steward: owns spec lifecycle and frontmatter discipline.
- Client/accessibility: used when member/admin UI behavior changes.
- Server/domain facade: used when domain contracts, authz, audit, or provider boundaries change.
- Admin/policy: used when Admin Console, IDM/RBAC, readiness, whitelisting, or policy changes.
- Provider/infra: used when adapters, OpenTofu, runner, backup/restore, or live-stack infrastructure changes.
- QA/evidence: owns Gherkin, mapping, evidence markers, Live Stack evidence, and sanitized artifacts.
- Docs/release: owns docs nav, handbooks, release notes, closure reports, and PR templates.
- Security/privacy review: used for secrets, raw provider data, auth, audit, and support-safe diagnostics.
- Integration review: used before PR handoff/merge readiness.

## Rollout and migration

- Start with framework-only spec `WEAVE-SPEC-0000`.
- Use the next product-core slice as the first real `WEAVE-SPEC-0001` only after Massimo resolves required product-core questions.
- Keep generated wiki/docs as projections, not canonical source.

## Risks and mitigations

- Risk: Markdown overload for small bugs.
  - Mitigation: full workflow only for product/architecture/provider/auth/release-relevant changes; small bugs may use issue/spec notes.
  - Evidence gate: PR template asks for spec/note and smallest local gate.
- Risk: Agent hallucination of product core.
  - Mitigation: clarification markers allowed only in draft/proposed specs; accepted/implementing specs fail guard if markers remain.
  - Evidence gate: `./gradlew specContract`.
- Risk: assistant runtime-policy confusion.
  - Mitigation: assistant brief templates document repo-safe handoff rules, operator-owned runtime boundaries, and policy-failure reporting without deployable config examples.
  - Evidence gate: docs review and `./gradlew specContract`.

## Final gates

- `./gradlew specContract`
- `./gradlew specContractTest`
- `./gradlew docsStructureCheck`
- `./gradlew acceptanceContract`
