# Sprint 14 Closure Report — Product Trust, Provider Choice, and Operator Experience

## Governing sources

- Sprint epic: [weave#535](https://github.com/masssi164/weave/issues/535)
- Closure issue: [weave#546](https://github.com/masssi164/weave/issues/546)
- Delivery board: `docs/project/sprint-14-delivery-board.md`
- Claim matrix: `docs/product-trust-provider-choice-claim-matrix.md`
- Matrix proof boundary: `docs/matrix-chat-migration-proof.md`
- Portability contract: `docs/architecture/provider-portability.md` and `docs/architecture/no-unaccounted-data-loss.md`
- Cross-repo Weaver carry-over: [weave#519](https://github.com/masssi164/weave/issues/519), [weaver#1](https://github.com/masssi164/weaver/issues/1), and [weaver#9](https://github.com/masssi164/weaver/issues/9)

## Final issue state

| Issue | Scope | Final state | Evidence |
| --- | --- | --- | --- |
| [weave#535](https://github.com/masssi164/weave/issues/535) | Sprint program governance | Closed | Delivery board, claim matrix, this closure report, green gates. |
| [weave#536](https://github.com/masssi164/weave/issues/536) | Why Weave positioning | Closed | Approved/avoid wording in `docs/product-trust-provider-choice-claim-matrix.md`; PR #548. |
| [weave#537](https://github.com/masssi164/weave/issues/537) | Matrix-first provider data model research | Closed | Matrix source-object mapping in `docs/matrix-chat-migration-proof.md`; PR #548. |
| [weave#538](https://github.com/masssi164/weave/issues/538) | Self-hosted Matrix Chat migration proof | Closed | Matrix proof and lifecycle fixtures; PRs #548 and #550. |
| [weave#539](https://github.com/masssi164/weave/issues/539) | Admin provider-switch journey | Closed | Admin journey in `docs/matrix-chat-migration-proof.md`; provider selection/apply gates in `docs/admin-operator-handbook.md`; PR #548. |
| [weave#540](https://github.com/masssi164/weave/issues/540) | Provider-agnostic member UX | Closed | Member/client boundary and stable capability states in Matrix proof and claim matrix; PR #548. |
| [weave#541](https://github.com/masssi164/weave/issues/541) | Export/import/cutover/rollback contracts | Closed | Provider portability schema v2 plus Matrix lifecycle fixture validated by `portabilityContractCheck`; PR #550. |
| [weave#542](https://github.com/masssi164/weave/issues/542) | Claim and migration evidence matrix | Closed | Claim matrix, product-trust claim matrix guard, release evidence gate, and portability gate; PRs #548/#550. |
| [weave#543](https://github.com/masssi164/weave/issues/543) | Self-hosted reference stack/operator experience | Closed | `docs/admin-operator-handbook.md`, infra README/operator docs, release-verify/operator-check/backup/restore/support-bundle paths. |
| [weave#544](https://github.com/masssi164/weave/issues/544) | Cloud Act/GDPR/subprocessor/sovereignty risk framing | Closed | Procurement-risk checklist, source anchors, and legal-review caveat in claim matrix; PR #548. |
| [weave#545](https://github.com/masssi164/weave/issues/545) | Customer-facing claim matrix | Closed | Customer wording/evidence table in claim matrix; PR #548. |
| [weave#546](https://github.com/masssi164/weave/issues/546) | Sprint closure | Closed | This report, zero open Sprint 14 issues, green gates, merged PR #553, and closed `weave#519`. |
| [weave#519](https://github.com/masssi164/weave/issues/519) | Weaver RuntimeProfile / `weave-chat` / Credential Broker carry-over | Closed | Weave PRs #520/#521/#528/#529/#530/#532/#533 and merged PR #553, Weaver PRs #7/#8/#10/#11/#12, closed `weaver#1` and `weaver#9`. |

## Merge train

1. [weave#548](https://github.com/masssi164/weave/pull/548) — `9b67fed` — Sprint 14 product-trust docs, delivery board, claim matrix, Matrix proof boundary, and fixture checks.
2. [weave#550](https://github.com/masssi164/weave/pull/550) — `4f7c33f` — Matrix Chat lifecycle fixture and executable portability guard for preflight, blocked apply, rollback-retention, support-safe redaction, and overclaim prevention.
3. [weaver#11](https://github.com/masssi164/weaver/pull/11) — `448def6` — offline and live/container `weave-chat` LM Studio round-trip harness; closes `weaver#9`.
4. [weaver#12](https://github.com/masssi164/weaver/pull/12) — `ada8c4b` — support-safe channel/model audit refs; closes `weaver#1` with prior Weaver RuntimeProfile PR evidence.
5. Earlier cross-repo carry-over evidence: Weave PRs #520, #521, #528, #529, #530, #532, and #533; Weaver PRs #7, #8, and #10.
6. Final Weave carry-over PR: [weave#553](https://github.com/masssi164/weave/pull/553) — merged as `0e3eea6`; PA Weaver Chat facade, configurable governed bridge, support-safe service-level live evidence for `channels.weave-chat` -> Weaver -> LM Studio -> assistant response, and review coverage ensuring the user-facing send response reflects actual completion evidence.

PR #549 was closed unmerged because merged PR #550 superseded it with the Matrix lifecycle fixture and removed the conflicting duplicate branch.

## Evidence and gates

Closure PR branch evidence run based on current `origin/main` after PR #550:

```text
./gradlew productTrustClaimMatrixCheck portabilityContractCheck docsCheck releaseEvidenceCheck --no-daemon
# BUILD SUCCESSFUL
```

Targeted Weaver evidence before closing the cross-repo runtime anchors:

```text
node scripts/run-vitest.mjs src/weaver/weave-chat-roundtrip-harness.test.ts src/weaver/runtime-profile.test.ts
pnpm format:docs:check -- docs/channels/weave-chat.md
node --import tsx scripts/weaver/weave-chat-roundtrip.ts > /tmp/weaver-weave-chat-roundtrip-evidence.json
node scripts/run-vitest.mjs src/weaver/runtime-profile.test.ts
pnpm docs:check-mdx docs/weaver-runtime-profile.md
```

PR #553 live PA Weaver recheck after Massimo's acceptance correction:

```text
docker run --rm \
  -v "$HOME/Library/Application Support/mkcert/rootCA.pem:/tmp/mkcert-rootCA.pem:ro" \
  -e CURL_CA_BUNDLE=/tmp/mkcert-rootCA.pem \
  curlimages/curl:8.11.1 -sS https://lmstudio.home.internal/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen/qwen3.5-9b","messages":[{"role":"system","content":"Reply in one short sentence."},{"role":"user","content":"Weave live verification: say READY."}],"temperature":0,"max_tokens":32}'
# HTTP call completed from a Docker container with mkcert CA trust; model=qwen/qwen3.5-9b returned a chat completion id and content.

WEAVE_WEAVER_PA_CHAT_LIVE=true ./gradlew test --tests 'com.massimotter.weave.backend.service.ChatFacadeServiceLiveWeaverRoundTripTest'
# BUILD SUCCESSFUL; asserts pa-weaver -> channels.weave-chat -> governed bridge -> LM Studio -> assistant response,
# mode=live, liveCall=completed, source=lmstudio-openai-compatible,
# weaverReceived=true, lmStudioResponseReceived=true, rawProviderDiagnosticsExposed=false.

./gradlew test --tests 'com.massimotter.weave.backend.controller.AdminControlPlaneControllerTest.adminControlPlaneOverviewIsSupportSafeAndProviderNeutral' \
  --tests 'com.massimotter.weave.backend.controller.AdminControlPlaneControllerTest.adminReadinessTestsAndPolicyUpdatesAreAuditedAndRedacted'
# BUILD SUCCESSFUL; asserts admin-owned model provider selection and support-safe Weaver distribution projection.
```

Post-merge `main` closure evidence:

```text
CI run https://github.com/masssi164/weave/actions/runs/26738067087
# head=e4af279, workflow=CI, conclusion=success

Live Stack E2E run https://github.com/masssi164/weave/actions/runs/26738076622
# head=e4af279, workflow=Live Stack E2E, conclusion=success
# artifact=weave-live-stack-acceptance-evidence, id=7324563895, size=26917, expired=false
# manifest: valid=true, scenarioCount=41, mappingCount=41
# liveRuntimeMappingCount=8, offlineSpecMappingCount=33
# runtimeEvidenceCollected=true, findings=[]
# observed markers: AUTH_RESULT, PROFILE_RESULT, CHAT_RESULT, MATRIX_RESULT
# E2EE_RESULT, FILES_RESULT, PROVIDER_STACK_RESULT, CALENDAR_RESULT
# BOARDS_RESULT, PROVIDER_REALITY_RESULT
```

## Decisions and boundaries

- Weave is positioned as a provider-neutral collaboration control plane, not as a hobby-only self-hosting bundle.
- The reference self-hosted stack is the strongest proof path for sovereignty, auditability, operational control, and reversibility, but product architecture remains provider-neutral.
- Public claims must stay inside the claim matrix evidence class. Forbidden wording remains: GDPR-proof, Cloud-Act-proof, guaranteed compliant, legally sovereign, compliant by default, no vendor lock-in without scope, or lossless migration without named fixture proof.
- Matrix Chat is the first no-cost provider proof. Sprint 14 proves support-safe classification, preflight, blocked apply, and rollback-retention evidence; it does not enable production apply.
- Matrix encrypted-room history remains unsupported/coming_later until a client-side key/export strategy exists.
- Normal members see stable Weave capability states only. Raw provider diagnostics, secrets, URLs, Matrix internals, MCP/tool internals, and migration reports stay in admin/operator/support-safe evidence surfaces.
- Weaver runtime profiles use stable `channels.weave-chat`, support-safe audit refs, SecretRefs/runtime-token references, and deny-by-default policy boundaries; raw provider channels/secrets are not projected to normal runtime/member surfaces.

## Follow-up / next implementation sprint

Sprint 15 should implement from the now-evidenced contracts, not expand claims first:

- turn Matrix Chat proof artifacts into server/admin migration dry-run implementation slices;
- decide media copy/reference retention and power-level mapping policy before any apply path;
- keep E2EE encrypted-history migration blocked until client-side export/key strategy is designed and fixture-tested;
- connect Admin Console provider-switch screens to backend-owned preflight/consequence/rollback evidence;
- add accessibility/manual UX evidence for member disruption copy before promoting customer-facing provider-switch flows.

## Closure gate

- [x] Sprint 14 issues are labeled by track/phase and represented in the delivery board.
- [x] Product positioning and avoid wording are recorded in the claim matrix.
- [x] Procurement-risk framing separates legal compliance from engineering/operational evidence.
- [x] Matrix-first migration scope is explicit about supported, lossy, manual-review, unsupported, archive-only, and coming_later states.
- [x] Product-trust claims are checked by `productTrustClaimMatrixCheck`.
- [x] Provider portability contracts and Matrix lifecycle fixtures are executable through `portabilityContractCheck`.
- [x] Release/claim posture is guarded through `releaseEvidenceCheck`.
- [x] `weave#519`, `weaver#1`, and `weaver#9` are closed with cross-repo evidence. PR #553 is merged, `weave#519` is closed, and the Sprint 14 milestone closure gate verifies zero open Sprint 14 issues.
- [x] Closure report exists on `origin/main` with Sprint 14 closed.
