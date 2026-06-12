---
id: WEAVE-SPEC-0008
title: Local dogfood topology weave.test
version: 0.1.0
status: proposed
domain: local-dogfood-topology
owner: weave-devops-lead
github_issue: null
supersedes: []
depends_on:
  - WEAVE-SPEC-0000
acceptance_features:
  - e2e/features/v0_1_dogfood_release.feature
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
---

# Feature 0008: Local dogfood topology (`weave.test`)

Corpus sources: `WEAVE-STEERING-SPEC-KIT-OPERATING-MODEL`, `WEAVE-MIGRATION-LEGACY-SPECS-20260612`.

`weave.test` is the only local/dogfood URL truth for Weave implementation, fixtures, docs, and evidence. the obsolete pre-`weave.test` local domain is treated as drift unless it appears in a historical migration note that explicitly rejects it.

## Acceptance

- Repo content contains no active the obsolete pre-`weave.test` local domain references.
- Client, server, infra, docs, screenshots, and E2E evidence use `weave.test` for local/dogfood examples.
- Release-ready claims stay forbidden while live stack E2E or equivalent support-safe blocker evidence is missing.
