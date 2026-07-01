# Spec-driven development for Weave

Status: active delivery framework, updated 2026-05-31.

Weave now separates two truths:

- **Specification truth**: the fachliche Weave Specification Corpus, pinned by `specs/weave-specs.lock.json` and normally located at `../weave-specs`.
- **Implementation/evidence truth**: this monorepo, GitHub issues/PRs/checks, CI artifacts, release evidence, and runtime evidence.

The implementation repository must conform to the pinned specification corpus. It must not silently redefine product/domain meaning.

## Framework

Weave SDD uses a combined framework:

- GitHub Spec Kit lifecycle: constitution -> specify -> plan -> tasks -> implement.
- Kiro-style split: steering files plus domain/provider specs.
- Domain-Driven Design: bounded contexts, ubiquitous language, context map, provider anti-corruption layers.
- Specification by Example / BDD / Gherkin: product-language examples mapped to executable acceptance.
- C4 and ADRs: architecture views and decision records.
- OpenAPI, AsyncAPI, and JSON Schema: contract-first APIs, events, reports, and evidence structures.
- OpenClaw Orchestrator Pattern: `weave-co-leader` routes scoped work to L2/L3 agents with file-reference-only briefs and Veto gates.

## Canonical corpus

The canonical corpus owns:

- product constitution;
- ubiquitous language;
- domain context map;
- domain specs;
- provider specs;
- acceptance examples;
- OpenAPI/AsyncAPI/JSON Schema contracts;
- C4/ADR architecture decisions;
- release/RC gate definitions.

This repo owns:

- code and generated code;
- tests and acceptance mappings;
- CI and Gradle gates;
- release notes and support-safe evidence;
- GitHub issue/PR/milestone state;
- generated projections or historical transitional specs that prove conformance.

## Repo-local spec inventory

Repo-local spec-like artifacts are classified in `specs/spec-inventory.yaml`. Treat that file as the implementation repository inventory for transitional packets, conformance fixtures, and repo-owned evidence. It does not replace the pinned corpus; it explains how this repo still depends on local packets while migration/conformance work continues.

Runtime evidence from the separate Weaver/OpenClaw repository may be required when Weave work depends on runtime behavior, but that runtime repository does not redefine Weave product/domain meaning.

## Required first step for agents

Before coding, opening PRs, merging, or declaring work complete:

1. Read `specs/weave-specs.lock.json`.
2. Read the relevant files in the pinned spec corpus.
3. Read `specs/spec-inventory.yaml` for any repo-local transitional packet, fixture, or evidence artifact in scope.
4. Identify the bounded context, provider boundary, examples, contracts, and Veto dimensions.
5. Only then inspect implementation repo files and GitHub state.
6. If the work depends on Weaver/OpenClaw runtime behavior, inspect that separate runtime repository as dependency evidence.
7. Run `./gradlew specCorpusConformance` for spec-driven work.

## Product-core safety rule

Do not let assistants invent unresolved product decisions. If an answer changes Weave product meaning, provider promises, domain vocabulary, accessibility/supportability/auditability/deployability, or Weaver policy, update the spec corpus first or mark the work blocked.

## Sprint orchestration

A sprint is the execution of a spec-backed issue DAG, not a single implementation PR.

Expected autonomous flow:

1. `weave-co-leader` identifies governing spec corpus files.
2. Product/spec and architecture/contract reviewers validate fachliche readiness.
3. Quality/evidence reviewers require examples and executable mapping.
4. Security/privacy/accessibility/provider/ops reviewers apply Veto where relevant.
5. Only accepted/implementing specs become implementation issues.
6. Scoped implementers receive file-reference-only briefs: spec IDs, paths, allowed files, gates, stop conditions.
7. PRs prove conformance with tests, CI, evidence artifacts, and exactly one release-notes label.
8. Sprint closure cites spec version, issue DAG, merged PRs, gates, evidence, unresolved decisions, and next safe action.

Durable sprint state belongs in the spec corpus, GitHub, repo evidence, and checked-in closure reports. Agent chat/session state is disposable.

## DevOps traceability

Every implementation PR should connect:

```text
spec corpus commit/version
  -> domain/provider spec ID
  -> acceptance example / scenario mapping marker
  -> contract schema when applicable
  -> GitHub issue/PR
  -> local/GitHub gate commands
  -> sanitized evidence artifacts
  -> exactly one release-notes label
```

Minimum gates:

- `./gradlew specCorpusConformance` for spec-driven work.
- `./gradlew specContract` while transitional repo-local specs still exist.
- `./gradlew acceptanceContract` when product behavior or Gherkin may be affected.
- Smallest area gate (`clientCi`, `serverCi`, `adminCi`, `infraStatic`, `docsStructureCheck`, etc.).
- `./gradlew ci` for cross-stack or release-relevant changes when local toolchain supports it.

## AI-assisted delivery model

The delivery lead coordinates; scoped reviewers/implementers execute narrow tasks. Runtime configuration and allowlists are operator-owned outside this product repository. The repo-local workflow contract is [AI-assisted delivery orchestration](agent-team-orchestration.md).

Do not add live agent allowlists, personal operator paths, model routing, private hierarchy definitions, or operator-runtime JSON examples to this product repo.

## Hard stops

Stop before:

- secrets or raw provider payload exposure;
- live infra mutation without approval;
- data loss or destructive cleanup;
- history rewrite;
- hidden scope expansion;
- product-core ambiguity;
- implementation that lacks a governing spec corpus path.
