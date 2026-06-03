# Sprint 21 closure report — Product Reality Foundation & Claim Gates

Date: 2026-06-03

Status: **READY FOR AUTONOMOUS CLOSURE AFTER PR #655 MERGES**. Sprint 21 establishes the product-reality foundation needed for Sprint 22 provider-lab execution: unsupported release, customer-ready, provider-interchangeability, migration/rollback, and Weaver availability claims are gated by explicit evidence checks before they can be merged.

## Governing specs and issues

- Product/spec truth remains pinned by `specs/weave-specs.lock.json` with the local default corpus `../weave-specs`.
- Product direction remains `docs/product-line-and-weaver-plan.md`: Weave is the provider-neutral organization suite first, admin/provider readiness second, and Weaver remains a governed per-user PA runtime later.
- Sprint 21 milestone: `Sprint 21 — Product Reality Foundation & Claim Gates`.
- Sprint 21 issues: #618, #619, #620, #621, and #622.
- Implementation PR: #655 `chore: enforce Sprint 21 product reality gates`.

## Issue DAG and final state

1. #618 `epic(truth): freeze release claims behind evidence gates`
   - State: **satisfied by PR #655**.
   - Evidence: `release/product-reality-gates.json`, `tools/product_reality_claim_gate_check.py`, Gradle `productRealityClaimGateCheck`, `releaseEvidenceCheck`, and expanded docs scans.
   - Boundary: no production release or customer-ready claim is made by this sprint.

2. #619 `epic(registry): align reality levels and adapter manifests`
   - State: **satisfied by PR #655**.
   - Evidence: canonical provider reality levels are exactly `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, and `release_ready` across release gates, domain registry validation, server enum/runtime, admin type normalization, and client DTO parsing.
   - Boundary: old aliases such as `configured_readiness`, `live_adapter_read`, and `live_adapter_write` are rejected rather than silently accepted.

3. #620 `epic(domains): finish canonical Chat Files Calendar and Weaver objects`
   - State: **satisfied by PR #655**.
   - Evidence: `release/product-reality-gates.json`, canonical domain registry resources, `CanonicalDomainRegistry`, `ProviderCapabilityContracts`, `DomainAdapterRegistryMapperTest`, and `CanonicalDomainRegistryContractTest` now carry the Sprint 21 canonical object names for Chat, Files, Calendar, and Weaver.
   - Boundary: this is registry/contract truth, not live provider parity.

4. #621 `epic(lab): prepare the free provider lab for executable proof`
   - State: **satisfied for Sprint 21 foundation**.
   - Evidence: provider-lab manifest entries and claim gates name the initial free/self-hosted proof targets for identity, chat, files, calendar, boards, and Weaver while keeping their minimum Sprint 21 reality level at `contract_only` until executable proof exists.
   - Boundary: Sprint 22 must perform the executable provider-lab proof; Sprint 21 only prevents unsupported overclaims.

5. #622 `epic(roadmap): rebase release sequence and keep humans last`
   - State: **satisfied by PR #655**.
   - Evidence: product-reality docs, claim matrix, release notes, and release gates preserve the human-last release validation boundary and prevent automated checks from being represented as release/customer-ready proof without named evidence.
   - Boundary: human validation remains after automated E2E/runtime/migration/rollback/restore/support-safe evidence, not before it.

Dependency shape:

- #618 is the top-level claim gate and depends on the registry vocabulary/object truth from #619 and #620.
- #621 depends on #618/#619 so the free provider lab starts from honest, non-release-ready labels.
- #622 integrates the release sequencing and prevents Sprint 21 from becoming an unsupported release-promotion claim.

## Files and contracts changed

Primary artifacts:

- `release/product-reality-gates.json`
- `tools/product_reality_claim_gate_check.py`
- `tools/domain_registry_check.py`
- `specs/0004-domain-registry/spec.md`
- `specs/0004-domain-registry/canonical-domain-registry-v1.json`
- `server/src/main/resources/canonical-domain-registry-v1.json`
- `server/src/main/resources/contracts/canonical-domain-registry.v1.json`
- `server/src/main/java/com/massimotter/weave/backend/domainregistry/CanonicalDomainRegistry.java`
- `server/src/main/java/com/massimotter/weave/backend/provider/ProviderRealityLevel.java`
- `server/src/main/java/com/massimotter/weave/backend/provider/ProviderCapabilityContracts.java`
- `server/src/main/java/com/massimotter/weave/backend/provider/ProviderCategoryHealthMapper.java`
- `admin-console/src/api.ts`
- `admin-console/src/App.test.tsx`
- `client/lib/features/app/domain/entities/provider_stack_snapshot.dart`
- `client/lib/integrations/weave_api/data/dtos/provider_stack_response_dto.dart`
- `client/test/integrations/weave_api/data/services/weave_api_client_test.dart`
- `docs/product-reality-foundation.md`
- `docs/product-trust-provider-choice-claim-matrix.md`
- `docs/domain-registry-v1.md`
- `docs/admin-operator-handbook.md`
- `docs/release-v0.1-rc3-evidence.md`
- `docs/release-notes/unreleased.md`

## Validation evidence

Local gates run on PR #655 branch:

- `./gradlew domainRegistryCheck specContract acceptanceContract --console=plain` — passed.
- `./gradlew serverCi --console=plain` — passed.
- `./gradlew adminCi clientCi --console=plain` — passed.
- `python3 tools/product_reality_claim_gate_check.py` — passed.
- `./gradlew productRealityClaimGateCheck --console=plain` — passed.
- `./gradlew productRealityClaimGateCheck releaseEvidenceCheck docsCheck adminCi --console=plain` — passed after Copilot review fixes.

GitHub checks for PR #655:

- `Release Notes Label Check` — passed on head `32e1585`.
- `Gradle CI` — pending at report-writing time; merge is gated on success.

Expected non-blocking release-readiness boundary:

- `./gradlew releaseReadinessCheck --console=plain` remains blocked without explicit current-head CI summary and credentialed Live Stack manifest/waiver. This is correct for Sprint 21 because no release/customer-ready promotion is claimed.

## Review evidence

- GitHub Copilot PR review was requested and produced actionable comments.
- Copilot findings addressed in commit `32e1585`:
  - Added missing `WeaveSpace` to the claim gate canonical Chat object requirement.
  - Replaced a broad admin-console `/configured/i` assertion with an exact `configured` text assertion so `not_configured` cannot satisfy the test.
- Fallback specialist reviews were used for architecture/docs/backend slices before final integration.

## Non-claims and boundaries

- No production release was published.
- No live infrastructure, provider tenant, credential, migration apply, destructive restore, or provider write operation was performed.
- No raw provider payload, token, tenant URL, provider-internal ID, or secret was added to evidence.
- No provider is claimed customer-ready unless it reaches `release_ready` with named evidence.
- No broad Weaver availability, per-user PA runtime availability, or unrestricted agent/tool execution is claimed.

## Sprint 22 readiness and handoff

Sprint 22 can start from a stable Sprint 21 foundation:

- Use the eight reality levels exactly; do not reintroduce stale readiness names.
- Treat `release_ready` as the only customer/member-ready level.
- Build executable provider-lab proof for the named free/self-hosted targets instead of weakening Sprint 21 gates.
- Keep provider-lab evidence support-safe and redacted by default.
- Promote providers only by moving through evidence-backed `live_read`, `live_write`, migration dry-run/apply, rollback, and release gates.

Next autonomous action after PR #655 has green GitHub CI: merge PR #655, close issues #618–#622 with evidence comments, and close the Sprint 21 milestone.
