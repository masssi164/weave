# Sprint 14 delivery board: product trust and provider choice

Status: active Sprint 14 execution plan. GitHub milestone: [Sprint 14 — Product Trust, Provider Choice & Operator Experience](https://github.com/masssi164/weave/milestone/14).

Sprint 14 makes Weave professionally explainable and evidence-backed as a provider-neutral collaboration control plane. The sprint is not a hobby/self-hosting-only scope. The self-hosted reference stack is the strongest proof path for data sovereignty, auditability, operational control, and reversibility, while provider facades keep managed, external, and hybrid providers possible behind stable Weave domains.

## Governing truth

- Specification corpus: `WEAVE-SPEC-0001` Admin-Suite and provider-neutral product core, `WEAVE-DOMAIN-CHAT`, `WEAVE-DOMAIN-WEAVER-GOVERNED-PA`, the domain context map, and the no-unaccounted-data-loss portability contract.
- Repo evidence: [Product line and Weaver plan](../product-line-and-weaver-plan.md), [Provider replacement and anti-silo contract](../provider-replacement-and-anti-silo-contract.md), [Provider portability contract](../architecture/provider-portability.md), [No-unaccounted-data-loss portability contract](../architecture/no-unaccounted-data-loss.md), [Governed Weaver runtime security contract](../governed-weaver-runtime-security-contract.md), and the Sprint 14 issue bodies.
- Research inputs: the Sprint 14 local research pack and public sources listed in [Product trust and provider-choice claim matrix](../product-trust-provider-choice-claim-matrix.md).

## Issue DAG

| Order | Issue | Track | Dependency / sequencing | Evidence expected |
| --- | --- | --- | --- | --- |
| 0 | [#535](https://github.com/masssi164/weave/issues/535) program governance | product/test/devops | Root. Must establish board, claim discipline, and Sprint 14 issue graph before expanding implementation claims. | This board, milestone labels, claim matrix, closure report. |
| 1 | [#536](https://github.com/masssi164/weave/issues/536) Why Weave positioning | product/docs | Runs after #535; informs #545 customer wording. | Approved use/avoid wording in the claim matrix. |
| 1 | [#544](https://github.com/masssi164/weave/issues/544) compliance/risk brief | product/docs/security | Runs after #535 and before public legal/compliance-adjacent copy. | Risk framing in the claim matrix with source anchors and legal-review caveat. |
| 2 | [#537](https://github.com/masssi164/weave/issues/537) Matrix data model research | research/dev | Depends on #535; precedes #538 fixture scope and #541 contract hardening. | Source-backed Matrix mapping in the migration proof. |
| 2 | [#539](https://github.com/masssi164/weave/issues/539) provider-switch journey | product/admin | Depends on #536/#544 wording and existing Admin Console provider-switch gates. | Admin journey requirements and audit/disruption model. |
| 2 | [#540](https://github.com/masssi164/weave/issues/540) provider-agnostic client UX | client/product | Depends on #536 and informs #539 member-impact preview. | Stable capability vocabulary and accessibility expectations. |
| 3 | [#538](https://github.com/masssi164/weave/issues/538) Matrix migration proof | chat/dev/test | Depends on #537; feeds #541 and #542. | Conservative Matrix MVP contract plus future fixture outputs. |
| 3 | [#541](https://github.com/masssi164/weave/issues/541) portability contracts | dev/server | Depends on #537/#538 and existing portability schema v2. | Reusable export/import/cutover/rollback contract updates and tests. |
| 3 | [#519](https://github.com/masssi164/weave/issues/519) Weaver RuntimeProfile carry-over | weaver/server/admin/infra | Guarded. Keep open unless all cross-repo evidence exists; otherwise split into concrete child issues for `weave` and `weaver`. | RuntimeProfile projection, stable `channels.weave-chat`, Credential Broker references, no raw secrets. |
| 4 | [#542](https://github.com/masssi164/weave/issues/542) evidence matrix | test/release | Depends on first product/research/contract outputs; blocks customer-facing overclaims. | Claim matrix with evidence classes and release-blocking gaps. |
| 4 | [#545](https://github.com/masssi164/weave/issues/545) customer-facing claim matrix | marketing/docs | Depends on #536/#542/#544; cannot outpace evidence. | Customer wording classified by evidence/roadmap state. |
| 5 | [#543](https://github.com/masssi164/weave/issues/543) self-hosted operator experience | devops/docs | Depends on #537/#538 for Matrix proof needs and existing infra bootstrap docs. | Reference stack/runbook and operator evidence links. |
| 6 | [#546](https://github.com/masssi164/weave/issues/546) closure | governance | Final; requires merged PR evidence, #519 split/completion decision, and zero open milestone issues. | `docs/sprint-14-closure-report.md` on `origin/main`. |

## #519 decision for this sprint

Do not close #519 from documentation alone. Treat it as an umbrella until all of these are implemented or split into closed child issues with evidence:

1. `weave`: signed `WeaverRuntimeProfile` projection from Weave policy, model aliases, domain provider projections, tool/MCP grants, sandbox/deny policy, CredentialRefs, runtime-token references, audit policy, and profile hash.
2. `weave`: stable `channels.weave-chat` projection backed by Weave Chat-domain routing, with providerRefs such as Matrix/Teams/Slack hidden behind backend routing and normal member UX.
3. `weave`: Credential Broker boundary for RuntimeProfile generation and audit; profiles, logs, support bundles, and evidence contain SecretRefs and broker receipts only.
4. `weaver`: runtime consumption of the signed profile without accepting raw OpenClaw dashboard/config as a second policy source.
5. Cross-repo evidence: tests, docs, CI links, and support-safe fixtures proving no raw provider secrets or OAuth refresh tokens are projected.

Recommended split if direct implementation is too large for one PR train:

| Child issue | Repo | Scope |
| --- | --- | --- |
| RuntimeProfile projection fixture and schema conformance | `weave` | Server-side profile generation, signing/hash metadata, redaction tests. |
| Stable `channels.weave-chat` runtime channel adapter | `weaver` | Consume Weave Chat channel projection and reject provider-named raw channel configs in normal runtime mode. |
| Credential Broker RuntimeProfile grant path | `weave` | Short-lived runtime-token references, SecretRef-only profile content, audit receipts. |
| Cross-repo incorporation evidence | `weave` + `weaver` | CI/evidence bundle linked from #519 and Sprint 14 closure. |

## First implementation slice

This PR train starts with a low-risk docs/evidence slice:

- board/DAG and #519 split decision;
- claim/evidence matrix for professional positioning, procurement-risk language, Matrix-first proof, Admin provider-switch journey, and customer wording;
- Matrix Chat migration proof boundary that can drive later fixture tests without claiming lossless migration.

Follow-up implementation PRs should add machine-readable fixtures and contract checks for Matrix Chat export/import/cutover/rollback, then wire Admin Console and client behavior only after those contracts are executable.
