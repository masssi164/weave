# Weave agent-team orchestration

Status: active delivery contract, 2026-05-28.

This page defines the professional multi-agent delivery model for Weave. It translates the NotebookLM deep-research result into repo-local, reviewable rules. The research emphasized four recurring practices: keep a lead/orchestrator separate from implementors, use isolated/scoped worker contexts, verify with adversarial reviewers, and make quality gates executable. Relevant source titles include "The Code Agent Orchestra - what makes multi-agent coding work", "Best practices for Claude Code", "Orchestrate teams of Claude Code sessions", "Multi-agent coordination patterns: Five approaches and when to use them", "Agents - OpenCode", "Permissions - OpenCode", and the OpenClaw ACP/subagent docs.

## Operating principle

`weave-co-leader` owns sprint decomposition, issue/PR orchestration, merge sequencing, integration, and evidence. It does **not** become a mega-coder and it must not declare a sprint complete just because one PR is green.

Use this sprint loop until the issue DAG is integrated or a product-core blocker stops safe progress:

1. Recover truth from `main`, repo specs/docs/tasks, GitHub issues/PRs, and CI/evidence.
2. Identify the governing spec and decide whether it is ready for implementation. Keep unresolved product-core questions in `draft`/`proposed`.
3. Convert the spec plan/tasks into a GitHub issue DAG. Label independent work `parallel`; label ordered work `sequential`; link issues/PRs back to the spec.
4. Cut short-lived branches from updated `main` and open reviewable PRs in dependency order.
5. Spawn scoped specialists only for narrow issue slices, each with exact files, inputs, stop conditions, and a required gate.
6. Integrate only from returned evidence and inspected diffs, then run an adversarial Integration-Gate review.
7. Merge dependency-free PRs once CI/gates/review evidence pass and the sprint brief authorizes merge; otherwise surface the exact missing approval.
8. After every merge, update `main`, re-evaluate the remaining DAG, and continue the next slice.
9. Stop only when all sprint issues/PRs are merged or when a product-core clarification, live-infra/destructive approval, or failed gate blocks safe progress.
10. Produce the sprint closure report from repo/GitHub/CI evidence.

Material optimization means improved correctness, traceability, security/privacy, accessibility, supportability, deployability, CI/evidence quality, or reviewability without hidden scope expansion.

## Sprint management responsibilities

The co-leader keeps durable sprint state in GitHub and repo files, not chat transcripts:

- Specification stewarding lives in `specs/<id>/spec.md`, `plan.md`, `tasks.md`, and traceability files.
- Work tracking lives in GitHub issues with dependency notes and `parallel`/`sequential` labels.
- Implementation state lives in PRs and CI artifacts.
- Release impact lives in exactly one release-notes label per PR.
- Sprint closure lives in a checked-in report or PR comment that cites merged PRs, gates, and remaining decisions.

If the sprint brief authorizes autonomous merge, `weave-co-leader` may merge green, reviewed PRs in DAG order. If not, it reports merge-ready PRs and asks for the one missing merge decision.

## Recommended roles

Use native OpenClaw subagents for these repo-aware specialists by default:

- `weave-product-spec`: product-core wording, lifecycle status, frontmatter, non-goals, clarification markers, issue slicing.
- `weave-architecture-contract`: provider-neutral domain boundaries, contract drift, spec/plan architecture consistency.
- `weave-client-ui`: Flutter member/admin UX implementation, navigation, state handling, widget tests.
- `weave-client-a11y`: accessibility semantics, keyboard/screen-reader behavior, l10n, deterministic screenshots.
- `weave-admin-console`: Admin Console, workspace health, IDM/RBAC, policy previews, readiness, whitelisting.
- `weave-server-domain`: domain facades, authorization, audit, provider boundaries, backend contract tests.
- `weave-server-auth-audit`: identity, authz, audit ordering, support-safe audit payloads.
- `weave-provider-adapters`: provider-neutral adapter facades and canonical model mapping.
- `weave-provider-infra`: OpenTofu, adapters, runner/environment posture, backup/restore, support bundles.
- `weave-e2e-acceptance`: Gherkin, live-stack acceptance flow coverage, scenario intent.
- `weave-qa-evidence`: `scenario_mappings.json`, sanitized artifacts, CI/evidence completeness.
- `weave-docs-release`: docs navigation, handbooks, release notes, PR templates, closure reports.
- `weave-security-privacy`: secrets, raw provider payloads, audit, support-safe diagnostics, external-provider risk.
- `weave-devex-ci`: Gradle tasks, CI workflows, dependency/tooling checks, evidence upload.
- `weave-integration-reviewer`: final diff/spec/PR readiness, evidence strength, scope creep, release-label and CI posture.

Use ACP harnesses only when explicitly requested or intentionally selected for a coding-harness run:

- `codex`: explicit Codex ACP work, especially code-edit loops where ACP behavior is desired.
- `claude`: explicit Claude Code ACP work.
- `gemini`: explicit Gemini CLI ACP work or broad alternative review.
- `opencode`: explicit OpenCode ACP work and OpenCode-style agent-team experiments.

Do not silently fall back across runtimes. If OpenClaw policy denies an ACP agent id, report that policy error and stop.

## OpenClaw configuration shape

The repo does not own live OpenClaw config. The example below documents the expected shape for an operator-managed `~/.openclaw/openclaw.json` or equivalent Gateway config.

Repo-local example: `.specify/templates/weave-agent-team-config.example.json5`.

Important OpenClaw fields used by the example:

- `acp.enabled`, `acp.backend`, `acp.defaultAgent`, `acp.allowedAgents`, `acp.maxConcurrentSessions`, `acp.runtime.ttlMinutes`.
- `agents.defaults.subagents.maxSpawnDepth = 2` so `main -> weave-co-leader -> scoped worker` is allowed.
- `agents.defaults.subagents.maxChildrenPerAgent`, `agents.defaults.subagents.maxConcurrent`, and `agents.defaults.subagents.runTimeoutSeconds` to bound fan-out and stuck work.
- `agents.list[].subagents.requireAgentId` and `agents.list[].subagents.allowAgents` for the `weave-co-leader` native subagent policy.
- `agents.list[].runtime.type = "acp"` and `agents.list[].runtime.acp.agent/backend/mode/cwd` for named ACP profiles.

Minimal excerpt:

```json5
{
  acp: {
    enabled: true,
    backend: "acpx",
    defaultAgent: "codex",
    allowedAgents: ["codex", "claude", "gemini", "opencode"],
    maxConcurrentSessions: 4,
    runtime: { ttlMinutes: 120 },
  },
  agents: {
    defaults: {
      subagents: {
        maxSpawnDepth: 2,
        maxChildrenPerAgent: 5,
        maxConcurrent: 8,
        runTimeoutSeconds: 900,
      },
    },
    list: [
      {
        id: "weave-co-leader",
        workspace: "/Users/flotterotter/code/weave",
        subagents: {
          requireAgentId: true,
          allowAgents: [
            "weave-product-spec",
            "weave-client-a11y",
            "weave-server-domain",
            "weave-admin-policy",
            "weave-provider-infra",
            "weave-qa-evidence",
            "weave-docs-release",
            "weave-security-privacy",
            "weave-integration-reviewer",
            "weave-codex-acp",
            "weave-claude-acp",
            "weave-gemini-acp",
            "weave-opencode-acp",
          ],
        },
      },
      {
        id: "weave-codex-acp",
        runtime: {
          type: "acp",
          acp: {
            agent: "codex",
            backend: "acpx",
            mode: "persistent",
            cwd: "/Users/flotterotter/code/weave",
          },
        },
      },
    ],
  },
}
```

Without `maxSpawnDepth: 2`, a `weave-co-leader` that was itself spawned as a subagent becomes a leaf and cannot spawn specialists. That is acceptable for simple reviews but not for the team-lead/orchestrator pattern.

The co-leader allowlist must include the named ACP profile ids if it is expected to spawn them with `sessions_spawn(runtime="acp", agentId="weave-codex-acp")` or equivalent. Operators may instead allow direct ACP harness ids such as `codex` or `claude`, but the policy and brief must use the same ids.

Before changing live config, inspect the Gateway schema for the exact path and apply config through Gateway config tools, not manual edits.

## Brief and handoff contracts

Use `.specify/templates/weave-agent-briefs.md` for the stable templates:

- Truth-Recovery
- Specialist-Brief
- ACP-Harness-Brief
- Evidence-Return
- Integration-Gate
- Optimization-Review
- Session-Handoff

A worker prompt must include:

- role and one independently testable goal;
- exact allowed files or globs;
- governing spec/plan/tasks/docs paths;
- required gate or reason the gate cannot run;
- stop conditions;
- evidence-only return format.

Do not paste transcripts, broad sprint histories, or hidden product assumptions into workers.

## Professional evaluation rubric

`weave-integration-reviewer` or `weave-co-leader` scores 0-3:

- correctness against spec and acceptance;
- traceability from issue/spec to evidence;
- product-core protection and explicit unresolved questions;
- agent-team usability of briefs/config/docs;
- ACP/runtime policy clarity;
- maintainability and simplicity;
- evidence quality and reproducibility.

A score below 2 in any release-blocking dimension requires another implementation loop or an explicit blocker.

## Guardrails

Stop before:

- secrets or raw provider payload exposure;
- live infra mutation without approval;
- data loss or destructive cleanup;
- history rewrite;
- hidden scope expansion;
- accepted/implementing/implemented specs that still contain `[NEEDS CLARIFICATION: ...]`;
- product-core choices that Massimo/team have not decided.

When blocked by product-core ambiguity, keep the spec `draft` or `proposed`, write the clarification marker, and ask for the one decision needed to proceed.
