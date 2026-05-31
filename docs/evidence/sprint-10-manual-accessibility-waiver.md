# Sprint 10 manual accessibility evidence waiver

Status: waiver pending live assistive-technology execution
Issue: #473
Owner: Sprint 10 release owner and client accessibility owner
Expiry: 2026-06-14 or before v0.1 RC promotion, whichever comes first
Scope: Sprint 10 release-closure manual accessibility rows for Admin Console provider apply/recovery surfaces and critical member/admin traversal that require real screen reader, keyboard-only, and text-scaling execution.

## Reason

Manual screen-reader and assistive-technology traversal cannot be truthfully produced from this local coding session. No live tester/device/browser combination was available in this scope, so release closure must not infer manual accessibility pass status from widget or Admin Console tests.

## Required execution before waiver expiry

Run and record the manual checks from `docs/accessibility-release-gate.md`, with Sprint 10 emphasis on:

- Admin Console provider selection/apply: keyboard-only traversal reaches category, adapter, dry-run, consequence confirmation, blocked-apply messaging, readiness test, and replacement dry-run evidence without pointer-only steps.
- Screen reader announces fresh vs stale dry-run evidence, missing gates, explicit consequence confirmation, member impact, rollback/support boundary, and apply blocked/enabled status as text.
- Text scaling/zoom to 200% keeps provider apply gates, consequence confirmation, evidence refs, and recovery copy readable and operable.
- Critical member flows from the baseline gate remain unchanged or have a recorded pass/blocker.

Record tester, date, platform/browser, assistive technology, result, evidence link, and any blocking issue. If any row fails, create/link a release-blocking issue and do not treat this waiver as a pass.

## Acceptance accounting

This waiver is an explicit release-risk artifact, not a substitute for pass evidence. It satisfies Sprint 10 closure only if the release owner accepts the temporary waiver and tracks execution before the expiry above.
