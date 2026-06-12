---
id: WEAVE-SPEC-0000
title: Weave spec-driven development framework
version: 0.1.0
status: implemented
domain: delivery
declared_scope: process-framework
owner: delivery-owner
github_issue: null
supersedes: []
depends_on: []
acceptance_features:
  - e2e/features/northstar_spec_decisions.feature
evidence_gates:
  - ./gradlew specContract
  - ./gradlew specContractTest
  - ./gradlew docsStructureCheck
---

# Feature specification: Weave spec-driven development framework

## Intent

Weave needs a repo-local, versioned, evidence-backed specification framework so product-core decisions, implementation plans, acceptance mappings, and AI-assisted handoffs stay traceable across contributors and reviewers.

This framework does not define new member-facing product behavior. It defines the governance and tooling that future product-core specs must use.

## Product boundaries

### In scope

- A Weave specification constitution under `.specify/memory/constitution.md`.
- Weave-specific spec, plan, task, and repo-safe assistant briefing templates.
- A repo-local `specs/` directory convention.
- A deterministic `specContract` guard for required metadata and unsafe clarification drift.
- Documentation that explains repo-safe AI-assisted delivery roles, handoffs, runtime boundaries, and evidence gates.

### Out of scope

- Choosing a final Chat/Files/Calendar/Boards product-core slice.
- Changing runtime behavior for members, admins, providers, or Weaver.
- Enabling or documenting deployable coding-agent runtime configuration, model routing, allowlists, or personal operator paths.
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

### US2 - Delivery lead briefs scoped reviewers safely (Priority: P1)

**Actor**: Delivery lead
**Story**: The delivery lead can assign scoped implementation or review work with allowed files, stop conditions, runtime-boundary policy, and required evidence.
**Why now**: Weave work should use compact templates and evidence returns instead of transcript dumps.
**Independent test**: Assistant briefing templates exist and are linked from the docs.

**Acceptance scenarios**:

1. Given a spec plan, when delivery work needs implementation or review help, then the lead can brief only the needed logical role with exact gates and stop conditions.
2. Given a coding-harness request, when the operator environment rejects or lacks the configured runtime, then the lead reports the policy error instead of silently switching runtimes.
3. Given a repo-local framework artifact, when it describes AI-assisted delivery, then it does not include live allowlists, model routing, personal operator paths, private hierarchy definitions, or deployable runtime JSON examples.

## Functional requirements

- **FR-001**: The repo MUST contain a Weave specification constitution that preserves product-first, provider-neutral, evidence-first delivery.
- **FR-002**: The repo MUST contain templates for specs, plans, tasks, and repo-safe assistant briefs.
- **FR-003**: Specs MUST be versioned in Git under `specs/` and validated by a deterministic local guard.
- **FR-004**: The guard MUST allow explicit product questions in draft/proposed specs and block them in accepted/implementing/implemented specs.
- **FR-005**: The framework MUST document logical review/implementation roles and runtime-boundary constraints without encoding live assistant allowlists, model routing, personal paths, or private hierarchy definitions.
- **FR-006**: The framework MUST define an optimization-review loop that repeats implementation/review until no material optimization remains or a product-core clarification blocks safe progress.
- **FR-007**: The framework MUST include compact handoff/review templates and MUST forbid deployable-looking operator-runtime JSON examples in the product repo.
- **FR-008**: This framework MUST NOT create a parallel wiki as source of truth.
- **FR-009**: The pinned Weave Specification Corpus in `specs/weave-specs.lock.json` MUST remain the canonical product/domain source of truth. Repo-local specs MUST be implementation and conformance projections that trace to pinned corpus files, not competing product truth.
- **FR-010**: Work MUST follow the Spec Kit lifecycle: Spec → Plan → Tasks → Implement → Evidence. Implementing or implemented specs MUST have a matching `plan.md`, `tasks.md`, and `traceability.yaml` unless the spec explicitly declares itself process-only and non-implementing.
- **FR-011**: Local dogfood/runtime evidence MUST use `weave.test` / `*.weave.test` as the only local URL truth. The former `.local` dogfood alias is obsolete drift and MUST NOT appear in active specs, docs, code, config, tests, or generated fixtures.
- **FR-012**: Northstar/product-core decisions MUST be projected across every affected repo-local spec, plan or task file before implementation or promotion.
- **FR-013**: Each affected Northstar/product-core claim MUST have a mapped Gherkin scenario in `e2e/features/` and `e2e/scenario_mappings.json`; decorative, unmapped scenarios MUST fail acceptance review.

## Domain model and contracts

- **Pinned corpus**: `../weave-specs` content selected by `specs/weave-specs.lock.json`; canonical product/domain source of truth.
- **Repo-local spec projection**: Versioned implementation/conformance contract under `specs/` with frontmatter, acceptance/evidence links, lifecycle status, and traceability back to the pinned corpus.
- **Plan**: Technical implementation approach constrained by a spec and the constitution.
- **Task list**: Independently testable slices mapped to stories/areas and gates.
- **Traceability map**: `traceability.yaml` linking pinned corpus source → repo-local projection → acceptance scenarios → evidence gates/artifacts.
- **Assistant brief**: Scoped work order for a logical reviewer or implementer with allowed files, gate, and stop conditions.
- **Optimization review**: Adversarial but practical review that returns only material improvements against the spec, runtime policy, and evidence.
- **Evidence gate**: Command or artifact that proves a claim without raw secret/provider leakage.

## Acceptance and evidence mapping

- Gherkin feature path(s): `e2e/features/northstar_spec_decisions.feature`.
- `e2e/scenario_mappings.json` marker(s): `NORTHSTAR_SPEC_COVERAGE_MATRIX`; no member-facing runtime behavior changes.
- Tooling test path(s): `tools/spec_contract_check.py`, `tools/spec_contract_check_test.py`.
- Assistant delivery templates: `.specify/templates/weave-agent-briefs.md` and `docs/agent-team-orchestration.md`.
- Live Stack E2E required? no; repo-process only.
- Support-safe evidence artifact(s): `build/evidence/ci-summary.json` when run through `./gradlew ci`.

## Release and migration impact

- Member impact: none.
- Admin/operator impact: future specs become more traceable while live operator runtime configuration remains outside the product repo.
- Developer/API impact: new `./gradlew specContract` gate and spec templates.
- Data migration/backfill: none.
- Rollback/reversibility: remove the added spec framework files and Gradle/Makefile wiring.
- Release-notes label expected: `release-notes-skip`.

## Open questions

None for this framework slice. Future product-core specs must keep unresolved product questions in `draft` or `proposed` state until product owner/team review resolves them.
