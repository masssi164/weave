# Enterprise release foundation

Status: active enterprise dogfood and human-testing release contract.

This page defines the professional release spine for Weave. It deliberately separates product validation from admin/control-plane work: **Live Stack E2E is evidence**, not an Admin Portal feature. Admin/operator surfaces own setup, policy, readiness, and support-safe remediation; member journeys stay provider-neutral and are proved through the acceptance contract.

The machine-readable contract lives at `release/enterprise-release-gates.json` and is checked by `tools/release_gate_check.py` through `./gradlew releaseEvidenceCheck`.

## Operating principles

- Delivery is ordered `dev` → isolated candidate E2E → `dogfood` → protected iOS distribution → physical acceptance → `main`.
- No main or `humanTestingReady=true` claim without an exact-candidate readiness manifest in state `ready`.
- Current-surface collaboration, deployment, distribution, in-place session upgrade, and physical-iPhone VoiceOver gates are mandatory and cannot be waived into a ready state.
- Provider-specific IDs, raw endpoints, SecretRefs, credential-bearing URLs, downstream bodies, and private live logs stay out of public/support artifacts.
- Weaver remains governed, opt-in, capability-whitelisted, audited, and default-disabled; it must not be used to bypass release evidence.
- The `weave-co-leader` orchestrates cross-domain delivery; specialist agents implement scoped slices, while release evidence remains deterministic and human-reviewable.

## Enterprise lanes

### `pr-safe-ci`

Purpose: fast deterministic confidence before merge.

Required gates:

- `gradle-ci` runs `./gradlew ci` and uploads sanitized `build/evidence/ci-summary.json` plus docs artifacts.
- `spec-corpus-conformance` checks out and lints the exact commit pinned by `specs/weave-specs.lock.json`.
- `release-notes-label-check` enforces exactly one release-notes label for PRs.

This lane must stay PR-safe: no real provider credentials, no destructive live runner reset, no public release promotion, and no claim that runtime evidence was collected.

### `release-candidate-live-evidence`

Purpose: prove the candidate on the dedicated self-hosted live runner with credentials and a real local stack.

Required gates:

- `credentialed-live-stack-e2e` via `.github/workflows/live-stack-e2e.yml`.
- `three-user-live-collaboration` runs twice with disposable author, collaborator, and outsider identities in a fully isolated namespace.
- The same isolated lane runs real missing-capability, expired-token, and Matrix-session-revocation probes before the client suite. The client wrong-workspace assertions and the independent `isolated-authorization.json` artifact must agree before authorization can pass.

Required artifacts:

- `weave-live-stack-acceptance-evidence/acceptance-summary.md`
- `weave-live-stack-acceptance-evidence/scenario-mapping-results.json`
- `weave-live-stack-acceptance-evidence/evidence-markers.json`
- `weave-live-stack-acceptance-evidence/release-evidence-manifest.json`
- `weave-live-stack-acceptance-evidence/human-testing-automated-evidence.json`
- `weave-live-stack-acceptance-evidence/isolated-identities.json`
- `weave-live-stack-acceptance-evidence/isolated-authorization.json`
- `weave-live-stack-acceptance-evidence/isolated-calendar-outage.json`
- `weave-live-stack-acceptance-evidence/isolated-cleanup.json`

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
- `PROVIDER_REALITY_RESULT`
- `MULTI_USER_AUTH_SHELL_RESULT`
- `MULTI_USER_HOME_RESULT`
- `MULTI_USER_CHAT_RESULT`
- `MULTI_USER_FILES_RESULT`
- `MULTI_USER_CALENDAR_RESULT`
- `MULTI_USER_SETTINGS_PROFILE_RESULT`
- `MULTI_USER_FAILURE_CONTAINMENT_RESULT`
- `MULTI_USER_AUTHORIZATION_RESULT`
- `MULTI_USER_CLEANUP_RESULT`

The live lane validates product flows. It must not become a junk drawer for admin/control-plane assertions. Admin/provider readiness can be part of the product evidence only when it is consumed through stable backend-owned facades and support-safe member/operator states.

### `persistent-dogfood-verification`

Purpose: run `persistent-dogfood-deployment` for the accepted candidate under the shared non-cancelling lock. The deployment runs twice, uses non-destructive operator checks rather than the static-user smoke suite, verifies normalized Compose-model, Keycloak-reconciliation, and runtime idempotency, records cached provider health, and proves that the single persistent human member, captured Mailpit data, TLS identity, and existing session state were not reset. Persistent dogfood does not retain disposable automation identities.

### `ios-dogfood-distribution`

Purpose: run `ios-dogfood-distribution` only after the exact candidate's persistent deployment succeeds. `.github/workflows/ios-dogfood.yml` is triggered by that successful workflow result, verifies the earlier isolated E2E run for the same commit, builds immutable diagnostics, and uploads through the protected `ios-dogfood` environment. A waiting environment review is `blocked`, not success.

### `physical-human-acceptance`

Purpose: close `physical-iphone-voiceover` and `human-testing-readiness-manifest` after the candidate is installed in place. `.github/workflows/human-testing-readiness.yml` records support-safe physical-device evidence and validates `human-testing-readiness.json`. Simulator evidence is functional evidence only and cannot satisfy this lane.

### `release-promotion`

Purpose: turn evidence into a reviewable release candidate decision.

Required gates:

- `release-draft-review` creates a draft release from generated release notes and review artifacts.
- `release-owner-signoff` records the release owner decision with commit, artifact links, blocker/waiver if any, rollback note, and owner.

Promotion must cite the exact-candidate ready manifest. A successful PR-safe CI, isolated E2E, deployment, or TestFlight upload alone is not enough.

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

Use the local/CI-safe readiness check before creating or promoting an RC tag. The latest published audit is [`v0.1.0-rc.3`](release-v0.1-rc3-evidence.md); the command below is an example shape and each promotion must pass explicit candidate values. Current post-RC3 release readiness is still blocked by #591 until actual manual assistive-technology evidence (or an accepted release-owner scope split) exists:

```sh
./gradlew releaseReadinessCheck \
  -PcandidateVersion=<candidate-version> \
  -PcandidateTag=<candidate-tag> \
  -PcandidateCommit=<sha>
# or pass explicit evidence paths when reviewing downloaded artifacts:
python3 tools/release_readiness_check.py \
  --candidate-version <candidate-version> \
  --candidate-tag <candidate-tag> \
  --candidate-commit <sha> \
  --ci-summary build/evidence/ci-summary.json \
  --live-evidence-dir weave-live-stack-acceptance-evidence \
  --blockers-json build/evidence/release-blockers.json \
  --human-testing-readiness-manifest build/evidence/human-testing-readiness.json \
  --json
```

The command does not publish a release, create a tag, call providers, or read live logs. It validates only support-safe summaries and pointers:

- clean version/tag/commit inputs;
- release notes have the required sections and at least one candidate entry;
- sanitized CI summary exists, matches the candidate commit, and includes the release evidence gate;
- release lane and offline evidence pointers stay present;
- Live Stack E2E `release-evidence-manifest.json` is credentialed runtime evidence for the same commit and contains all required markers;
- `release-blocker` issue evidence is supplied and has no open blockers;
- the exact-candidate human-testing readiness manifest evaluates to `ready` with no mandatory blocker.

If the CI summary is absent, the tool writes a local pointer under `build/evidence/rc-readiness/` but still blocks readiness; generated pointers are not a substitute for green CI. Historical scoped waivers remain visibly `waived`, but they cannot replace the mandatory human-testing readiness manifest.

### RC promotion workflow

1. Merge the candidate through `dev` CI with the pinned corpus and PR-safe checks green.
2. Promote `dev` to `dogfood` only after the isolated three-user suite passes twice and cleans its namespace.
3. Let `Test Stack Deploy` update and verify persistent dogfood twice without changing the human member.
4. Let the successful deployment trigger the protected `iOS Dogfood` candidate build and TestFlight upload.
5. Install the build in place and complete the protected physical-iPhone VoiceOver/session/navigation acceptance workflow.
6. Export a support-safe release-blocker summary and the final `human-testing-readiness.json` artifact.
7. Run `tools/release_readiness_check.py` for the exact candidate and record the result with rollback notes.
8. Only a `ready` result may proceed to `main`, tagging, or a human-testing-ready claim.

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
