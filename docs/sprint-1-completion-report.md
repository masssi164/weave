# Sprint 1 completion report: provider-neutral facade contracts

Date: 2026-05-25

## Goal

Complete the provider-neutral domain facade contract foundation so Weave can treat Chat, Files/Documents, Calendar/Meetings, Boards/Tasks, Identity/Admin, Readiness, and Audit as governed product contracts instead of fixed-provider implementation details.

## Slices completed

1. **Chat domain facade contracts** — message, room, membership, receipt, attachment, and audit contract surfaces for provider-neutral chat.
2. **Non-Chat domain facade contracts** — Files/Documents, Calendar/Meetings, Boards/Tasks, Identity/Admin, Readiness, and Audit contract surfaces aligned with the product-line plan.
3. **Backlog and closed-worktree reconciliation** — closed superseded/debt issues so Sprint 2 starts from current repo/GitHub truth, not stale worktrees.

## PRs and commits

| PR | Merge commit | Head branch | Result |
| --- | --- | --- | --- |
| [#302](https://github.com/masssi164/weave/pull/302) `feat(server): add Chat domain facade contracts` | `5accc2454c833aace175fe9b5ef5c6da48072576` | `sprint/chat-domain-facade` at `9df1189e65376464bfc6448185bf60c6adf285b3` | Merged 2026-05-25 09:37 UTC. |
| [#303](https://github.com/masssi164/weave/pull/303) `feat(server): add non-chat domain facade contracts` | `2a15dd1819a6097f9d878a206fb03ae8153f3088` | `feat/domain-facade-contracts-slice-2` at `f2a257dc3005a083e12b5f148b8862f93d62fbc0` | Merged 2026-05-25 10:33 UTC. |

## Issues closed or updated

| Issue | Outcome |
| --- | --- |
| [#295](https://github.com/masssi164/weave/issues/295) | Closed by PR #302. |
| [#282](https://github.com/masssi164/weave/issues/282) | Closed by PR #303. |
| [#299](https://github.com/masssi164/weave/issues/299) | Closed after backlog triage. |
| [#298](https://github.com/masssi164/weave/issues/298) | Closed after closed-worktree reconciliation. |
| [#284](https://github.com/masssi164/weave/issues/284) | Closed as superseded/covered by PRs #301/#302. |

## Docs updated

- Server/provider facade contract documentation and generated contract references were updated with the merged Chat and non-Chat surfaces.
- Product-line ordering remains anchored in `docs/product-line-and-weaver-plan.md`: Weave provider-neutral organization suite first; admin portal/IDM/RBAC/readiness/whitelisting second; Weaver governed per-user PA runtime later.
- Sprint 2 now starts from root build/evidence/delivery documentation instead of reopening Sprint 1 contract questions.

## Gates

- PR #302 passed Acceptance Contract, Admin Console Checks, Client Offline Checks, Docs Checks, Infra Static Checks, Release Notes Label Check, and Server Checks before merge.
- PR #303 passed the same full gate set before merge. A later merge-event run produced skipped jobs plus the release-label result; those skipped merge-event jobs are not a PR gate failure.
- Copilot review/check signal was present for PR #303.

## Non-collected evidence

- No live-stack E2E was collected. Sprint 1 contract work did not require operator confirmation, live services, secrets, or budget-consuming infrastructure.
- No screenshots or manual UX accessibility captures were collected because the delivered scope was backend contract/documentation oriented.
- No raw provider payloads, credentials, live URLs, or provider error bodies were stored as evidence.

## Risks carried into Sprint 2

- Build orchestration is still transitioning: Make and GitHub Actions have not yet fully converged on root Gradle as the delivery SSOT.
- Evidence is still partly reconstructed from workflow/check state instead of being emitted as a sanitized build artifact.
- Docs and release-note generation exist, but deterministic Gradle-owned output locations and explicit update-vs-check boundaries need hardening.
- Live E2E must remain opt-in with existing confirmation semantics while acceptance mappings stay executable and non-decorative.
