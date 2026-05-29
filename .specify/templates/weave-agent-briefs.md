# Weave assistant briefing templates

Use these compact templates for repo-safe AI-assisted delivery. Do not paste transcripts. Do not ask reviewers or implementers to infer product scope from memory. Live runtime configuration, allowlists, model routing, and personal operator paths are intentionally outside this repository.

## Invocation matrix

- Delivery lead: recovers repo/GitHub/CI truth, plans the issue DAG, integrates evidence, and enforces gates.
- Repo roles: scoped prompts with logical responsibilities such as Product/Spec, Architecture/Contract, Client/A11y, Server/Domain, Admin/Policy, Provider/Infra, QA/Evidence, Docs/Release, Security/Privacy, and Integration Review.
- Coding harnesses: use only when explicitly selected by the operator environment. The product repo must not name deployable live harness profiles or allowlists.
- Copilot review: fallback-only; do not block PR readiness on Copilot premium review exhaustion.
- Stop conditions: secrets, live infra mutation, data loss, history rewrite, hidden scope expansion, or unresolved product-core ambiguity.

## Professional optimization loop

Use the smallest pattern that solves the slice:

1. Recover truth from repo/GitHub/CI and identify the contract/spec.
2. Build a task DAG; parallelize only independent file/contract areas.
3. Give each worker exact inputs, allowed files, required gates, and stop conditions.
4. Integrate from evidence, not transcript claims.
5. Run adversarial Integration-Gate review.
6. If review finds material improvement, create the next scoped brief and repeat.
7. Stop only when Integration-Gate returns no material optimization opportunity or a product-core clarification blocks safe progress.

Material optimization means a change that improves correctness, traceability, security/privacy, accessibility, supportability, deployability, CI/evidence quality, or reviewability without hidden scope expansion.

## Truth-Recovery

```text
Recover current truth from the Weave repo, GitHub issues/PRs, and CI/evidence.
Do not rely on chat memory except as orientation.
Return: branch, relevant docs/specs, open PRs/issues, latest CI/evidence, blockers, smallest safe next slice.
Do not modify files.
```

## Specialist-Brief

```text
Role: <Product/Spec | Architecture/Contract | Client/A11y | Server/Domain | Admin/Policy | Provider/Infra | QA/Evidence | Docs/Release | Security/Privacy | Integration Review>
Goal: <one independently testable outcome>
Allowed files: <exact paths/globs>
Inputs: <spec/plan/docs/issues>
Required gate: <command>
Stop before: secrets, live infra mutation, data loss, hidden scope expansion, unresolved [NEEDS CLARIFICATION].
Return only: done/blocked, files changed, evidence command+result, risks, recommended next.
```

## Coding-Harness-Brief

```text
Runtime: operator-selected coding harness.
Goal: <one coding-harness-suitable outcome>
CWD: <repo root supplied by operator environment>
Allowed scope: <exact files/globs>; do not touch live infra/secrets/generated artifacts.
Inputs: <spec/plan/tasks/docs paths>
Required gate: <command or explain why not runnable>
Policy: if the configured harness is denied/unavailable, report the policy error. Do not silently switch runtime.
Return only: status, files changed, evidence, risks, follow-up patch suggestion.
```

## Evidence-Return

```text
Return concise evidence only:
- Status: done | blocked | partial
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
Evaluate: professionalism, problem-solving strategy, result quality, simplicity, risk posture, and evidence strength.
Return: merge-ready? yes/no, material optimization opportunities? yes/no, blockers, exact command/evidence, recommended next action.
```

## Optimization-Review

```text
Act as adversarial but practical integration reviewer.
Inputs: <diff/spec/tasks/evidence>
Score 0-3 each: correctness, traceability, spec protection, handoff usability, runtime-policy hygiene, maintainability, evidence quality.
Find only material improvements; ignore taste-only rewrites.
Return:
- Verdict: no-material-optimizations | optimize-before-commit | blocked
- Scores:
- Findings, each with path, risk, exact suggested change, required gate
- If no findings: final evidence needed before commit/PR
```

## Sprint-Planning

```text
Plan a real Weave sprint from the governing spec.
Inputs: <spec.md/plan.md/tasks.md/docs/issues>.
Do first: recover truth from main, specs, GitHub issues/PRs, CI/evidence.
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
Handoff current sprint/PR state.
Include only: branch/PR/issue URLs, changed files, gates run with result, CI/check status, unresolved blockers, next exact command/action.
Do not include raw diffs, transcripts, secrets, or broad narrative.
```
