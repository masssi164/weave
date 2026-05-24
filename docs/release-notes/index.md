# Release notes

Release notes are the durable, user/admin/operator-facing record of what changed. They complement PR descriptions and acceptance evidence; they do not replace tests or support-safe artifacts.

## Files

- [Unreleased](unreleased.md) collects changes that have merged but are not cut into a tagged release yet.
- [v0.1](v0.1.md) records the dogfood-production release notes.

## Categories

Every release notes page uses these categories:

- Added
- Changed
- Fixed
- Security
- Accessibility
- Migration/Operator Notes
- Known Issues

Use `Migration/Operator Notes` for admin/operator-impacting changes such as provider configuration, SecretRefs, OpenTofu/bootstrap behavior, backup/restore, support bundles, readiness, audit, and policy/whitelist changes.

## Label-driven process

Every PR must deliberately choose exactly one release notes label before review/merge:

- `release-notes-feature` — included in generated release notes under Added/Changed-style sections.
- `release-notes-bugfix` — included in generated release notes under Fixed.
- `release-notes-skip` — excluded from generated release notes.

Release notes are generated from merged PR labels, not manually reconstructed later. The CI `Release Notes Label Check` fails PRs with zero or multiple release-notes labels.

Full generation from merged PR metadata is tracked in [release notes automation follow-up](https://github.com/masssi164/weave/issues/291). Until then, checked-in release notes pages are draft companions to the label source of truth: keep entries concise, user/admin/operator-oriented, and linked to deeper docs when needed.

At release cut, generated notes should move into the versioned release notes file and `unreleased.md` should reset to empty category headings. Run `make release-notes-check` or `make docs-check` before requesting review.

Release notes must stay honest about shipped, gated, disabled, degraded, or future behavior. Do not describe preview-only or guarded surfaces as generally available.
