# Sprint 29 human Weaver validation template

Issues: #653, #654. Human signoff status: pending.

Do not paste secrets, tokens, raw provider payloads, raw provider error bodies, or private user content into this report. Describe unsafe behavior by category and link sanitized evidence only.

## Entry condition

Run this only after `python3 tools/sprint29_release_decision_guard.py entry --evidence <generated-pre-human-acceptance-report.json>` passes and the UX/accessibility validation has no untriaged blockers.

## Tester and environment

- Tester:
- Date/time UTC:
- Candidate version/tag/commit:
- Runtime profile / policy fixture:
- User role/group used:
- Device/OS/browser or app target:
- Assistive technology used, if any:

## Human tasks

1. Confirm Weaver is presented as governed, optional, and disabled unless explicitly enabled by organization policy and user opt-in.
2. Enable/opt in using the candidate flow and verify the user understands what rights and organization-whitelisted capabilities mean.
3. Review personalization/customization controls and confirm the profile/version/reset language is understandable.
4. Trigger an approval prompt and confirm the action, risk, target, and deny path are clear before approval.
5. Attempt or inspect an unsafe/unapproved capability path and confirm it is blocked, filed as a release blocker, or explicitly scoped out by evidence.
6. Deactivate/revoke Weaver and verify the UI explains what stops, what audit/evidence remains, and how reactivation would work.
7. Confirm audit explanations are understandable without revealing private content, raw tool payloads, secrets, or provider diagnostics.

## Findings

| ID | Area | Severity (`blocker`, `major`, `minor`) | Description | Evidence pointer | Release blocker issue/PR |
| --- | --- | --- | --- | --- | --- |
| WV-1 |  |  |  |  |  |

## Release blocker handling

- Unsafe tool behavior is a release blocker unless it is proven unreachable in the candidate scope.
- Human testers must confirm understandable control points before signoff.
- No raw provider diagnostics or private content enter reports.

## Signoff

Human signoff status: pending

- Signed by:
- Signed at UTC:
- Decision: `pending` / `blocked` / `signed_off`
- Scope statement: Weaver validation covers only the candidate, runtime policy/profile, user role, and evidence listed above.
