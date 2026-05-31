# Sprint 11 manual accessibility evidence template

Status: evidence template pending live assistive-technology execution
Issues: #480, #489
Owner: Sprint 11 release owner and client accessibility owner
Scope: Replacement evidence for the Sprint 10 manual accessibility waiver before v0.1 RC promotion.

This file is not pass evidence until every result cell is completed with a real tester, date, platform/browser, assistive technology, result, evidence link, and any linked release-blocking issue.

## Execution rules

- Test against the exact release-candidate commit or record the tested commit explicitly.
- Use real assistive technology, not automated widget tests as a substitute.
- Keep evidence support-safe: no tokens, cookies, raw provider payloads, private room/event IDs, usernames, display names, file names, provider URLs, or screenshots containing private data.
- If any row fails, create or link a release-blocking issue before RC promotion.
- Do not delete or supersede the Sprint 10 waiver until this template is completed and accepted by the release owner.

## Result matrix

| Flow | Platform/browser | Assistive technology | Tester/date | Required result | Actual result | Evidence link | Blocking issue |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Admin Console guided setup and provider category review | TBD | Keyboard-only + screen reader | TBD | Admin reaches identity, chat, files, documents, calendar, boards, calls, and Weaver readiness without pointer-only steps. | TBD | TBD | TBD |
| Provider apply and recovery gates | TBD | Keyboard-only + screen reader | TBD | Category, adapter, dry-run, consequence confirmation, readiness test, replacement evidence, blocked apply, and enabled apply state are reachable and announced. | TBD | TBD | TBD |
| Fresh/stale evidence messaging | TBD | Screen reader | TBD | Fresh vs stale dry-run evidence, missing gates, member impact, rollback/support boundary, and apply status are announced as text. | TBD | TBD | TBD |
| Admin Console 200% text scaling | TBD | Browser zoom/text scaling | TBD | Provider apply gates, consequence confirmation, evidence refs, and recovery copy remain readable and operable at 200%. | TBD | TBD | TBD |
| Member domain surfaces provider reality | TBD | Keyboard-only + screen reader | TBD | Files, Calendar, Boards, Calls, and Documents states are announced as available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later without raw provider diagnostics. | TBD | TBD | TBD |
| Baseline member critical traversal | TBD | Mobile screen reader or desktop screen reader | TBD | Sign-in, Home, chat, files, calendar, settings/recovery, and workspace capability states remain traversable or have linked blockers. | TBD | TBD | TBD |

## Acceptance accounting

Completion requires all rows to record `pass` or a linked release-blocking issue in `Actual result`, plus release-owner acceptance. Until then, #480 remains open and v0.1 RC promotion must not claim the Sprint 10 manual accessibility waiver has been replaced.
