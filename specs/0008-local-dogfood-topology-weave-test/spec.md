# Feature 0008: Local dogfood topology (`weave.test`)

Corpus sources: `WEAVE-STEERING-SPEC-KIT-OPERATING-MODEL`, `WEAVE-MIGRATION-LEGACY-SPECS-20260612`.

`weave.test` is the only local/dogfood URL truth for Weave implementation, fixtures, docs, and evidence. `weave.local` is treated as drift unless it appears in a historical migration note that explicitly rejects it.

## Acceptance

- Repo content contains no active `weave.local` references.
- Client, server, infra, docs, screenshots, and E2E evidence use `weave.test` for local/dogfood examples.
- Release-ready claims stay forbidden while live stack E2E or equivalent support-safe blocker evidence is missing.
