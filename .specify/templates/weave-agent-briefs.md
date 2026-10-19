# Weave assistant briefing templates

Use these compact templates for repo-safe AI-assisted delivery. Do not paste transcripts. Do not ask reviewers or implementers to infer product scope from memory. Live runtime configuration, allowlists, model routing, and personal operator paths are intentionally outside this repository.

## Invocation matrix

- Delivery lead: validates source-bound context, supplies bounded task briefs, integrates evidence, and enforces gates. Whole-sprint discovery and issue-DAG planning apply to explicitly assigned sprint work.
- Repo roles: scoped prompts with logical responsibilities such as Product/Spec, Architecture/Contract, Client/A11y, Server/Domain, Admin/Policy, Provider/Infra, QA/Evidence, Docs/Release, Security/Privacy, and Integration Review.
- Coding harnesses: use only when explicitly selected by the operator environment. The product repo must not name deployable live harness profiles or allowlists.
- Copilot review: follow `docs/gitflow-pr-workflow.md`; use the required independent fallback when unavailable or waived. Exhaustion does not waive required review.
- Stop conditions: secrets, live infra mutation, data loss, history rewrite, hidden scope expansion, or unresolved product-core ambiguity.

## Professional optimization loop

Apply the root `AGENTS.md` prepared-task rule through a finite acceptance loop:

1. Validate the prepared brief's implementation baseline, corpus pin, governing requirements and source paths. Recover only missing or changed material inputs. Full sprint discovery belongs to the delivery lead at sprint entry, not every worker.
2. Set one independently testable outcome, allowed files, required checks and completion boundary before implementation. Parallelize only independent owners; do not rebuild the whole sprint DAG for a leaf fix.
3. Inspect the affected code, implement the slice and run its smallest meaningful proof. Integrate from observed results and source, not transcript claims.
4. Review the actual diff for concrete acceptance violations, correctness, security, privacy, accessibility, supportability and required evidence. Fix blocking findings within scope; a materially new requirement returns to the lead instead of silently expanding the task.
5. Stop when the stated acceptance, required checks and review are satisfied. Optional improvements do not prevent completion and do not automatically create another task, specialist or review round.

A blocking finding identifies a violated applicable requirement, affected behavior and necessary correction. A general opportunity to improve quality, a rubric score, stylistic preference or a hypothetical future feature is not itself a blocker. Scope changes and all authorization and release gates remain explicit.

## Truth-Recovery

```text
Validate the prepared brief's implementation baseline, corpus pin, governing requirements, source paths and current consequential checks.
For unchanged verified inputs, reuse the brief; unstructured chat memory is not evidence.
Recover only missing or materially changed requirements from their owning source. Whole-sprint discovery is required only when explicitly assigned or materially invalidated.
Return: validated identities, relevant deltas, blockers and the exact next scoped action.
Do not modify files.
```

## Specialist-Brief

```text
Role: <Product/Spec | Architecture/Contract | Client/A11y | Server/Domain | Admin/Policy | Provider/Infra | QA/Evidence | Docs/Release | Security/Privacy | Integration Review>
Goal: <one independently testable outcome>
Allowed files: <exact paths/globs>
Baseline: <implementation commit/tree and corpus pin>
Inputs: <governing requirements and source paths; relevant issue/spec>
Settled decisions and invalidation triggers: <decisions; changed inputs requiring review>
Acceptance and completion boundary: <observable criteria; implementation, PR or authorized delivery>
Required checks and review: <commands and review requirements>
Stop before: secrets, live infra mutation, data loss, hidden scope expansion, unresolved [NEEDS CLARIFICATION].
Return only: done/blocked/partial at the assigned boundary, source identity, files changed, checks/review with results or pending status, blockers, next exact action. Stop at the assigned boundary.
```

## Coding-Harness-Brief

```text
Runtime: operator-selected coding harness.
Goal: <one coding-harness-suitable outcome>
CWD: <repo root supplied by operator environment>
Allowed scope: <exact files/globs>; do not touch live infra/secrets/generated artifacts.
Baseline: <implementation commit/tree and corpus pin>
Inputs: <governing requirements and source paths; relevant issue/spec>
Settled decisions and invalidation triggers: <decisions; changed inputs requiring review>
Acceptance and completion boundary: <observable criteria; implementation, PR or authorized delivery>
Required checks and review: <commands and review requirements; report unavailable prerequisites>
Policy: if the configured harness is denied/unavailable, report the policy error. Do not silently switch runtime.
Return only: status at the assigned boundary, source identity, files changed, checks/review with results or pending status, blockers, next exact action. Do not start follow-up implementation without authorization.
```

## Evidence-Return

```text
Return concise evidence only:
- Status: done | blocked | partial at the assigned completion boundary
- Implementation commit/tree and corpus pin:
- Required checks/review still pending:
- Files changed:
- Gates run:
- Evidence artifacts:
- Risks/blockers:
- Next safe action:
No transcript recap. No broad architecture essay.
```

## Integration-Gate

```text
Inspect the branch/PR against spec and constitution.
Check: linked issue/spec, release-notes label, acceptance mapping, docs impact, smallest local gate, CI status, review fallback, no unrelated files.
Evaluate concrete scope acceptance, correctness, risk and evidence. Every blocker must identify an applicable violated requirement and a necessary correction. Optional optimization opportunities do not change a passing completion verdict.
Return: acceptance satisfied? yes/no, merge-ready? yes/no, concrete blockers, exact-source check/review results, next exact action. Separate optional follow-ups; pending required CI or independent review prevents merge readiness.
```

## Optimization-Review

```text
Act as adversarial but practical integration reviewer.
Inputs: <diff/spec/tasks/evidence>
Optional scores (0-3): correctness, traceability, spec protection, handoff usability, runtime-policy hygiene, maintainability, evidence quality. Scores explain findings; they do not independently trigger implementation or another review round.
Find concrete violations of applicable requirements; ignore taste-only rewrites. Preserve required security, privacy, accessibility, correctness and evidence checks. Review the correction when a blocker is fixed; stop when acceptance and required checks/review are satisfied.
Return:
- Verdict: acceptance-satisfied | changes-required | blocked
- Blocking findings, each with requirement/source, path, affected behavior, necessary correction and required check
- Pending required evidence or review, if any
- Optional non-blocking follow-ups, without starting new work
```

## Sprint-Planning

```text
Plan a real Weave sprint from the governing spec.
Inputs: <spec.md/plan.md/tasks.md/docs/issues>.
Do first: recover truth from the pinned spec corpus, then current dev and main, repo conformance specs, GitHub issues/PRs, CI/evidence.
Output: sprint goal, issue DAG, PR train order, required roles, required gates, merge authorization status, product-core blockers.
If issues are missing and GitHub access is available, create/update them with dependency notes and parallel/sequential labels.
Do not implement yet.
```

## Issue-DAG

```text
Create or update GitHub issues for the sprint.
Each issue must include: spec id, user/admin/operator value, allowed scope, dependencies, acceptance/gate, release-notes expectation, and whether it is parallel or sequential.
Do not create vague umbrella tasks unless they link concrete implementation/review issues.
Return: issue numbers, dependency order, blockers.
```

## PR-Train

```text
Open or update PRs for the issue DAG.
Each PR must include: issue/spec link, summary, tests/evidence, release-notes label expectation, CI status, review fallback if needed, and next dependent PR.
Do not merge until branch protection, local gates, release-label policy, and Integration-Gate pass.
```

## Session-Handoff

```text
Handoff one current, bounded work item.
Include: implementation commit/tree, corpus pin, governing requirements and source paths, settled decisions, allowed files, acceptance and completion boundary, checks with exact-source results, unresolved blockers, and next exact command/action.
Separate current blockers from historical failures and already accepted fixes. Identify the changed inputs that invalidate each reusable conclusion. Revalidate consequential live permissions/checks at the action boundary.
Do not include raw diffs, transcripts, secrets, broad histories or an instruction to rediscover the whole project. A valid prepared handoff satisfies background truth recovery for unchanged inputs.
```
