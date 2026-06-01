# Sprint 15 provider-switch accessibility evidence template

Status: Template/evidence checklist for Matrix Chat dry-run and Admin provider-switch consequence copy.

## Scope

Review the Admin provider-switch dry-run screen, consequence preview, blocked apply/cutover affordances, and member-visible capability preview. The review must prove consequences are understandable without color, without provider internals, and without keyboard traps.

## Stable vocabulary

Use only these member-impact states in member/admin copy: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`, `unsupported`.

## Manual AT checklist

- Keyboard can reach the dry-run trigger, consequence preview, audit refs, rollback limits, and disabled apply/cutover controls.
- Screen reader announces state text, not only icon/color/severity.
- Consequence copy distinguishes admin/operator evidence from member-visible disruption.
- Disabled apply/cutover controls explain the missing backend evidence or Sprint 15 dry-run-only blocker.
- Member preview does not expose Matrix URLs, `mxc://`, provider keys, tokens, raw diagnostics, homeserver details, or operator-only adapter internals.
- Copy states E2EE history migration as `unsupported`/`coming_later` until a client-side export/key strategy exists.

## Evidence recording

Record reviewer, date, browser/AT combination, tested route, pass/fail result, and any blocker issue. Do not attach screenshots or logs containing raw provider diagnostics.
