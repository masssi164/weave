# Sprint 18 manual AT candidate signoff checklist for #591

Status: **candidate-specific human execution package; not pass evidence until completed by a real reviewer**.
Issue: <https://github.com/masssi164/weave/issues/591>
Primary blocker artifact: `docs/evidence/accessibility/sprint-18-manual-at-blocker.md`
Release gate index: `docs/accessibility-release-gate.md#sprint-18-manual-assistive-technology-release-trust-gate`

This checklist is the non-human prerequisite package for Massimo / the release owner. It tells the human reviewer exactly what to run and where to record evidence. It does **not** close #591, waive #591, or replace real assistive-technology testing.

## 1. Candidate pinning

Use one exact candidate ref for the whole session. Prefer a release tag; otherwise use the protected `origin/main` commit under review.

```bash
git fetch origin --tags --prune
CANDIDATE_REF=${CANDIDATE_REF:-origin/main}
CANDIDATE_SHA=$(git rev-parse "$CANDIDATE_REF^{commit}")
printf 'candidate_ref=%s\ncandidate_sha=%s\n' "$CANDIDATE_REF" "$CANDIDATE_SHA"
```

Record both `candidate_ref` and `candidate_sha` in every evidence row and in the #591 comment.

Current package preparation ref when this checklist was added: `origin/main` at `ae596b654b24a7d207b363e99f03fa61b9387e39`.

## 2. Minimal preflight gates

Run only compact, support-safe checks before manual AT. These checks prove the candidate docs/release evidence still load; they are not manual AT pass evidence.

```bash
make docs-check
make release-evidence-check
```

Optional if the candidate is meant to be promoted beyond blocker review:

```bash
make release-notes-check
```

Record command, result, local timestamp, and any artifact path or CI URL. Do not paste raw provider diagnostics, tokens, Matrix URLs, member content, SecretRef values, or unsafe downstream error bodies into #591.

## 3. Manual AT matrix to execute

Complete every row with a real person, real device/browser, and real assistive technology. A row may be `pass` only when keyboard, screen-reader, text-scale/reflow, and support-safety checks all pass for the same candidate SHA.

| Flow ID | Role | Route / surface | Required AT coverage | Evidence path to fill |
| --- | --- | --- | --- | --- |
| `member-workspace-loop` | member | Weave Home -> channel workspace -> chat -> files -> calendar -> boards -> meeting/decision loop | VoiceOver or TalkBack; desktop keyboard + screen reader; 200% text scale/reflow | `docs/evidence/accessibility/sprint-18-manual-at-blocker.md`, replace the matching blocked row or link a new completed evidence file |
| `admin-migration-apply-recovery` | admin/operator | Admin provider replacement dry-run, member-impact preview, apply block, recovery, rollback, support boundary | desktop keyboard; screen reader; 200% zoom/text scale | same as above |
| `admin-go-live-claim-control` | admin/release owner | Admin RC go-live summary, release blockers, support-bundle refs, audit/export refs, release-notes source, CI/Live Stack evidence, next actions | desktop keyboard; screen reader; text-scale/reflow | same as above |
| `agent-runtime-control-admin-lifecycle` | owner/admin/operator | ARC entitlement and lifecycle actions, delete-state confirmation, audit correlation, disabled-by-policy states | desktop keyboard; screen reader; text-scale/reflow | same as above |

For each row, record:

- reviewer name or handle and reviewer role;
- date/time and timezone;
- `candidate_ref`, `candidate_sha`, build URL or artifact ID;
- OS, browser/device, assistive technology and version;
- route/surface tested and account role;
- keyboard result: `pass` or `fail`, plus support-safe note;
- screen-reader result: `pass` or `fail`, plus support-safe note;
- text-scale/reflow result: `pass` or `fail`, plus support-safe note;
- support-safety result: `pass` or `fail` for no raw provider diagnostics, Matrix URLs, tokens, member content, SecretRef values, or unsafe downstream errors;
- outcome: `pass`, `fail`, or `waived-by-release-owner`;
- linked GitHub blocker for every failed row.

## 4. Support-safe #591 comment template

Issue #591 is closed_not_planned and this file is only a historical template. Record a current human session against the current candidate and current release-owner issue; do not reopen or reuse #591 as pass evidence.

```markdown
Manual AT candidate signoff update for #591

Candidate:
- ref: <tag-or-origin/main>
- sha: <exact commit>
- build/artifact: <support-safe URL or identifier>

Preflight gates:
- `make docs-check`: <pass/fail>, evidence: <CI URL or local artifact path>
- `make release-evidence-check`: <pass/fail>, evidence: <CI URL or local artifact path>
- optional `make release-notes-check`: <pass/fail/not-run>, evidence: <CI URL or local artifact path>

Manual AT rows:
- `member-workspace-loop`: <pass/fail/waived>, reviewer: <name>, AT/browser/device: <support-safe summary>, blocker: <issue or none>
- `admin-migration-apply-recovery`: <pass/fail/waived>, reviewer: <name>, AT/browser/device: <support-safe summary>, blocker: <issue or none>
- `admin-go-live-claim-control`: <pass/fail/waived>, reviewer: <name>, AT/browser/device: <support-safe summary>, blocker: <issue or none>
- `agent-runtime-control-admin-lifecycle`: <pass/fail/waived>, reviewer: <name>, AT/browser/device: <support-safe summary>, blocker: <issue or none>

Release-owner decision:
- <accepted / blocked / scoped waiver>
- rollback note: <support-safe rollback or no-promotion note>
- claim boundary: no public/production accessibility signoff unless every required row is pass or explicitly waived by release owner with owner, scope, candidate SHA, expiry, and compensating evidence.
```

## 5. Closure boundary

Do not close #591 from this checklist alone. #591 can close only after completed support-safe evidence is recorded for all required rows, or after release ownership records an exceptional scoped waiver with owner, scope, exact candidate SHA/tag, expiry, linked blockers, compensating evidence, and rollback/no-promotion note.
