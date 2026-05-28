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

Failure-only support-safe diagnostics:

- `weave-live-stack-acceptance-evidence/failure-diagnostics/failure-summary.md`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/failure-summary.json`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/container-status.tsv`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/failed-markers.json`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/health-checks/operator-check.txt`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/support-bundle/weave-support-*.tar.gz`

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

On failure, diagnostics are additive and support-safe: container state, health/readiness output after redaction, failed or missing runtime markers, and a redacted support bundle reference. The workflow must not print or upload raw container logs as default evidence. If an operator deliberately enables private raw-log capture for deeper debugging, the destination must stay outside the uploaded evidence directory and remain a private runner/operator artifact.

This makes evidence portable: a release owner can inspect one artifact directory and know what commit, run, lane, and contract it represents without reading private runner logs.


## Repeatable RC readiness check

Use the local/CI-safe readiness check before creating or promoting an RC tag:

```sh
./gradlew releaseReadinessCheck \
  -PcandidateVersion=0.1.0-rc.1 \
  -PcandidateTag=v0.1.0-rc.1 \
  -PcandidateCommit=<sha>
# or pass explicit evidence paths when reviewing downloaded artifacts:
python3 tools/release_readiness_check.py \
  --candidate-version 0.1.0-rc.1 \
  --candidate-tag v0.1.0-rc.1 \
  --candidate-commit <sha> \
  --ci-summary build/evidence/ci-summary.json \
  --live-evidence-dir weave-live-stack-acceptance-evidence \
  --blockers-json build/evidence/release-blockers.json \
  --json
```

The command does not publish a release, create a tag, call providers, or read live logs. It validates only support-safe summaries and pointers:

- clean version/tag/commit inputs;
- release notes have the required sections and at least one candidate entry;
- sanitized CI summary exists, matches the candidate commit, and includes the release evidence gate;
- release lane and offline evidence pointers stay present;
- Live Stack E2E `release-evidence-manifest.json` is credentialed runtime evidence for the same commit and contains all required markers;
- `release-blocker` issue evidence is supplied and has no open blockers;
- any waiver is an explicit release-owner marker with owner, reason, exact candidate commit/tag, expiry, scoped gate, and compensating evidence.

If the CI summary is absent, the tool writes a local pointer under `build/evidence/rc-readiness/` but still blocks readiness; generated pointers are not a substitute for green CI. If credentialed Live Stack E2E or release blockers are waived, the JSON and Markdown output mark the check as `waived` rather than pretending it passed.

### RC promotion workflow

1. Prepare the candidate on protected `main`; do not create the RC tag yet.
2. Run `./gradlew ci` and keep the sanitized `build/evidence/ci-summary.json` artifact.
3. Review release notes or generate a draft release artifact; the GitHub release remains a draft/prerelease until signoff.
4. Dispatch `.github/workflows/live-stack-e2e.yml` on the candidate commit using the dedicated self-hosted runner and collect the `weave-live-stack-acceptance-evidence` artifact directory.
5. Export a support-safe release-blocker summary, for example open GitHub issues labeled `release-blocker`, to `build/evidence/release-blockers.json`.
6. Run `tools/release_readiness_check.py` with the exact candidate version, tag, commit, CI summary, Live Stack artifact, release notes, and blocker summary.
7. Record the result in the signoff issue with artifact links, rollback note, and release-owner decision.
8. Only after green or explicitly waived readiness should the release owner create the RC tag and publish the prerelease.

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
