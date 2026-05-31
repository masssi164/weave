# Weave Specification Constitution

Version: 0.1.0  
Ratified: 2026-05-28  
Last amended: 2026-05-28

This constitution governs implementation-repo conformance to the pinned Weave Specification Corpus referenced by `specs/weave-specs.lock.json`, plus transitional repo-local specifications under `specs/`, Spec Kit-style templates under `.specify/templates/`, and repo-safe AI-assisted delivery guidance. It keeps fachliche specification truth separate from implementation/evidence truth.

## Core principles

### I. Repo truth over chat memory

Repo truth over chat memory means this monorepo, GitHub issues/PRs, protected-branch status, and sanitized CI/evidence artifacts are the current implementation/evidence truth. Fachliche product/domain specification truth lives in the pinned Weave Specification Corpus. Agent memory, old chat, older checkouts, and external notebooks are orientation only. Generated wiki/docs/projections may explain specs, but must not become canonical truth.

### II. Product-core before implementation

The pinned specification corpus must describe Weave product outcomes before implementation tactics. Product vocabulary is category-first and provider-neutral: identity/IDM, spaces, chat, files, calendar, boards/tasks, meetings, documents/collaboration, decisions/evidence, admin health/ops, and the optional Weaver runtime. Raw provider setup, secrets, endpoint rotation, support diagnostics, and policy authoring stay admin/operator side.

### III. Acceptance and evidence first

For new product behavior, write or update product-language acceptance in the spec corpus before implementation. A reviewable scenario needs a stable mapping to executable evidence in `e2e/scenario_mappings.json` or a named draft/proposed blocker. `./gradlew specCorpusConformance`, `./gradlew acceptanceContract`, and the transitional `./gradlew specContract` are the minimum spec/evidence gates.

### IV. Accessibility, supportability, auditability, deployability

These are release blockers, not polish. Specs and plans must call out member/admin/operator impact, screen-reader and non-color-only behavior when relevant, support-safe diagnostics, audit posture, and deployment/release evidence.

### V. Provider-neutral and anti-silo by default

Provider adapters serve Weave-owned domain contracts. Specs must preserve export/delete/provenance/migration/dry-run behavior and must not hardcode one vendor as the product model. Provider names belong in admin readiness and operator evidence, not normal member navigation.

### VI. Weaver is governed and disabled by default

Weaver-derived PA runtime is optional, later, per-user, isolated, auditable, generated from organization policy, and governed by `user-rights, organization-whitelisted capabilities`. Agentic developer help may be a governed capability, but live assistant runtime configuration, allowlists, model routing, and operator hierarchy must not live in this product repository or become a bypass around disabled exec/elevated defaults.

### VII. Small slices, no markdown theater

Full spec workflow is required for product capabilities, architectural migrations, provider/auth/policy/acceptance changes, and release-blocking work. Small bug fixes may use a lightweight issue/spec note, but still need a test/evidence path. Do not create redundant planning documents that reviewers cannot validate.

## Required spec lifecycle

Allowed status values:

- `draft`: incomplete; may contain `[NEEDS CLARIFICATION: ...]` markers.
- `proposed`: reviewable direction; may still contain explicit open questions.
- `accepted`: product/technical contract accepted; no clarification markers.
- `implementing`: active work; no clarification markers.
- `implemented`: code/evidence merged or ready for merge; no clarification markers.
- `superseded`: replaced by another spec.
- `deprecated`: intentionally being phased out.
- `rejected`: deliberately not pursued.

Every non-draft implementation PR must link issue/spec/evidence and select exactly one release-notes label.

## Assistant governance

The delivery lead is an orchestrator, not a mega-coder. It recovers truth from repo/GitHub/CI, selects the smallest next slice, briefs scoped reviewers/implementers with allowed files and gates, integrates results, and returns evidence. Reviewers and implementers must return concise evidence, blockers, and diffs/gates — not transcript dumps.

Assistant invocation must respect runtime policy:

- Repo-local docs describe logical roles and evidence expectations only.
- Live assistant runtime configuration, allowlists, personal operator paths, model routing, and hierarchy definitions stay outside this product repository.
- Coding harnesses are used only when explicitly requested or when the operator environment selects them; policy rejections must be reported clearly.
- Do not require Copilot review while premium requests are exhausted; record human/assistant fallback review plus green CI instead.
- Stop before secrets, data loss, live infra mutation, history rewrite, or hidden scope expansion.

## Governance

This constitution supersedes ad-hoc sprint habits for spec-driven work. Amendments require a PR that updates this file, `docs/spec-driven-development.md`, `specs/weave-specs.lock.json` when the canonical corpus changes, and any affected templates or guards. The primary conformance guard is `./gradlew specCorpusConformance`; the transitional repo-local guard remains `./gradlew specContract`.
