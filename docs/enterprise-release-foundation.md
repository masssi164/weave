# Enterprise release foundation

Status: Sprint 6 enterprise foundation, source-backed release engineering contract.

This page defines the professional release spine for Weave. It deliberately separates product validation from admin/control-plane work: **Live Stack E2E is evidence**, not an Admin Portal feature. Admin/operator surfaces own setup, policy, readiness, and support-safe remediation; member journeys stay provider-neutral and are proved through the acceptance contract.

The machine-readable contract lives at `release/enterprise-release-gates.json` and is checked by `tools/release_gate_check.py` through `./gradlew releaseEvidenceCheck`.

## Operating principles

- `main` is protected and release-candidate source of truth; use short-lived PR branches only.
- No v0.1 release-candidate promotion without green credentialed Live Stack E2E on the release-candidate head, or an explicit release-owner waiver.
- A waiver is not a green build. It must name the exact blocker, commit, owner, expiry/next action, and compensating evidence.
- Provider-specific IDs, raw endpoints, SecretRefs, credential-bearing URLs, downstream bodies, and private live logs stay out of public/support artifacts.
- Weaver remains governed, opt-in, capability-whitelisted, audited, and default-disabled; it must not be used to bypass release evidence.
- The `weave-co-leader` orchestrates cross-domain delivery; specialist agents implement scoped slices, while release evidence remains deterministic and human-reviewable.

## Enterprise lanes

### `pr-safe-ci`

Purpose: fast deterministic confidence before merge.

Required gates:

- `gradle-ci` runs `./gradlew ci` and uploads sanitized `build/evidence/ci-summary.json` plus docs artifacts.
- `release-notes-label-check` enforces exactly one release-notes label for PRs.

This lane must stay PR-safe: no real provider credentials, no destructive live runner reset, no public release promotion, and no claim that runtime evidence was collected.

### `release-candidate-live-evidence`

Purpose: prove the candidate on the dedicated self-hosted live runner with credentials and a real local stack.

Required gate:

- `credentialed-live-stack-e2e` via `.github/workflows/live-stack-e2e.yml`.

Required artifacts:

- `weave-live-stack-acceptance-evidence/acceptance-summary.md`
- `weave-live-stack-acceptance-evidence/scenario-mapping-results.json`
- `weave-live-stack-acceptance-evidence/evidence-markers.json`
- `weave-live-stack-acceptance-evidence/release-evidence-manifest.json`

Required runtime markers for the v0.1 dogfood candidate:

- `AUTH_RESULT`
- `PROFILE_RESULT`
- `CHAT_RESULT`
- `MATRIX_RESULT`
- `E2EE_RESULT`
- `FILES_RESULT`
- `PROVIDER_STACK_RESULT`
- `CALENDAR_RESULT`
- `BOARDS_RESULT`

The live lane validates product flows. It must not become a junk drawer for admin/control-plane assertions. Admin/provider readiness can be part of the product evidence only when it is consumed through stable backend-owned facades and support-safe member/operator states.

### `release-promotion`

Purpose: turn evidence into a reviewable release candidate decision.

Required gates:

- `release-draft-review` creates a draft release from generated release notes and review artifacts.
- `release-owner-signoff` records the release owner decision with commit, artifact links, blocker/waiver if any, rollback note, and owner.

Promotion must cite the live evidence run or the explicit waiver. A successful PR-safe CI run alone is not enough.

## Evidence artifact contract

Every Live Stack E2E artifact directory must include a support-safe manifest:

- schema version and generation time;
- source lane and workflow identity;
- commit under test;
- run id, run attempt, and run URL when available;
- acceptance artifact file list;
- support-safe exclusions;
- RC rule reminder.

This makes evidence portable: a release owner can inspect one artifact directory and know what commit, run, lane, and contract it represents without reading private runner logs.

## Product/context alignment

Sprint 6 changes must keep these layers coherent:

- Gherkin scenario first in `e2e/features`.
- Mapping in `e2e/scenario_mappings.json` with stable evidence markers.
- Executable proof in the smallest appropriate lane:
  - PR-safe unit/contract/static checks for deterministic boundaries;
  - admin/control-plane CI for setup, policy, and readiness APIs;
  - Live Stack E2E only for credentialed product journeys.
- Documentation in `docs/product-acceptance-flows.md`, `docs/acceptance-contracts.md`, and this enterprise release page.
- Release evidence checks in `./gradlew releaseEvidenceCheck` so docs and machine-readable gates drift together.

## Sprint 6 implementation priority

1. Keep #360 honest: merge the current capability fix only with green PR checks, rerun Live Stack E2E on `main`, and update #360 with sanitized artifacts or exact blocker.
2. Land the enterprise release contract: lanes, gates, manifest, support-safe checks, and docs wired into `releaseEvidenceCheck`.
3. Split future work by evidence lane instead of feature excitement:
   - identity/provider dry-run ops in admin/control-plane CI;
   - context/member flows in acceptance mappings and client/server tests;
   - credentialed product journeys in Live Stack E2E.
4. Add provider-ops depth only after the release spine can prove or block a candidate without ambiguity.

## What this foundation prevents

- Calling an RC green because offline tests passed while credentialed E2E failed.
- Treating Admin Portal work as the cause or solution for every E2E failure.
- Uploading raw live logs as “evidence”.
- Expanding the milestone with unrelated UX/provider work before the release gate is reliable.
- Losing traceability between feature scenarios, runtime markers, release notes, and promotion decisions.
