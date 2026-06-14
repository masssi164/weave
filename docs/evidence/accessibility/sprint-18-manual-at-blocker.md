# Sprint 18 manual assistive-technology release signoff blocker

Status: release-blocking evidence gap for issue #591.
Date recorded: 2026-06-01.
Recorder: OpenClaw release-trust subagent.
Candidate scope: Sprint 18 critical Admin, Member, and governed Weaver release-trust flows.

This file is **not pass evidence**. It is the support-safe blocker/waiver accounting artifact required when real manual assistive-technology execution cannot be collected in the repository automation environment. Replace each `blocked` row with real tester evidence before any RC or production accessibility signoff claim.

## Required manual evidence shape

Each replacement row must include reviewer, date, build or commit, route/surface tested, assistive technology plus browser/device combination, keyboard result, screen-reader result, text-scale or reflow result, support-safety result, outcome, and linked blocker for any failure.

## Sprint 18 critical flow accounting

| Flow ID | Critical route or surface | Required assistive-technology coverage | Current result | Blocker / expiry | Claim boundary |
| --- | --- | --- | --- | --- | --- |
| `member-workspace-loop` | Member Weave Home -> channel workspace -> chat/files/calendar/boards/meeting/decision loop | VoiceOver or TalkBack, desktop keyboard/screen reader, and 200% text scale/reflow | `blocked`: no real manual AT session was available to this subagent | #762 successor / #591 predecessor; expires before any Sprint 18 RC promotion | Do not claim Sprint 18 member workspace accessibility signoff. Automated tests may only be cited as supporting evidence. |
| `admin-migration-apply-recovery` | Admin provider replacement dry-run, member-impact preview, apply block, recovery/rollback/support boundary | Desktop keyboard, screen reader, and 200% zoom/text scale | `blocked`: no real manual AT session was available to this subagent | #762 successor / #591 predecessor; expires before any Sprint 18 RC promotion | Do not claim production provider migration apply, lossless cutover, or completed admin AT signoff. |
| `admin-go-live-claim-control` | Admin RC go-live summary, release blockers, support-bundle refs, audit/export refs, claim-control next actions | Desktop keyboard, screen reader, and text-scale/reflow | `blocked`: no real manual AT session was available to this subagent | #762 successor / #591 predecessor; expires before any Sprint 18 RC promotion | Do not claim RC/prod readiness while CI, Live Stack, accessibility, support bundle, audit/export, or blocker evidence is missing/stale. |
| `governed-weaver-approval-revocation` | Governed Weaver approval, denial, revocation, receipt, audit, and disabled-by-policy states | Desktop keyboard, screen reader, and text-scale/reflow | `blocked`: no real manual AT session was available to this subagent | #762 successor / #591 predecessor; expires before any Sprint 18 RC promotion | Do not claim broad Weaver availability, autonomous team writes, raw provider access, or completed governed Weaver AT signoff. |

## Support-safety review

This blocker artifact intentionally contains no screenshots, raw logs, endpoint URLs, tokens, provider diagnostics, SecretRef values, member content, raw Weaver runtime configuration, or downstream error payloads. It is suitable to cite from release notes, claim matrices, and PR evidence as a blocker only.

## Replacement checklist

Use the candidate execution package in [`sprint-18-manual-at-candidate-signoff-checklist.md`](sprint-18-manual-at-candidate-signoff-checklist.md) before asking Massimo / the release owner for #591 signoff. The checklist gives the exact candidate-pinning commands, compact preflight gates, manual AT rows, evidence fields, and support-safe #591 comment template.

Before closing #591 or promoting a Sprint 18 candidate, replace this blocker with real manual evidence or a release-owner-approved exceptional waiver that names:

- owner and reviewer;
- exact candidate commit/tag or build;
- assistive technology, browser/device, and OS versions;
- tested route and role;
- pass/fail result for keyboard, screen reader, text scale/reflow, and support-safety;
- linked GitHub blocker for any failed row;
- expiry and compensating evidence for any exceptional waiver.
