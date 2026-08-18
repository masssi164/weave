# Sprint 18 manual assistive-technology release signoff history

Status: historical accounting artifact for issue #591, closed_not_planned on 2026-06-13.
Date recorded: 2026-06-01.
Recorder: OpenClaw release-trust subagent.
Candidate scope: Sprint 18 critical Admin, Member, and governed Weaver release-trust flows.

This file is **not pass evidence**. It is the support-safe historical accounting artifact from the Sprint 18 automation environment. Current release promotion still requires current accessibility evidence, an explicit release-owner scope decision, and green release gates for the candidate being promoted.

## Required manual evidence shape

Each replacement row must include reviewer, date, build or commit, route/surface tested, assistive technology plus browser/device combination, keyboard result, screen-reader result, text-scale or reflow result, support-safety result, outcome, and linked blocker for any failure.

## Sprint 18 critical flow accounting

| Flow ID | Critical route or surface | Required assistive-technology coverage | Historical result | Historical blocker | Claim boundary |
| --- | --- | --- | --- | --- | --- |
| `member-workspace-loop` | Member Weave Home -> channel workspace -> chat/files/calendar/boards/meeting/decision loop | VoiceOver or TalkBack, desktop keyboard/screen reader, and 200% text scale/reflow | `closed_not_planned`: no real manual AT session was available to the Sprint 18 subagent | #591 closed_not_planned | Do not claim current member workspace accessibility signoff without current candidate evidence. |
| `admin-migration-apply-recovery` | Admin provider replacement dry-run, member-impact preview, apply block, recovery/rollback/support boundary | Desktop keyboard, screen reader, and 200% zoom/text scale | `closed_not_planned`: no real manual AT session was available to the Sprint 18 subagent | #591 closed_not_planned | Do not claim production provider migration apply, lossless cutover, or completed admin AT signoff without current candidate evidence. |
| `admin-go-live-claim-control` | Admin RC go-live summary, release blockers, support-bundle refs, audit/export refs, claim-control next actions | Desktop keyboard, screen reader, and text-scale/reflow | `closed_not_planned`: no real manual AT session was available to the Sprint 18 subagent | #591 closed_not_planned | Do not claim RC/prod readiness while CI, Live Stack, accessibility, support bundle, audit/export, migration, or Weaver evidence is missing/stale. |
| `agent-runtime-control-admin-lifecycle` | Current ARC entitlement, provision/start/stop/suspend/reconcile/revoke/delete-state confirmation, audit correlation, and disabled-by-policy states; the former member approval inbox is removed | Desktop keyboard, screen reader, and text-scale/reflow | `closed_not_planned`: no real manual AT session was available to the Sprint 18 subagent | #591 closed_not_planned | Do not claim broad Weaver availability, autonomous team writes, raw provider access, or completed ARC admin lifecycle AT signoff without current candidate evidence. |

## Support-safety review

This blocker artifact intentionally contains no screenshots, raw logs, endpoint URLs, tokens, provider diagnostics, SecretRef values, member content, raw Weaver runtime configuration, or downstream error payloads. It is suitable to cite from release notes, claim matrices, and PR evidence as a blocker only.

## Replacement checklist

Use the candidate execution package in [`sprint-18-manual-at-candidate-signoff-checklist.md`](sprint-18-manual-at-candidate-signoff-checklist.md) as a historical template only. Current release evidence should be recorded against the current milestone, candidate, and release-owner decision.

Before promoting a current release candidate, record current manual evidence or a release-owner-approved exceptional waiver that names:

- owner and reviewer;
- exact candidate commit/tag or build;
- assistive technology, browser/device, and OS versions;
- tested route and role;
- pass/fail result for keyboard, screen reader, text scale/reflow, and support-safety;
- linked GitHub blocker for any failed row;
- expiry and compensating evidence for any exceptional waiver.
