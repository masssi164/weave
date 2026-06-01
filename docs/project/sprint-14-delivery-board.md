# Sprint 14 delivery board: product trust and provider choice

Status: closed Sprint 14 execution record. GitHub milestone: [Sprint 14 — Product Trust, Provider Choice & Operator Experience](https://github.com/masssi164/weave/milestone/14) is closed with zero open issues. Cross-repo Weaver milestone: [Sprint 14 — Product Trust, Provider Choice & Operator Experience](https://github.com/masssi164/weaver/milestone/2) is closed with the Weaver runtime anchors completed.

Board rule: every open issue listed on this Delivery Board is Sprint 14 scope and must be assigned to the Sprint 14 milestone in its owning repository. Use the local Sprint 14 ops script `sprint_14/scripts/add_delivery_board_issues_to_sprint14.sh` after board edits so Weave and Weaver issue metadata stay aligned.

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
| 3 | [`weave#519`](https://github.com/masssi164/weave/issues/519) Weaver RuntimeProfile carry-over | weaver/server/admin/infra | Guarded cross-repo parent. Keep open unless all cross-repo evidence exists; otherwise split into concrete child issues for `weave` and `weaver`. | RuntimeProfile projection, stable `channels.weave-chat`, Credential Broker references, no raw secrets. |
| 3 | [`weaver#1`](https://github.com/masssi164/weaver/issues/1) governed Weaver runtime and stable `weave-chat` channel | weaver/runtime/chat/security | Child/implementation anchor for `weave#519`. Must move from Sprint 13 carry-over into Sprint 14 and remain release-blocking until the governed runtime seam is proven. | RuntimeProfile loader seam, stable `channels.weave-chat`, raw config lockdown, deny-by-default tool policy, SecretRef/runtime-token-only credential boundary, audit refs. |
| 4 | [`weaver#9`](https://github.com/masssi164/weaver/issues/9) LM Studio container round-trip evidence | weaver/runtime/test/chat | Depends on `weaver#1` seam and local LM Studio runtime config verification. Proves the first end-to-end `weave-chat` runtime path without leaking raw provider config/secrets. | Inbound Weave Chat message → model response → outbound reply, container-visible LM Studio URL, support-safe evidence, explicit offline/skip interpretation. |
| 4 | [#542](https://github.com/masssi164/weave/issues/542) evidence matrix | test/release | Depends on first product/research/contract outputs; blocks customer-facing overclaims. | Claim matrix with evidence classes and release-blocking gaps. |
| 4 | [#545](https://github.com/masssi164/weave/issues/545) customer-facing claim matrix | marketing/docs | Depends on #536/#542/#544; cannot outpace evidence. | Customer wording classified by evidence/roadmap state. |
| 5 | [#543](https://github.com/masssi164/weave/issues/543) self-hosted operator experience | devops/docs | Depends on #537/#538 for Matrix proof needs and existing infra bootstrap docs. | Reference stack/runbook and operator evidence links. |
| 6 | [#546](https://github.com/masssi164/weave/issues/546) closure | governance | Final; requires merged PR evidence, #519 split/completion decision, and zero open milestone issues. | `docs/sprint-14-closure-report.md` on `origin/main`. |

## #519 decision for this sprint

`weave#519` is closed for Sprint 14. The cross-repo runtime seam is evidenced by closed Weaver child anchors [`weaver#1`](https://github.com/masssi164/weaver/issues/1) and [`weaver#9`](https://github.com/masssi164/weaver/issues/9), Weave-side projection/audit/infra evidence from PRs #520, #521, #528, #529, #530, #532, and #533, and merged PR #553. PR #553 adds the Weave-side PA Weaver chat facade and configurable HTTP bridge slice, including service-level live Weave Chat evidence for: PA Weaver option -> send message -> governed Weaver bridge -> LM Studio -> assistant response. Sprint 14 is closed after PR #553 merged, `weave#519` closed, and the GitHub milestone closure gate verified zero open Sprint 14 issues.

Current evidence status:

1. `weave`: signed `WeaverRuntimeProfile` projection from Weave policy, model aliases, domain provider projections, tool/MCP grants, sandbox/deny policy, CredentialRefs, runtime-token references, audit policy, and profile hash is implemented by prior PRs.
2. `weave`: stable `channels.weave-chat` projection is exposed through the Weave Chat-domain routing path; PR #553 keeps providerRefs hidden behind support-safe facade evidence and fails closed when the governed bridge is not configured.
3. `weave`: Credential Broker boundary for RuntimeProfile generation and audit; profiles, logs, support bundles, and evidence contain SecretRefs and broker receipts only.
4. `weave`: PR #553 service-level live evidence covers the full PA Weaver option -> Weave Chat message -> governed Weaver bridge -> LM Studio -> assistant response path without exposing provider URLs, tokens, provider room ids, or unsafe diagnostics.
5. `weave`: Admin Control Plane now exposes and accepts the admin-owned `model` provider category; `weaverDistributionPolicy.modelAliases[0]` projects `lmstudio` / `lmstudio/qwen/qwen3.5-9b` with `credentialref://weave/channels/weave-chat/runtime-token`, so model-provider switching is not a member-client setting.
6. `weave`: container trust for the local HTTPS LM Studio endpoint was verified with the mkcert root CA mounted read-only and `CURL_CA_BUNDLE=/tmp/mkcert-rootCA.pem`; unsafe TLS-disable flags are not part of the final evidence path.
7. `weaver`: runtime consumption of the signed profile without accepting raw OpenClaw dashboard/config as a second policy source is covered by the closed Weaver child issues.
8. Cross-repo evidence: tests, docs, CI links, and support-safe fixtures prove no raw provider secrets or OAuth refresh tokens are projected.
9. Final closure: PR #553 is merged, `weave#519` is closed with merged evidence, and the Sprint 14 milestone has zero open issues.

Historical split option, now resolved by the Sprint 13/14 merge train:

| Child issue | Repo | Scope |
| --- | --- | --- |
| RuntimeProfile projection fixture and schema conformance | `weave` | Server-side profile generation, signing/hash metadata, redaction tests. |
| Stable `channels.weave-chat` runtime channel adapter | `weaver` | Consume Weave Chat channel projection and reject provider-named raw channel configs in normal runtime mode. |
| Credential Broker RuntimeProfile grant path | `weave` | Short-lived runtime-token references, SecretRef-only profile content, audit receipts. |
| Cross-repo incorporation evidence | `weave` + `weaver` | CI/evidence bundle linked from #519 and Sprint 14 closure. |

## Implementation slices delivered

The Sprint 14 PR train delivered:

- board/DAG and #519 completion decision;
- claim/evidence matrix for professional positioning, procurement-risk language, Matrix-first proof, Admin provider-switch journey, and customer wording;
- Matrix Chat migration proof boundary without lossless/legal overclaims;
- machine-readable Matrix migration proof and lifecycle fixtures;
- `portabilityContractCheck`, `docsCheck`, and `releaseEvidenceCheck` gates on current `main`;
- cross-repo Weaver `weave-chat` runtime evidence and support-safe audit refs;
- merged PR #553 and closed `weave#519` with zero open Sprint 14 milestone issues.

Next implementation sprint should turn the blocked Matrix migration proof into concrete admin/server implementation slices only after media retention, power-level mapping, and E2EE client-export strategy decisions are made explicitly.
