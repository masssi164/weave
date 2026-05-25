# Manuals and release notes integration

Status: implemented Sprint 3 contract, 2026-05-25.

## Decision

Weave documentation is a product surface, not a loose wiki. Member help, admin/operator manuals, and release notes must use the same release discipline as client, server, and infra changes.

## Member and admin manuals

Weave should provide two MkDocs-built manuals:

- **User manual:** embedded as the Help surface in `weave/client`.
- **Admin/operator manual:** embedded in the Admin Console for setup, provider mapping, readiness, backup/restore, migration, and support-bundle guidance.

Both manuals must:

- use the same CSS custom properties/design tokens as the app and Admin Console;
- support dark/light/high-contrast theme behavior without separate copy forks;
- be iframe-embeddable with a strict allowlist and no broad script privileges;
- expose accessible headings, skip links, focus order, and screen-reader-friendly navigation;
- avoid image-only instructions and dense tables as the only source of truth;
- keep provider secrets, raw URLs with credentials, tokens, downstream error bodies, and personal data out of examples and screenshots.

The iframe/webview integration must fail closed: if docs are unavailable, the app shows a support-safe unavailable/help-download state instead of broken provider or filesystem errors.

Sprint 3 implementation records the embed contract in product surfaces without granting broad runtime permissions:

- `client/lib/features/help/presentation/help_screen.dart` exposes the MkDocs user manual as a constrained Help manual surface.
- `client/lib/features/settings/presentation/settings_screen.dart` exposes the Admin/operator manual only in the owner/admin setup surface.
- Both surfaces state the checked-in manual source, constrained embed permissions, shared design-token behavior, and support-safe fallback state.

## README release-note embedding

The README is the public product entry point. Release notes must be generated and inserted automatically rather than copied by hand.

Required contract:

- Keep `<!-- WEAVE_RELEASE_NOTES_START -->` and `<!-- WEAVE_RELEASE_NOTES_END -->` markers in `README.md`.
- Generate release notes from merged PR metadata and/or versioned release-note files using the repo release-notes automation.
- Reject release-note generation when a merged PR has zero or multiple release-note classification labels, except explicit `release-notes-skip`.
- Preserve a reviewable GitHub release draft path before publishing; the `Release draft` workflow must create/update drafts only and upload README/release-note artifacts for review.
- Keep README copy honest about shipped, gated, disabled, and future surfaces.
- Require marketing/product review before public positioning changes are declared final.

## Acceptance criteria

- Client Help embeds the user manual through a constrained iframe/webview surface using shared design tokens.
- Admin Console embeds the admin/operator manual through the same design-token contract.
- MkDocs output can be built and checked in CI without leaking secrets or relying on external network calls.
- README release-note block is updated only by automation or an explicitly documented release-maintainer command.
- Accessibility checks cover headings, labels, keyboard reachability, and non-color-only status cues inside embedded docs.
