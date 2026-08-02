# Release notes

Release notes are the durable, user/admin/operator-facing record of what changed. They complement PR descriptions and acceptance evidence; they do not replace tests or support-safe artifacts.

## Files

- [Unreleased](unreleased.md) collects changes that have merged but are not cut into a tagged release yet.
- [v0.1](v0.1.md) records the dogfood-ready review release notes, including the latest published `v0.1.0-rc.3` prerelease facts and evidence links.

## Categories

Every release notes page uses these categories:

- Added
- Changed
- Fixed
- Security
- Accessibility
- Migration/Operator Notes
- Known Issues

Use `Migration/Operator Notes` for admin/operator-impacting changes such as provider configuration, SecretRefs, Compose/reconciliation behavior, backup/restore, support bundles, readiness, audit, and policy/whitelist changes.

## Label-driven process

Every PR must deliberately choose exactly one release notes label before review/merge:

- `release-notes-feature` — included in generated release notes under Added/Changed-style sections.
- `release-notes-bugfix` — included in generated release notes under Fixed.
- `release-notes-skip` — excluded from generated release notes.

Release notes are generated from merged PR labels, not manually reconstructed later. The CI `Release Notes Label Check` fails PRs with zero or multiple release-notes labels.

Use the local generator for release drafts from merged PR metadata:

```sh
GH_TOKEN=... python3 tools/release_notes_generate.py --repo masssi164/weave --since 2026-05-24T21:09:00Z --output docs/release-notes/unreleased.md
```

Use `--dry-run` to inspect output without writing, and use `--input tools/fixtures/release_notes_prs.json` for deterministic local checks. Issue #293 tracks the remaining automation to publish GitHub release drafts from this source of truth. Checked-in release notes pages remain concise, user/admin/operator-oriented drafts linked to deeper docs when needed.

At release cut, generated notes should move into the versioned release notes file and `unreleased.md` should reset to empty category headings plus any current known blockers. `v0.1.0-rc.3` follows this pattern: versioned notes hold the release facts, while Unreleased is reset for later changes and the current #591 manual assistive-technology blocker. Run `make release-notes-check` or `make docs-check` before requesting review.

Release notes must stay honest about shipped, gated, disabled, degraded, or future behavior. Do not describe preview-only or guarded surfaces as generally available.
