# Specification governance in the DevOps flow

The DevOps lanes exist to protect the specification boundary, not to replace it.

## Truth boundary

- Canonical fachliche product/domain truth is the pinned Weave Specification Corpus in `specs/weave-specs.lock.json`.
- This repository is implementation and evidence truth: code, tests, CI, release evidence, issues, PRs, and generated or transitional spec projections.
- Repo-local `specs/` content can prove or project conformance, but it must not silently redefine the pinned corpus.

## Required spec impact declaration

Every PR declares one spec impact:

- `none` — no product/domain contract changed;
- `implements locked spec` — implementation or evidence now conforms to the pinned corpus;
- `updates spec` — a linked corpus change is required or already accepted;
- `changes evidence only` — evidence, gates, fixtures, or reports changed without changing product/domain meaning.

## Lane-specific spec rules

- `dev` receives normal spec integration and conformance-fix work.
- `future/*` may explore larger product lines, but each merge toward `dev` must reconcile with the pinned corpus or link the required corpus change.
- `rc/*` should not introduce new product/domain meaning. If a release candidate exposes a spec gap, either fix conformance, defer the scope, or record a release-gate exception.
- `main` must only receive spec-consistent, release-ready truth or a documented emergency hotfix.

## Conflict handling

When implementation and corpus disagree:

- do not normalize the mismatch away in docs;
- name the mismatch in the linked issue or PR;
- choose conformance fix or spec-change path;
- run `./gradlew specCorpusConformance` and `./gradlew specContract` when feasible;
- record any skipped gate with the reason and next safe action.
