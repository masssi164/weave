# Weave agent-team orchestration

Status: active delivery contract, 2026-05-28.

This page defines the professional multi-agent delivery model for Weave. It translates the NotebookLM deep-research result into repo-local, reviewable rules. The research emphasized four recurring practices: keep a lead/orchestrator separate from implementors, use isolated/scoped worker contexts, verify with adversarial reviewers, and make quality gates executable. Relevant source titles include "The Code Agent Orchestra - what makes multi-agent coding work", "Best practices for Claude Code", "Orchestrate teams of Claude Code sessions", "Multi-agent coordination patterns: Five approaches and when to use them", "Agents - OpenCode", "Permissions - OpenCode", and the OpenClaw ACP/subagent docs.

## Operating principle

`weave-co-leader` owns decomposition, integration, and evidence. It does **not** become a mega-coder.

Use this loop until no material optimization remains:

1. Recover truth from this repo, GitHub issues/PRs, and CI/evidence.
2. Identify the governing issue/spec/acceptance contract.
3. Build a small task DAG; parallelize only independent file/contract areas.
4. Spawn a scoped specialist with exact files, inputs, stop conditions, and a required gate.
5. Integrate only from returned evidence and inspected diffs.
6. Run an adversarial Integration-Gate review.
7. Repeat implementation/review for material findings.
8. Stop only when the reviewer reports no material optimization opportunity or a product-core clarification blocks safe progress.

Material optimization means improved correctness, traceability, security/privacy, accessibility, supportability, deployability, CI/evidence quality, or reviewability without hidden scope expansion.

## Recommended roles

Use native OpenClaw subagents for these repo-aware specialists by default:

- `weave-product-spec`: product-core wording, lifecycle status, frontmatter, non-goals, clarification markers.
- `weave-client-a11y`: Flutter member/admin UX, accessibility semantics, keyboard/screen-reader behavior, l10n, widget tests.
- `weave-server-domain`: domain facades, authorization, audit, provider boundaries, backend contract tests.
- `weave-admin-policy`: Admin Console, workspace health, IDM/RBAC, policy previews, readiness, whitelisting.
- `weave-provider-infra`: OpenTofu, adapters, runner/environment posture, backup/restore, support bundles.
- `weave-qa-evidence`: Gherkin, `scenario_mappings.json`, Live Stack evidence, sanitized artifacts.
- `weave-docs-release`: docs navigation, handbooks, release notes, PR templates, closure reports.
- `weave-security-privacy`: secrets, raw provider payloads, audit, support-safe diagnostics, external-provider risk.
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
