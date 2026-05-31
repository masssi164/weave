# Sprint 13 Closure Report — Weaver RuntimeProfile, Chat Channel Projection, and Credential Broker

## Governing sources

- Sprint epic: [weave#519](https://github.com/masssi164/weave/issues/519)
- Product-line order: `docs/product-line-and-weaver-plan.md` — Weave provider-neutral suite first; admin/IDM/RBAC/readiness second; governed Weaver runtime later.
- Runtime/profile spec projection: `specs/0007-governed-weaver-runtime/` and [weave#528](https://github.com/masssi164/weave/pull/528)
- Weaver fork bootstrap epic: [weaver#1](https://github.com/masssi164/weaver/issues/1)

## Issue DAG final state

| Issue | Scope | Final state | Evidence |
| --- | --- | --- | --- |
| [weave#523](https://github.com/masssi164/weave/issues/523) | server RuntimeProfile loader/tool registry/audit seam | Closed | [weave#529](https://github.com/masssi164/weave/pull/529) |
| [weave#527](https://github.com/masssi164/weave/issues/527) | profile hash/provider/tool/credential audit records | Closed | [weave#529](https://github.com/masssi164/weave/pull/529) |
| [weave#526](https://github.com/masssi164/weave/issues/526) | per-user runtime containers and lifecycle | Closed | [weave#530](https://github.com/masssi164/weave/pull/530) |
| [weave#525](https://github.com/masssi164/weave/issues/525) | governed Mein Weaver member UX | Closed | [weave#532](https://github.com/masssi164/weave/pull/532) |
| [weave#524](https://github.com/masssi164/weave/issues/524) | admin Weaver distribution policy | Closed | [weave#533](https://github.com/masssi164/weave/pull/533) |
| [weaver#3](https://github.com/masssi164/weaver/issues/3) | stable `weave-chat` channel plugin seam | Closed | [weaver#7](https://github.com/masssi164/weaver/pull/7) |
| [weaver#4](https://github.com/masssi164/weaver/issues/4) | signed RuntimeProfile to internal OpenClaw config | Closed | [weaver#7](https://github.com/masssi164/weaver/pull/7) |
| [weaver#5](https://github.com/masssi164/weaver/issues/5) | member-mode raw config/dashboard/tool-surface lockdown | Closed | [weaver#8](https://github.com/masssi164/weaver/pull/8) |
| [weaver#6](https://github.com/masssi164/weaver/issues/6) | RuntimeProfile tool/MCP denies and audit exports | Closed | [weaver#8](https://github.com/masssi164/weaver/pull/8) |

## Merge train

1. [weave#528](https://github.com/masssi164/weave/pull/528) — `a654a99` — Sprint 13 RuntimeProfile projection mapping.
2. [weave#529](https://github.com/masssi164/weave/pull/529) — `d79275c` — server RuntimeProfile audit/tool enforcement.
3. [weaver#7](https://github.com/masssi164/weaver/pull/7) — stable Weaver RuntimeProfile and `weave-chat` seams.
4. [weave#530](https://github.com/masssi164/weave/pull/530) — `05d8f9d` — infra runtime lifecycle contracts.
5. [weave#532](https://github.com/masssi164/weave/pull/532) — `fbcb169` — governed Mein Weaver member UX.
6. [weaver#8](https://github.com/masssi164/weaver/pull/8) — member runtime lockdown/tool policy enforcement.
7. [weave#533](https://github.com/masssi164/weave/pull/533) — `aff97c8` — admin Weaver distribution policy.

A combined admin/infra PR [weave#531](https://github.com/masssi164/weave/pull/531) was closed unmerged after the issue-scoped train superseded it.

## Evidence and gates

- `./gradlew serverCi acceptanceContract releaseEvidenceCheck --console=plain` passed for [weave#529](https://github.com/masssi164/weave/pull/529).
- `./gradlew infraStatic docsCheck acceptanceContract --console=plain` passed for [weave#530](https://github.com/masssi164/weave/pull/530).
- `./gradlew clientCi acceptanceContract --console=plain` passed for [weave#532](https://github.com/masssi164/weave/pull/532).
- `./gradlew adminCi acceptanceContract --console=plain` passed for [weave#533](https://github.com/masssi164/weave/pull/533) after conflict resolution with current `main`.
- Weaver fork gates for [weaver#8](https://github.com/masssi164/weaver/pull/8): targeted vitest contracts, docs list/checks, link checks, `git diff --check`, and `pnpm build` passed.
- GitHub CI passed on merged PR heads through [weave#533](https://github.com/masssi164/weave/pull/533). Final `main` CI for `aff97c8` was started by the merge and must be green before closing [weave#519](https://github.com/masssi164/weave/issues/519).

## Release and support posture

- Member UX remains bounded to policy-approved Weaver controls and does not expose raw OpenClaw configuration, provider secrets, channel tokens, raw MCP setup, or unsafe dashboard controls.
- Admin Console owns Weaver distribution policy preview for chat provider routing, model aliases, tools, skills, MCP grants, RuntimeProfile revocation, and audit/change history.
- Infra artifacts define one active runtime context per user/trust boundary with separated state/workspace/agentDir, short-lived runtime tokens, restart/reload/rollback gates, and support-bundle redaction.
- Server audit records preserve RuntimeProfile hash, provider/tool decisions, credential refs, and deny decisions without raw secrets.

## Follow-up / next safe action

Massimo added a concrete test-model requirement after the core Sprint 13 merge train: use the existing local OpenClaw LM Studio config shape (`lmstudio/qwen/qwen3.5-9b`), but from Docker/containerized Weaver runtime call `https://lmstudio.home.internal`, to prove inbound `weave-chat` message → model response → outbound `weave-chat` reply plus approved/denied tool behavior where applicable.

That follow-up is tracked as [weaver#9](https://github.com/masssi164/weaver/issues/9). It is the next Weaver evidence slice and must remain support-safe: no checked-in raw API keys, tokens, provider-native channel config, or raw OpenClaw member configuration.

## Closure gate

- [x] All Sprint 13 implementation child issues in `masssi164/weave` are closed: #523, #524, #525, #526, #527.
- [x] Weaver fork setup/security/tool-policy issues are closed: #3, #4, #5, #6.
- [x] Issue-scoped PRs are merged in dependency order.
- [x] Follow-up LM Studio container round-trip evidence is captured as [weaver#9](https://github.com/masssi164/weaver/issues/9).
- [ ] Final `main` CI for `aff97c8` is green.
- [ ] Close [weave#519](https://github.com/masssi164/weave/issues/519) after this report lands on `origin/main` and final `main` CI is green.
