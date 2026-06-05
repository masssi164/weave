# Sprint 30 closure report — Hot Phase Agentic Dogfood Readiness

## Scope

Sprint 30 closed the hot-phase dogfood readiness contract for Weave and the paired governed runtime contract for Weaver.

The sprint remains scoped to dogfood readiness, support-safe evidence, and governed agentic AI claims. It does not claim public production readiness, always-on autonomous PA availability, unrestricted background action, or provider lock-in.

## Governing artifacts

- Pinned Weave Specification Corpus: `specs/weave-specs.lock.json`
- Weave operating model: `docs/weave-operating-model.md`
- Product-line direction: `docs/product-line-and-weaver-plan.md`
- Sprint evidence pack: `docs/sprint-30-hot-phase-evidence-pack.md`
- Executable hot-phase guard: `tools/sprint30_hot_phase_check.py`
- Weave release fixtures: `release/sprint-30-hot-phase/`
- Weaver governed runtime contract: `masssi164/weaver` PR #17

## Issue DAG final state

| Repo | Issue | Acceptance result |
| --- | ---: | --- |
| Weave | #689 | Exact slogan shipped across product/admin/docs surfaces with guarded dogfood claim boundaries. |
| Weave | #690 | Profile-driven setup evidence added for `dev`, `local-lan-dogfood`, `public-dogfood`, and guarded `production`; LAN phone flow rejects loopback/Mac-only targets. |
| Weave | #691 | Agentic category gap addressed as governed beta direction without M365 lock-in or unrestricted PA claims. |
| Weave | #692 | Weave Control/Admin Console positioned as organization Weaver policy source of truth. |
| Weave | #693 | Mobile action-request and approval-receipt evidence fixture added for governed Weaver approvals. |
| Weave | #694 | Support-safe hot-phase evidence pack and executable gate added. |
| Weaver | #13 | Runtime consumes signed Weave Control policy and fails closed when policy/profile is missing or invalid. |
| Weaver | #14 | Background PA mode defined as read-only risk-detection with approval-required writes. |
| Weaver | #15 | Phone approval action-request events exposed with support-safe audit/receipt refs. |
| Weaver | #16 | Data-sovereignty and privacy boundary documented for governed runtime. |

## Merged PRs

| Repo | PR | Merge commit | Notes |
| --- | ---: | --- | --- |
| Weave | #695 | `bdacad6e04028d6b8f1d7b58af9e61b4d849e380` | Hot-phase readiness contracts, fixtures, slogan surfaces, evidence gate. |
| Weaver | #17 | `67695fba98f007db4f4726d215f90d8a961f8a50` | Governed Weave Control policy contracts, Weaver runtime docs, mobile approval events. |

## Evidence gates

Weave local gates passed before PR #695:

- `./gradlew sprint30HotPhaseCheck docsStructureCheck adminCi`
- `./gradlew releaseEvidenceCheck`
- `./gradlew docsCheck`
- `./gradlew clientCi`
- `./gradlew acceptanceContract`

Weave GitHub PR #695 gates after label correction:

- Gradle CI: success
- Release Notes Label Check: success with `release-notes-feature`

Weaver local gates passed before PR #17:

- `pnpm tsgo:core`
- `pnpm tsgo:extensions`
- `npm test -- --run src/weave-control/governed-weaver-policy.test.ts extensions/weave-chat/src src/weaver/runtime-profile.test.ts src/weaver/weave-chat-roundtrip-harness.test.ts`
- Changed-file type-aware `oxlint` for governed policy/runtime files
- Changed-file type-aware `oxlint` for `extensions/weave-chat/src`

Weaver broad `npm run verify` and `npm run check` were attempted. They reached useful typecheck/lint/test progress, then hung in local tooling after no new diagnostics. The merged PR relies on the concrete green gates above.

## Support-safe evidence posture

The Sprint 30 evidence pack and fixtures intentionally use support-safe references only:

- No raw secrets, bearer tokens, credential-bearing URLs, tenant URLs, provider payloads, provider errors, raw CI logs, member content, raw prompts, or private memory.
- Normal member/client surfaces remain provider-neutral.
- Admin/provider setup, SecretRefs, bootstrap diagnostics, CI/CD targets, endpoint rotation, and Weaver runtime administration remain outside normal member UX.

## Release and RC impact

Sprint 30 advances dogfood readiness for governed agentic AI without widening the public release claim. Release notes classify the work as feature-level hot-phase readiness evidence and guarded governance contracts.

## Unresolved decisions / deferred work

- Public production readiness remains deferred until separate release gates and live infrastructure evidence pass.
- Always-on autonomous PA remains out of scope; governed background mode is read-only risk-detection with explicit approval for writes.
- Native push production readiness remains unclaimed until dedicated mobile/provider evidence exists.

## Final closure gate

At closure-report creation time:

- Weave issues #689–#694 are closed.
- Weaver issues #13–#16 are closed.
- Weave milestone `Sprint 30 — Hot Phase Agentic Dogfood Readiness` has zero open issues.
- Weaver milestone `Sprint 30 — Weaver Hot Phase Control Contracts` has zero open issues.
- Next safe action: merge this closure report to Weave `main`, then close both Sprint 30 milestones.
