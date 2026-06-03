# Sprint 23 closure report — Chat Provider Switch

Status: implementation evidence prepared for Sprint 23 closure.

## Governing sources inspected

- GitHub milestone `Sprint 23 — Chat Provider Switch` and issues #627, #628, #629, and #630.
- Closed milestone `Sprint 22 — Free Provider Lab` and `docs/sprint-22-closure-report.md`.
- `docs/product-reality-foundation.md`, `release/product-reality-gates.json`, and `tools/product_reality_claim_gate_check.py`.
- `docs/product-trust-provider-choice-claim-matrix.md` and `docs/product-line-and-weaver-plan.md`.
- Pinned specification corpus lock `specs/weave-specs.lock.json` and repo-local portability contract `specs/0006-portability-contract/`.

## Sprint 22 entry verification

Sprint 22 is closed in GitHub with zero open issues (#623-#626) and PR #656 merged into `main`. The Sprint 23 entry scoreboard remains green at `release/provider-lab/sprint-23-entry-scoreboard.json`, and `providerLabCheck` is still the required entry gate.

## Issue DAG final state

1. #629 hardens canonical Chat object and ProviderRef coverage. It is the base for all migration/rollback evidence.
2. #627 depends on #629 and records Matrix/Synapse to Zulip fixture dry-run/apply evidence with stable Weave Chat domain IDs.
3. #628 depends on #627 and records Zulip to Matrix/Synapse rollback-honesty evidence with conflict classifications.
4. #630 depends on #627-#629 and caps provider-neutral/release wording to the named fixture evidence.

## Files and artifacts changed

- `docs/chat-provider-switch-contract.md` — Sprint 23 operator/developer contract and evidence interpretation.
- `release/provider-lab/chat-switch/canonical-object-coverage.json` — Chat canonical object coverage and ProviderRef redaction policy.
- `release/provider-lab/chat-switch/matrix-zulip-lossy-field-report.fixture.json` — positive LossyFieldReport fixture.
- `release/provider-lab/chat-switch/matrix-zulip-silent-drop-negative.fixture.json` — negative silent-drop fixture.
- `release/provider-lab/chat-switch/matrix-zulip-migration-proof.fixture.json` — Matrix/Synapse to Zulip dry-run/apply fixture evidence for #627.
- `release/provider-lab/chat-switch/zulip-matrix-rollback-proof.fixture.json` — rollback-honesty fixture evidence for #628.
- `release/provider-lab/chat-switch/sprint-23-claim-gate.fixture.json` — scoped claim scoreboard and overclaim rejection fixture for #630.
- `tools/chat_provider_switch_check.py` and `build.gradle` — CI-safe Sprint 23 gate, included in `releaseEvidenceCheck`.
- `docs/product-trust-provider-choice-claim-matrix.md` and `docs/release-notes/unreleased.md` — release/customer wording boundaries.

## Evidence commands

Required local evidence for closure:

```bash
./gradlew providerLabCheck chatProviderSwitchCheck productRealityClaimGateCheck releaseEvidenceCheck docsCheck --console=plain
```

Expected Sprint 23 chat-switch gate summary:

```text
chat-provider-switch-check: ok objects=12 positive=accept negative=reject migration=matrix-to-zulip rollback=zulip-to-matrix claims=scoped providerRefs=support_safe
```

## Claim boundary

Sprint 23 proves only a CI-safe provider-lab fixture scope for Matrix/Synapse to Zulip Chat switching, support-safe ProviderRefs, explicit LossyFieldReport coverage, rollback-honesty classification, and stable Weave Chat domain IDs. It does not prove production apply, production rollback, provider interchangeability, lossless migration, full-history preservation, E2EE history migration, customer-ready status, or release readiness.

## Open blockers for release claims

- Production provider mutation and rollback remain blocked until `release_ready` evidence exists.
- E2EE history migration remains `unsupported`/`coming_later`.
- Commercial chat adapters are outside Sprint 23.
- Lossless all-provider portability remains unproven.

## Sprint 24 handoff

Sprint 24 — Weaver Runtime Factory may start only after Sprint 23 issues are closed, the Sprint 23 PR is merged to `main`, and final `main` CI is green. Sprint 24 must keep Weaver claims behind governed runtime evidence and must not use Sprint 23 chat-switch evidence to imply Weaver availability.
