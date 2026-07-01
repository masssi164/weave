# Specification source of truth

Status: active repo policy.

Weave uses a split source-of-truth model so product meaning, implementation proof, and runtime dependency evidence do not drift into competing specifications.

## Authoritative layers

1. **Product/domain truth** lives in the pinned Weave Specification Corpus referenced by `specs/weave-specs.lock.json`.
2. **Implementation/evidence truth** lives in this repository: code, tests, CI, generated contracts, support-safe evidence, release notes, GitHub issues/PRs/checks, and milestones.
3. **Runtime dependency truth** for Weaver/OpenClaw behavior lives in the Weaver/OpenClaw runtime repository and its own specs, docs, source, tests, and CI. Those artifacts can prove runtime capability, but they do not override the Weave product corpus.

## Local repo specs

The `specs/` directory in this repository is not a second product corpus. It contains transitional Spec Kit packets, conformance projections, fixtures, and implementation evidence that must conform to the pinned corpus.

The machine-readable classification is `specs/spec-inventory.yaml`.

Use it before editing or deleting repo-local spec-like artifacts:

- `transitional-conformance`: product/domain meaning should move to the pinned corpus; keep the repo copy only while tasks, traceability, or gates still need it.
- `conformance-fixture`: keep fixtures/projections that tests or gates consume, but do not use them to redefine product meaning.
- `implementation-evidence`: repo-owned evidence, topology, CI, or release proof; keep it in this repo unless promoted into corpus release gates.

## Required workflow

Before product/domain, architecture, provider, auth/policy, acceptance, release-claim, or Weaver-related implementation work:

1. Read `specs/weave-specs.lock.json`.
2. Read the governing files in the pinned Weave Specification Corpus.
3. Check `specs/spec-inventory.yaml` for any repo-local transitional packet or fixture in scope.
4. If the change depends on Weaver/OpenClaw runtime behavior, inspect the separate runtime repository's relevant specs/docs/source/tests as dependency evidence.
5. If corpus and repo reality disagree, open an explicit spec-corpus change or implementation conformance fix. Do not silently let code, repo-local specs, issues, or chat redefine product/domain truth.
6. Run `./gradlew specCorpusConformance` and the smallest relevant implementation/evidence gate.

## Cleanup policy

Do not mass-delete repo-local specs just because the corpus is canonical. Cleanup must preserve evidence and gates:

1. Migrate remaining product/domain meaning into the pinned corpus.
2. Move required fixtures/projections into clear test/evidence locations or keep them documented in `specs/spec-inventory.yaml`.
3. Update tests/gates to consume the canonical corpus or generated projections.
4. Remove duplicate transitional packets in focused PRs with local gate evidence.

This policy also applies to AI-assisted work: agents must cite file paths, spec IDs, issues/PRs, and gate results instead of copying product truth into prompts or inventing acceptance from memory.
