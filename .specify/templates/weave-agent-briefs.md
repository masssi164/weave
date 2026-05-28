# Weave agent briefing templates

Use these compact templates for `weave-co-leader` orchestration. Do not paste transcripts. Do not ask specialists to infer product scope from memory.

## Invocation matrix

- Orchestrator: `weave-co-leader` native OpenClaw subagent/session.
- Repo specialists: native subagents with scoped prompts; logical role may be Product/Spec, Client/A11y, Server/Domain, Admin/Policy, Provider/Infra, QA/Evidence, Docs/Release, Security/Privacy, Integration Review.
- ACP harnesses: only when explicitly requested or a coding harness is the chosen runtime. Use allowed ACP harness IDs such as `codex`, `claude`, `gemini`, or `opencode`; report policy errors instead of falling back silently. Repo-local example: `weave-agent-team-config.example.json5`.
- Copilot review: currently fallback-only; do not block PR readiness on Copilot premium review exhaustion.
- Stop conditions: secrets, live infra mutation, data loss, history rewrite, hidden scope expansion, or unresolved product-core ambiguity.

## Professional optimization loop

Use the smallest pattern that solves the slice:

1. Recover truth from repo/GitHub/CI and identify the contract/spec.
2. Build a task DAG; parallelize only independent file/contract areas.
3. Prefer native subagents for repo specialists; use ACP only for explicit harness work.
4. Verify nested spawning is enabled (`agents.defaults.subagents.maxSpawnDepth >= 2`) before expecting a subagent co-leader to spawn workers.
5. Give each worker exact inputs, allowed files, required gates, and stop conditions.
6. Integrate from evidence, not transcript claims.
7. Run adversarial Integration-Gate review.
8. If review finds material improvement, create the next scoped brief and repeat.
9. Stop only when Integration-Gate returns no material optimization opportunity or a product-core clarification blocks safe progress.

Material optimization means a change that improves correctness, traceability, security/privacy, accessibility, supportability, deployability, CI/evidence quality, or reviewability without hidden scope expansion.

## Truth-Recovery

```text
You are weave-co-leader. Recover current truth from the Weave repo, GitHub issues/PRs, and CI/evidence.
Do not rely on chat memory except as orientation.
Return: branch, relevant docs/specs, open PRs/issues, latest CI/evidence, blockers, smallest safe next slice.
Do not modify files.
```

## Specialist-Brief

```text
Role: <Product/Spec | Client/A11y | Server/Domain | Admin/Policy | Provider/Infra | QA/Evidence | Docs/Release | Security/Privacy | Integration Review>
Goal: <one independently testable outcome>
Allowed files: <exact paths/globs>
Inputs: <spec/plan/docs/issues>
Required gate: <command>
Stop before: secrets, live infra mutation, data loss, hidden scope expansion, unresolved [NEEDS CLARIFICATION].
Return only: done/blocked, files changed, evidence command+result, risks, recommended next.
```

## ACP-Harness-Brief

```text
Runtime: named ACP profile <weave-codex-acp|weave-claude-acp|weave-gemini-acp|weave-opencode-acp> via OpenClaw sessions_spawn(runtime="acp", agentId="<profile-id>").
Goal: <one coding-harness-suitable outcome>
CWD: /Users/flotterotter/code/weave
Allowed scope: <exact files/globs>; do not touch live infra/secrets/generated artifacts.
Inputs: <spec/plan/tasks/docs paths>
Required gate: <command or explain why not runnable>
Policy: if the operator config intentionally uses direct ACP harness ids, use the configured id such as <codex|claude|gemini|opencode>; otherwise use the named Weave ACP profile. If the id is denied/unavailable, report the policy error. Do not silently switch to native subagent.
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
Act as adversarial but practical weave-co-leader reviewer.
Inputs: <diff/spec/tasks/evidence>
Score 0-3 each: correctness, traceability, spec protection, agent-team usability, ACP/runtime policy clarity, maintainability, evidence quality.
Find only material improvements; ignore taste-only rewrites.
Return:
- Verdict: no-material-optimizations | optimize-before-commit | blocked
- Scores:
- Findings, each with path, risk, exact suggested change, required gate
- If no findings: final evidence needed before commit/PR
```

## Sprint-Planning

```text
You are weave-co-leader. Plan a real Weave sprint from the governing spec.
Inputs: <spec.md/plan.md/tasks.md/docs/issues>.
Do first: recover truth from main, specs, GitHub issues/PRs, CI/evidence.
Output: sprint goal, issue DAG, PR train order, specialist roles, required gates, merge authorization status, product-core blockers.
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
For the next unblocked issue in the DAG, cut/update a short-lived branch from current main, implement the smallest reviewable slice, open a PR, set exactly one release-notes label, and run the required local gate.
After CI and Integration-Gate pass, merge only if the sprint brief authorizes autonomous merge; otherwise report merge-ready with the one missing decision.
After merge, fetch/fast-forward main before continuing dependent PRs.
Return: issue, branch, PR, gates, CI, merge status, next unblocked issue.
```

## Sprint-Closure

```text
Close the sprint only after all planned issues/PRs are merged or an explicit blocker is recorded.
Report exactly:
- Verdict
- Governing spec(s)
- Issue DAG with final state
- Merged PRs in order
- Gates and CI evidence
- Release notes / RC impact
- Product decisions made or still blocked
- Docs/spec updates
- Next safe action
A green unmerged PR is not a completed sprint.
```

## Session-Handoff

```text
Preserve durable state only:
- Spec/issue/PR IDs:
- Decisions made:
- Evidence/gates:
- Blockers/open questions:
- Next safe action:
Do not include discarded reasoning or logs.
```
