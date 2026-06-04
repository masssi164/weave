# Sprint 29 human UX/accessibility validation template

Issues: #652, #654. Human signoff status: pending.

Do not paste secrets, tokens, raw provider payloads, raw provider error bodies, or private user content into this report. Use screenshots only when they are redacted and necessary.

## Entry condition

Run this only after `python3 tools/sprint29_release_decision_guard.py entry --evidence <generated-pre-human-acceptance-report.json>` passes. Human validation must not start while any required automated gate is red, blocked, or pending.

## Tester and environment

- Tester:
- Date/time UTC:
- Candidate version/tag/commit:
- Device/OS/browser or app target:
- Assistive technology used:
- Text scaling / zoom level:
- Locale:

## Human tasks

1. Open the authenticated member home and confirm the product wording is understandable and does not make unsupported customer-ready claims.
2. Open a space/channel and verify chat, files, calendar, and board/task affordances are discoverable from the Weave-owned UI, not raw provider setup screens.
3. Keyboard-only path: navigate home, space tabs, chat composer, files/calendar entry points, settings, and sign out without a pointer device.
4. Screen-reader path: confirm headings, landmarks, focused controls, capability states, error states, and support wording are announced with useful labels.
5. Visual accessibility path: verify visible focus, non-color-only status cues, contrast-sensitive states, text scaling/zoom, and no clipped critical controls.
6. Admin/support wording path: confirm diagnostics remain support-safe and no private content or raw provider internals are exposed.

## Findings

| ID | Area | Severity (`blocker`, `major`, `minor`) | Description | Evidence pointer | Release blocker issue/PR |
| --- | --- | --- | --- | --- | --- |
| UXA-1 |  |  |  |  |  |

## Release blocker handling

- Any blocker finding must have a GitHub issue with `release-blocker` or be linked to an existing release blocker before final readiness can pass.
- Findings are triaged with blocker status before signoff.
- No raw provider diagnostics or private content enter reports.

## Signoff

Human signoff status: pending

- Signed by:
- Signed at UTC:
- Decision: `pending` / `blocked` / `signed_off`
- Scope statement: UX/accessibility validation covers only the candidate, environment, and evidence listed above.
