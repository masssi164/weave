# Sprint 8 delivery board

Status: active delivery policy for `Sprint 8 — Canonical Domains & Portable Provider Contracts`.

Sprint 8 turns the provider-neutral product direction into reviewable delivery lanes for canonical domains, portable provider contracts, Keycloak desired-state dry-run, Admin Console control-plane gates, and the OpenClaw-derived Weaver foundation. The sprint board is the historical execution surface; repo docs, transitional specs, issues, PRs, and CI evidence are implementation evidence, while the pinned Weave Specification Corpus remains canonical product/domain truth.

## Milestone and board

- Milestone: [`Sprint 8 — Canonical Domains & Portable Provider Contracts`](https://github.com/masssi164/weave/milestone/8)
- Board: [Weave Delivery Board](https://github.com/users/masssi164/projects/2/views/1)
- Prior related epic: [`#283`](https://github.com/masssi164/weave/issues/283) remains historical/related vertical mapping work. It is not the only Sprint 8 epic and does not replace the issue DAG below.
- Stale completed milestones are archived/closed when they have zero open issues. Sprint 4, Sprint 7, and Weave Stack Hardening v1 are not active Sprint 8 planning surfaces.

## Required labels

Every Sprint 8 issue must carry enough labels to make ownership, priority, and evidence visible without reading session history.

| Label group | Required use |
| --- | --- |
| `track:sprint-8` | Required on every Sprint 8 issue. |
| `priority:p0`, `priority:p1`, `priority:p2` | Required on every Sprint 8 issue. P0 blocks the architecture/control-plane foundation; P1 is required product behavior; P2 is polish or marketing readiness. |
| `type:epic`, `type:story`, `type:task`, `type:docs`, `type:tech-debt` | Required on every Sprint 8 issue. Use one primary type unless a task is deliberately both delivery and debt cleanup. |
| `track:devops`, `track:dev`, `track:marketing-dev` | Required where it clarifies the delivery lane. |
| `domain:identity`, `domain:people`, `domain:spaces`, `domain:boards`, `domain:weaver`, `domain:health`, `domain:portability`, `domain:domain-registry` | Required where the issue changes a product/domain contract. |
| `area:*` | Optional for cross-stack routing when a domain label is not specific enough. |

## Board columns

Use these project statuses for Sprint 8 items:

1. **Inbox** — captured but not yet accepted into Sprint 8 sequencing.
2. **Ready** — acceptance criteria, dependencies, labels, and evidence gate are explicit.
3. **In Progress** — branch or implementation/review work is active.
4. **PR Review** — PR is open, has exactly one `release-notes-*` label, and is awaiting checks/review.
5. **Evidence / Verify** — implementation is done and evidence, CI, release-note impact, or board state is being verified.
6. **Done** — merged, issue closed, evidence recorded, and no follow-up blocker remains.
7. **Parked** — explicitly deferred, superseded, or blocked by a product-core decision.

No issue should move to **Done** without acceptance criteria, evidence, and a claim decision. Product-readiness claims, README/marketing claims, provider apply, and Weaver runtime execution stay blocked until their specific evidence gates exist.

## Sprint 8 dependency DAG

| Order | Issue | Dependency rule | Evidence gate |
| --- | --- | --- | --- |
| 0 | `#425` Sprint setup | Root governance task. Must be completed before treating the sprint as unambiguous. | This board policy, milestone/label audit, stale milestone closure, and issue label audit. |
| 1 | `#426` foundation docs | Depends on `#425`. Establishes canonical domain/OpenClaw foundation documentation for later implementation. | Docs gate plus links to governing specs/docs. |
| 1 | `#427` canonical domain registry | Depends on `#425`; may run with `#426` if file ownership is separate. | Server/admin/client contract or spec evidence for registry states and provider-neutral vocabulary. |
| 2 | `#428` Space anchor | Depends on `#427` vocabulary. | Space model acceptance/evidence proving cross-domain anchor semantics. |
| 2 | `#429` portability migration contract | Depends on `#427`; informs provider-switch and apply work. | No-unaccounted-data-loss contract with dry-run, lossy-field, provenance, rollback, and redaction evidence. |
| 3 | `#430` identity dry-run | Depends on `#429` for apply boundaries. | Desired-state dry-run evidence; no live realm mutation. |
| 3 | `#431` Admin Console control plane | Depends on `#427` and `#429`. | Admin-only setup/switching states and support-safe diagnostics. |
| 3 | `#432` Boards portability | Depends on `#427` and `#429`; related to `#283`. | OpenProject and placeholder adapter parity against one Boards contract. |
| 3 | `#433` Weaver runtime profile contract | Depends on `#427` and policy ordering. | Per-user OpenClaw-derived profile contract; disabled-by-default; no runtime execution. |
| 4 | `#434` Sprint 8 acceptance scenario | Depends on architecture/control-plane issues above. | `./gradlew acceptanceContract` and scenario mapping evidence. |
| 5 | `#435` README rewrite | Depends on evidence from `#426`-`#434`; do not overclaim before evidence exists. | Docs/release evidence and product-ready claim discipline. |

## Current Sprint 8 issue audit

As of the board policy update, all Sprint 8 milestone issues have `track:sprint-8`, priority, type, and delivery/domain labels. Issue bodies carry acceptance criteria; gaps discovered during implementation must be fixed on the issue before a PR is marked review-ready.

