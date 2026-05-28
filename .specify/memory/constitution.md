# Weave Specification Constitution

Version: 0.1.0  
Ratified: 2026-05-28  
Last amended: 2026-05-28

This constitution governs repo-local specifications under `specs/`, Spec Kit-style templates under `.specify/templates/`, and `weave-co-leader` agent orchestration. It turns Weave planning into versioned, reviewable, executable product contracts.

## Core principles

### I. Repo truth over chat memory

The current source of truth is this monorepo, GitHub issues/PRs, protected-branch status, and sanitized CI/evidence artifacts. Agent memory, old chat, older checkouts, and external notebooks are orientation only. A generated wiki or documentation site may explain specs, but it must not become the canonical source.

### II. Product-core before implementation

Specs must describe Weave product outcomes before implementation tactics. Product vocabulary is category-first and provider-neutral: identity/IDM, chat, files, calendar, boards/tasks, meetings, documents/collaboration, decisions, health, and the optional Weaver runtime. Raw provider setup, secrets, endpoint rotation, support diagnostics, and policy authoring stay admin/operator side.

### III. Acceptance and evidence first

For new product behavior, write or update product-language acceptance before implementation. A reviewable scenario needs a stable mapping to executable evidence in `e2e/scenario_mappings.json` or a named draft/proposed blocker. `./gradlew acceptanceContract` and `./gradlew specContract` are the minimum spec gates.

### IV. Accessibility, supportability, auditability, deployability

These are release blockers, not polish. Specs and plans must call out member/admin/operator impact, screen-reader and non-color-only behavior when relevant, support-safe diagnostics, audit posture, and deployment/release evidence.

### V. Provider-neutral and anti-silo by default

Provider adapters serve Weave-owned domain contracts. Specs must preserve export/delete/provenance/migration/dry-run behavior and must not hardcode one vendor as the product model. Provider names belong in admin readiness and operator evidence, not normal member navigation.

### VI. Weaver is governed and disabled by default

Weaver/OpenClaw-derived PA runtime is optional, later, per-user, isolated, auditable, generated from organization policy, and governed by `user-rights, organization-whitelisted capabilities`. Agentic developer help may be a governed capability, but it must not become the product architecture or a bypass around disabled exec/elevated defaults.

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

## Agent governance

`weave-co-leader` is an orchestrator, not a mega-coder. It recovers truth from repo/GitHub/CI, selects the smallest next slice, briefs specialists with allowed files and gates, integrates results, and returns evidence. Specialists must return concise evidence, blockers, and diffs/gates — not transcript dumps.

Agent invocation must respect runtime policy:

- Native OpenClaw subagents are used for repo-aware specialists.
- ACP harnesses are used only when explicitly requested or when a coding harness is the right runtime; use allowed ACP harness IDs and report policy rejections clearly.
- Do not require Copilot review while premium requests are exhausted; record human/agent fallback review plus green CI instead.
- Stop before secrets, data loss, live infra mutation, history rewrite, or hidden scope expansion.

## Governance

This constitution supersedes ad-hoc sprint habits for spec-driven work. Amendments require a PR that updates this file, `docs/spec-driven-development.md`, and any affected templates or guards. The guard task is `./gradlew specContract`.
