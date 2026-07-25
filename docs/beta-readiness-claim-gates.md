# Beta readiness slice and claim gates

Status: Sprint 32 issue #830 product/spec evidence artifact. This page defines the checked-in Beta readiness slice for Weave and the evidence gates that must be green before contributors or release notes claim the slice as proven.

Beta is an end-to-end product state for the Weave organization suite. It is not a single integration smoke, a single MCP bridge smoke, or proof that every future production provider is ready. The user-facing product surfaces are the Admin Console / `weave-ctrl`, Weave Client, and governed Weaver experiences inside those surfaces. MCP remains an internal governed runtime bridge used by Weaver implementation slices; docs and release notes must not present MCP as the member-facing product surface.

## Canonical Sprint 32 issue set

Sprint 32 Beta readiness is governed by the GitHub milestone and the `track:sprint-32` issues. The following issue set is the Sprint 32 Beta slice that this page maps to claims and gates:

| Issue | Slice | Beta role | Claim status |
| --- | --- | --- | --- |
| [#830](https://github.com/masssi164/weave/issues/830) | Beta readiness slice and claim gates | Defines this checked-in product/spec artifact. | This page is the docs acceptance artifact; it does not by itself prove runtime readiness. |
| [#831](https://github.com/masssi164/weave/issues/831) | Adapter continuity dry-run | Foundation/server portability requirement. | Required before any Beta claim that adapter switching avoids unaccounted data loss. |
| [#832](https://github.com/masssi164/weave/issues/832) | Admin setup/control readiness preview | Admin Console / `weave-ctrl` Beta path. | Required before claiming admins can prepare, preview, and govern Beta readiness. |
| [#833](https://github.com/masssi164/weave/issues/833) | Approval-required Weaver actions | Governed Weaver control path. | Required before claiming shared-state Weaver actions route through user approval and audit. |
| [#834](https://github.com/masssi164/weave/issues/834) | Member Client + Weaver Beta flow | User-facing Weave Client + Weaver path. | Required before claiming members can use the Beta flow end to end. |
| [#835](https://github.com/masssi164/weave/issues/835) | Admin + User + Weaver E2E proof | End-to-end evidence. | Required before claiming the Beta path is proven beyond component tests. |
| [#836](https://github.com/masssi164/weave/issues/836) | Release evidence and demo alignment | Release/evidence closure. | Required before release notes, demo scripts, and closure reports use Beta-ready wording. |
| [#828](https://github.com/masssi164/weave/issues/828) / [#829](https://github.com/masssi164/weave/pull/829) | Server-side governed bridge cleanup | Foundation cleanup for Weaver execution boundaries. | Foundation cleanup only. It supports Sprint 32 but is not Beta completion and must not be described as the Beta product proof. |

Older Sprint 32 portability, client, admin, and control-plane issues remain supporting evidence when linked from the milestone, but the Beta claim may be promoted only when the issue-specific evidence above is complete and the closure gate in #836 confirms the claim posture.

## Beta product promise

A Sprint 32 Beta candidate may claim only this bounded product promise:

> Weave provides a dogfood-production Beta slice where an admin can prepare and control a provider-neutral organization workspace, invite members into the Weave Client, enable governed per-user Weaver assistance, and review evidence that provider/adapter changes are dry-run, auditable, and blocked when they would cause unaccounted data loss.

The promise is intentionally bounded:

- **Admin perspective:** `weave-ctrl` and the Admin Console show setup/control readiness, Weaver enablement/control, identity/RBAC posture, policy preview, adapter readiness, audit references, and release evidence without exposing raw provider setup to normal members.
- **User perspective:** Weave Client provides the member workspace and Weaver entry point with domain-first language, handoff-first onboarding, approval prompts for shared-state changes, and accessibility evidence.
- **Weaver perspective:** Weaver operates as a governed per-user capability with server-owned runtime profiles, policy-bound tools, approval receipts for writes/shared-state actions, support-safe audit references, and no raw provider credential exposure.
- **Foundation perspective:** canonical domain/application services, northbound projections,
  provider adapters, migration/dry-run evidence, support bundles, and release evidence form the
  proof boundary. Adapter switching without unaccounted data loss is a first-class Beta
  requirement, not a future enhancement.

## Claim matrix

Every Beta claim must identify a claim state and evidence source. Use `proven_beta` only after all named gates pass for the issue or release decision that owns the claim.

| Claim | Component scope | Evidence source | Required gates before `proven_beta` | Current Sprint 32 posture |
| --- | --- | --- | --- | --- |
| Admins can prepare a Beta workspace and review setup/control readiness before inviting members. | Admin Console, `weave-ctrl`, control plane, identity/RBAC readiness. | CI, Admin UI tests, setup/control docs, release notes. | #832 complete; `./gradlew adminCi`; relevant server/control-plane tests; `./gradlew docsCheck`; release-note entry reviewed under #836. | Beta target, not proven by this page alone. |
| Normal members enter through the Weave Client, not provider setup or raw infrastructure flows. | Weave Client onboarding, workspace/home, member settings. | CI, client tests, a11y smoke, release notes. | #834 complete; `./gradlew clientCi`; accessibility smoke evidence; `./gradlew docsCheck`; #836 release-note alignment. | Beta target, not proven by this page alone. |
| Weaver can assist a user through governed, per-user controls and approval-required shared-state actions. | Weave Client + Weaver, server runtime profile authority, internal bridge, audit receipts. | CI, E2E, approval/audit fixtures, a11y smoke, release notes. | #833 and #834 complete; server and client gates for approval flow; #835 E2E proof; accessibility smoke; #836 release-note alignment. | Beta target. #829 is supporting foundation cleanup only. |
| Adapter switching is reviewed through dry-run evidence and blocked when data loss is unaccounted. | server facades, adapters, migration proof, Admin Console consequence preview, evidence fixtures. | CI, migration dry-run, E2E where applicable, release notes. | #831 complete; `./gradlew portabilityContractCheck` or successor issue gate; migration dry-run artifact showing no unaccounted data loss or explicit blocker; #835/#836 evidence. | First-class Beta requirement; not optional and not implied by provider-neutral wording. |
| Beta readiness is end-to-end across Admin, User, Weaver, and foundation components. | Admin Console/`weave-ctrl`, Weave Client, Weaver, canonical domain/application services, northbound projections, adapters, evidence. | CI, E2E, migration dry-run, a11y smoke, release notes. | #831-#836 complete; `./gradlew docsCheck`; issue-specific CI; #835 E2E evidence; #836 release evidence; milestone closure report. | Blocked until the full issue set is closed with evidence. |
| The internal Weaver bridge is aligned to canonical application use cases and domain facades. | server, `weave-mcp-server`, shared application ports, domain dispatch. | CI and server tests from #829. | #829 merged with server gate evidence. | Proven as foundation cleanup only; not a standalone Beta completion claim. |

## Non-goals and blocked wording

Do not use the following wording in docs, release notes, PR bodies, or issue closure comments unless a later release decision explicitly promotes the evidence:

- "MCP is the Weave product surface" or copy that sends members to MCP instead of Weave Client / Weaver.
- "Beta complete" based only on #829, a bridge smoke, or one component-level CI run.
- "Provider switching is lossless" unless the specific adapter/domain evidence proves there is no unaccounted data loss. Prefer "dry-run reviewed with accounted loss report" when any lossy field or manual remediation remains.
- "Production migration apply", "production rollback", "public launch", or "general availability" for Sprint 32 Beta.
- "Unrestricted autonomous AI", "default Weaver PA availability", or "raw provider access" for Weaver.

## Closure requirements

Sprint 32 Beta claim promotion requires all of the following to be true:

1. GitHub issues #831, #832, #833, #834, #835, and #836 are closed with evidence.
2. The claim matrix above has no `proven_beta` claim whose required gate is missing or failed.
3. CI evidence covers the component gates named by the owning issues.
4. E2E evidence proves the Admin + User + Weaver path, not just an internal bridge call.
5. Migration dry-run evidence for adapter switching records either no unaccounted data loss or a named release blocker.
6. Accessibility smoke evidence is present for the member/admin flows that are claimed as Beta.
7. Release notes describe Beta scope and known limitations without presenting MCP as the user-facing product surface.

## Related evidence

- [Product line and Weaver plan](product-line-and-weaver-plan.md)
- [Quality and evidence](quality-and-evidence.md)
- [Admin-Suite readiness and setup contract](admin-suite-readiness-setup-contract.md)
- [Governed Weaver runtime security contract](governed-weaver-runtime-security-contract.md)
- [Provider portability contract](architecture/provider-portability.md)
- [No-unaccounted-data-loss portability contract](architecture/no-unaccounted-data-loss.md)
- [Product trust and provider-choice claim matrix](product-trust-provider-choice-claim-matrix.md)
- [Unreleased release notes](release-notes/unreleased.md)
- [Sprint 32 Beta readiness release evidence](evidence/sprint-32-beta-readiness-release-evidence.md)
- [Sprint 32 closure report](sprint-32-closure-report.md)
