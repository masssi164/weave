# Sprint 29 final release readiness decision template

Issues: #654, #651, #652, #653. Human signoff status: pending.

Do not paste secrets, tokens, raw provider payloads, raw provider error bodies, or private user content into this report. The final decision must distinguish exact `release_ready` scopes from lower product-reality levels.

## Entry condition

Final readiness triage can start only after:

1. `python3 tools/sprint29_release_decision_guard.py entry --evidence <generated-pre-human-acceptance-report.json>` passes.
2. Human UX/accessibility validation is completed as signed approval or documented blocker findings.
3. Human Weaver validation is completed as signed approval or documented blocker findings.
4. GitHub release-blocker issues are checked for the candidate.

After filling this decision template from those inputs, run `python3 tools/sprint29_release_decision_guard.py final --evidence <signed-final-decision.json>`. That final guard passes only for a signed `release_ready` decision; pending or blocked outcomes remain valid triage results but must not be promoted as release-ready.

## Candidate

- Candidate version/tag/commit:
- Decision owner:
- Decision time UTC:
- Source automated acceptance report:
- UX/accessibility evidence:
- Weaver validation evidence:
- Release-blocker issue query:

## Scope classification

| Scope | Reality level | Evidence pointer | Release wording allowed? |
| --- | --- | --- | --- |
|  | `contract_only` / `configured` / `live_read` / `live_write` / `migration_dry_run` / `migration_apply_ready` / `rollback_ready` / `release_ready` |  |  |

## Release blocker handling

- v0.1 release-ready remains blocked unless all release blockers are closed.
- Any open blocker means the decision is `blocked`, not `release_ready`.
- Lower reality levels may be reported as progress but not as customer-ready release claims.

## Decision

Human signoff status: pending

- Decision: `pending` / `blocked` / `release_ready_for_named_scope`
- Open release blockers: not checked
- Support-safe review completed: no
- Signed by:
- Signed at UTC:
- Notes:
