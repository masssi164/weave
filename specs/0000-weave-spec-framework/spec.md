---
id: WEAVE-SPEC-0000
title: Weave spec-driven development framework
version: 0.1.0
status: implemented
domain: delivery
declared_scope: process-framework
owner: weave-co-leader
github_issue: null
supersedes: []
depends_on: []
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew specContractTest
  - ./gradlew docsStructureCheck
---

# Feature specification: Weave spec-driven development framework

## Intent

Weave needs a repo-local, versioned, evidence-backed specification framework so product-core decisions, implementation plans, acceptance mappings, and agent handoffs stay traceable across humans and AI specialists.

This framework does not define new member-facing product behavior. It defines the governance and tooling that future product-core specs must use.

## Product boundaries

### In scope

- A Weave specification constitution under `.specify/memory/constitution.md`.
- Weave-specific spec, plan, task, agent briefing, and OpenClaw/ACP team configuration templates.
- A repo-local `specs/` directory convention.
- A deterministic `specContract` guard for required metadata and unsafe clarification drift.
- Documentation that explains how `weave-co-leader` coordinates specialists.

### Out of scope

- Choosing a final Chat/Files/Calendar/Boards product-core slice.
- Changing runtime behavior for members, admins, providers, or Weaver.
- Enabling ACP/Copilot/Codex/Gemini runtime configuration.
- Pushing, releasing, or mutating live infrastructure.

## User/admin/operator stories

### US1 - Developer creates a reviewable spec (Priority: P1)

**Actor**: Developer or agent  
**Story**: A contributor can create a spec with stable metadata, status, acceptance/evidence expectations, and open product questions without guessing core behavior.  
**Why now**: Weave is moving from sprint-doc-driven work to product-core spec-driven work.  
**Independent test**: `./gradlew specContract` validates the framework spec and templates.

**Acceptance scenarios**:

1. Given a repo-local spec, when the guard runs, then required metadata and lifecycle state are validated.
2. Given an implementation-ready spec, when clarification markers remain, then the guard fails before review.

### US2 - Team lead briefs specialists safely (Priority: P1)

**Actor**: `weave-co-leader`  
**Story**: The team lead can assign scoped specialist work with allowed files, stop conditions, runtime policy, and required evidence.  
**Why now**: Weave work should use compact templates and evidence returns instead of transcript dumps.  
**Independent test**: Agent briefing templates exist and are linked from the docs.

**Acceptance scenarios**:

1. Given a spec plan, when the co-leader needs implementation help, then it can brief only the needed specialist role with exact gates and stop conditions.
2. Given an ACP harness request, when the runtime policy rejects an agent id, then the co-leader reports the policy error instead of silently switching runtimes.

## Functional requirements

- **FR-001**: The repo MUST contain a Weave specification constitution that preserves product-first, provider-neutral, evidence-first delivery.
- **FR-002**: The repo MUST contain templates for specs, plans, tasks, and agent briefs.
- **FR-003**: Specs MUST be versioned in Git under `specs/` and validated by a deterministic local guard.
- **FR-004**: The guard MUST allow explicit product questions in draft/proposed specs and block them in accepted/implementing/implemented specs.
- **FR-005**: The framework MUST document which logical specialists `weave-co-leader` can use and the runtime constraints for native subagents vs ACP harnesses.
- **FR-006**: The framework MUST define an optimization-review loop that repeats implementation/review until no material optimization remains or a product-core clarification blocks safe progress.
- **FR-007**: The framework MUST include a non-live example of the expected OpenClaw native-subagent and ACP profile configuration shape.
- **FR-008**: This framework MUST NOT create a parallel wiki as source of truth.

## Domain model and contracts

- **Spec**: Versioned product/system contract with frontmatter, acceptance/evidence links, and lifecycle status.
- **Plan**: Technical implementation approach constrained by a spec and the constitution.
- **Task list**: Independently testable slices mapped to stories/areas and gates.
- **Agent brief**: Scoped work order for a specialist with allowed files, gate, and stop conditions.
- **Optimization review**: Adversarial but practical review that returns only material improvements against the spec, runtime policy, and evidence.
- **Evidence gate**: Command or artifact that proves a claim without raw secret/provider leakage.

## Acceptance and evidence mapping

- Gherkin feature path(s): none; process-only framework.
- `e2e/scenario_mappings.json` marker(s): none; no member-facing product behavior changes.
- Tooling test path(s): `tools/spec_contract_check.py`, `tools/spec_contract_check_test.py`.
- Agent team config example: `.specify/templates/weave-agent-team-config.example.json5`.
- Live Stack E2E required? no; repo-process only.
- Support-safe evidence artifact(s): `build/evidence/ci-summary.json` when run through `./gradlew ci`.

## Release and migration impact

- Member impact: none.
- Admin/operator impact: future specs become more traceable.
- Developer/API impact: new `./gradlew specContract` gate and spec templates.
- Data migration/backfill: none.
- Rollback/reversibility: remove the added spec framework files and Gradle/Makefile wiring.
- Release-notes label expected: `release-notes-skip`.

## Open questions

None for this framework slice. Future product-core specs must keep unresolved product questions in `draft` or `proposed` state until Massimo/team review resolves them.
