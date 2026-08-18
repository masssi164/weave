# Weave specs in the implementation repository

This directory is **not** the canonical fachliche specification truth anymore.

Canonical specification truth lives in the pinned Weave Specification Corpus referenced by:

- `specs/weave-specs.lock.json`
- default local corpus path: `../weave-specs`

This implementation repository is the **conformance and evidence truth**. It contains code, tests, CI gates, release evidence, generated projections, and historical repo-local specs that must conform to the pinned spec corpus.

For PR #1043 and later open-standards gateway work, repo-local packets that still describe OpenAPI/REST as a normal member data plane for Files, Calendar events, or Chat conversations/messages are obsolete. The canonical northbound collaboration data planes are:

- Files: Weave WebDAV at `/dav/files/**`.
- Calendar: Weave CalDAV/iCalendar at `/caldav/**`.
- Chat: Weave Matrix Client-Server facade at `/_matrix/client/**`.
- OpenAPI: control plane only for readiness, setup, credentials, revoke, admin/provider decisions, generated convenience models, and support-safe evidence.

## Truth boundary

- Specification truth: the corpus at the lockfile `specCorpus.localPath` (default `../weave-specs`), pinned by `specs/weave-specs.lock.json`.
- Implementation/evidence truth: this repo, GitHub issues/PRs/checks, CI artifacts, and checked-in release evidence.
- Generated docs/indexes/projections are not canonical.
- Repo-local spec packets are classified in `specs/spec-inventory.yaml`. Transitional packets and fixtures must not override the corpus, including newer packets such as local dogfood topology, domain-first MCP, full-product target, and governed Weaver PA target.


## Inventory

The classification for every repo-local spec-like artifact is maintained in:

- `specs/spec-inventory.yaml`

Use that inventory before editing, deleting, or citing repo-local packets. It records which artifacts are transitional conformance packets, conformance fixtures, or implementation evidence, and points to likely corpus owners for migration.

## Required workflow

1. Identify governing spec corpus files first.
2. Inspect this repo only after the fachliche spec boundary is known.
3. If spec corpus and repo reality disagree, create an explicit spec-change or conformance-fix task.
4. Run `./gradlew specCorpusConformance` before any spec-driven implementation claim.
5. Run the smallest relevant implementation gates after conformance is established.

## Current local conformance gate

```bash
./gradlew specCorpusConformance
```

This validates that the pinned spec corpus exists, is on the expected commit, has the required domain/steering files, and lint-passes.
