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

## Process

1. For release-affecting changes, add an entry to `docs/release-notes/unreleased.md` in the same PR.
2. Keep entries concise and user/admin/operator-oriented; link deeper docs when needed.
3. At release cut, move entries into the versioned release notes file and reset `unreleased.md` to empty category headings.
4. Run `make release-notes-check` or `make docs-check` before requesting review.

Release notes must stay honest about shipped, gated, disabled, degraded, or future behavior. Do not describe preview-only or guarded surfaces as generally available.
