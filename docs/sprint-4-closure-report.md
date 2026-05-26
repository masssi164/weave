# Sprint 4 closure report: accessible work rooms and governed Weaver scout

Status: closure PR evidence, 2026-05-26.

## Closure scope

Sprint 4 closes the transition from Sprint 3's provider-neutral control plane into a user-facing work-room experience. The closed scope is deliberately product-first: a normal member can start from Weave Home, enter channel work rooms, read and create decision evidence, see Meeting Capsule and Weaver Scout contracts, and rely on accessibility/evidence gates without being pushed into provider setup or preview UX.

## Final closure status

- Sprint 4 umbrella: #323, ready to close with this closure report after the final PR merge.
- Operating model and DevOps setup: PR #338 merged after branch-protection-required checks passed on `main`.
- Branch model: protected `main`, short-lived PR branches, GitHub Environments for testing/staging, and one release-notes label per PR.
- Current open Sprint 4 implementation gap: none found in the audited acceptance graph. Remaining broad/post-MVP issues are explicitly out of this closure scope unless they are linked below as closed by the Sprint 4 evidence graph.

## Issue and PR graph

| Area | Closing evidence | State |
| --- | --- | --- |
| Weave Home: DMs, favorites, channels, AI chats | PR #215 added the Home overview sections; current `client/test/features/chat/chat_overview_test.dart` and `client/test/features/chat/chat_screen_test.dart` verify grouping, empty states, hidden preview/provider setup surfaces, and accessibility tap-target guidelines. | Closes #325 and older umbrella #210. |
| Channel Work Rooms | PR #228 introduced accessible channel workspace tabs; PR #333 promoted Decisions and Weaver Scout into first-class work-room tabs with product-safe copy and scenario mapping. | Closes #211. |
| Decision Ledger | PR #336 added channel Decision Ledger records with lifecycle/status, author/time/source references, localized UI, and widget/provider tests. PR #337 added backend create/read contracts and audit evidence. | Closes #251 and #219. |
| Meeting Capsule | PR #337 added Meeting Capsule backend facades tied to channel context, agenda/follow-up fields, and fail-closed media controls without provider credential leakage or Matrix E2EE/media conflation. | Closes #249. |
| Governed Weaver Scout | PR #333 added the read-only/proposal-first Weaver Scout tab. PR #337 added allowed-context summaries, citable sources, blocked write receipts, and approval-receipt contract responses. | Closes #252 and #214. |
| No-preview/member-provider boundary | PR #333 and PR #337 kept channel/member paths product-safe; PR #338 froze the operating model and review gates. The release copy tests and scenario mappings cover hidden preview/provider setup language. | Closes #259. |
| Accessibility and ISO-style dogfood evidence | PR #334 added the Sprint 4 dogfood accessibility evidence gate; current `clientCi` includes widget/a11y tests, release copy contracts, and offline live-stack contract checks. | Closes #253 and #257. |
| DevOps/release discipline | PR #335 runs the release label gate on every PR update; PR #338 documents the operating model, branch rules, release policy, and agent templates. Issues #288, #292, and #331 are already closed. | Closed before this report. |

## Frozen member-facing contract

Sprint 4 freezes these user-facing rules for v0.1 dogfood:

- Weave Home groups work by intent: Favorites, Personal messages, Channels, and AI chats.
- Channel Work Rooms expose channel chat plus first-class work objects such as decisions, meetings, boards/tasks, files/calendar affordances, and governed Weaver context where enabled.
- Normal members do not configure raw providers, service endpoints, provider secrets, provider readiness diagnostics, or roadmap/preview surfaces.
- Capability states are ready, disabled, degraded, policy-blocked, or admin setup required; raw downstream provider detail stays in admin/operator surfaces.
- Weaver is read-only/proposal-first in Sprint 4. Any future write path requires explicit policy, approval, receipt, audit, and consent evidence.
- Meeting media readiness is separate from Matrix chat E2EE; the UI and backend contracts must not conflate those guarantees.

## Final evidence snapshot

| Evidence | Result |
| --- | --- |
| Operating-model PR | #338 merged with `release-notes-feature`; post-merge `main` CI run `26450589364` succeeded. |
| Current branch local aggregate | `WEAVE_DOCS_VENV=build/docs-venv ./gradlew acceptanceContract releaseEvidenceCheck docsCheck` succeeded before this report. |
| Current branch client aggregate | `WEAVE_DOCS_VENV=build/docs-venv ./gradlew clientCi` succeeded in `1m 5s`; offline contract mode passed 5 checks and skipped live credential tests as expected. |
| Weave Home executable coverage | `chat_overview_test.dart` and `chat_screen_test.dart` verify Favorites, Personal messages, Channels, AI chats, empty states, no old preview/provider setup copy, and tap-target accessibility. |
| Scenario mapping | `e2e/scenario_mappings.json` maps the Home daily loop, channel workspace, Decision Ledger, Meeting Capsule, Weaver Scout, and release/operator paths to executable or documented evidence markers. |
| Release discipline | Open PR list is empty; branch protection required checks were satisfied for #338; release-label gate is now part of every PR update. |

## Local gates used during closure

```bash
python3 -m venv build/docs-venv
./build/docs-venv/bin/python -m pip install -q -r docs/requirements.txt
WEAVE_DOCS_VENV=build/docs-venv ./gradlew acceptanceContract releaseEvidenceCheck docsCheck
WEAVE_DOCS_VENV=build/docs-venv ./gradlew clientCi
```

## Residual risks and non-goals

- Live Stack E2E with real credentials was not rerun in this closure branch; `clientCi` intentionally ran offline contract checks and skipped live credential tests.
- Sprint 4 does not publish a GitHub release by itself.
- Sprint 4 does not claim broad provider marketplaces, Teams/Slack migration tooling, media recording/transcription/caption support, autonomous Weaver writes, or generic provider swaps.
- Issue #51 remains a broad/post-MVP UX backlog item and is not a blocker for closing the Sprint 4 milestone.
- Older PRs merged before the release-label gate may not have the modern exact-one-label discipline retroactively; the discipline is enforced for current/future PRs by #335 and documented by #338.
