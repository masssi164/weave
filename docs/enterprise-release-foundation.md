# Enterprise release foundation

Status: historical release-hardening design; superseded for active `dev` and
`dogfood` development by ADR 0022 in the pinned specification corpus.

The workflow paths and readiness-manifest chain described below are not active
development gates. The current path is `Full Compose E2E` followed by direct,
resettable Dogfood Compose and a development-signed in-place iPhone update. This
page is retained only as input to a future production-hardening ADR; it must not
be used to block development human testing.

This page defines the professional release spine for Weave. It deliberately separates product validation from admin/control-plane work: **the Fresh product flow is evidence**, not an Admin Portal feature. Admin/operator surfaces own setup, policy, readiness, and support-safe remediation; member journeys stay provider-neutral and are proved through the acceptance contract.

The machine-readable contract lives at `release/enterprise-release-gates.json` and is checked by `tools/release_gate_check.py` through `./gradlew releaseEvidenceCheck`.

## Operating principles

- Delivery is ordered `dev` → isolated candidate E2E → `dogfood` → protected iOS distribution → physical acceptance → `main`.
- No main or `humanTestingReady=true` claim without an exact-candidate readiness manifest in state `ready`.
- Current-surface collaboration, deployment, distribution, in-place session upgrade, and physical-iPhone VoiceOver gates are mandatory and cannot be waived into a ready state. The readiness manifest binds the candidate-manifest digest, four immutable runtime images, live and fixture proof origins, and the tester-confirmed twenty-step physical protocol.
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

Purpose: prove the candidate on the dedicated self-hosted runner with a real
disposable stack while keeping human credentials and activation links in the
bounded `testApp` process.

Required gates:

- `test-app-product-flow-e2e` via `.github/workflows/live-stack-e2e.yml`;
  Flutter physical-device authentication is a separate AppAuth evidence gate.
- `testApp` proves real owner/collaborator/outsider invitations, Keycloak required actions in Chromium,
  fresh Authorization Code + PKCE sessions, two-pass Chat/Files/Calendar/Home/Profile collaboration,
  direct Synapse and canonical JPA/PostgreSQL state, backend/Synapse restart continuity, provider
  outage exactly-once recovery, callback replay idempotency, per-cell `private_key_jwt`, Spring AI
  MCP discovery/tool invocation, revoke/regrant, and exact namespace cleanup.

Required artifacts:

- `weave-live-stack-acceptance-evidence/weave-test-app-evidence.json`
- `weave-live-stack-acceptance-evidence/human-testing-live-automated-evidence.json`
- `weave-live-stack-acceptance-evidence/teardown-evidence.json`

The bounded runner also emits the support-safe `WEAVE_TEST_APP_RESULT` marker;
the JSON artifact remains the durable evidence authority.

Raw failure diagnostics stay private on the self-hosted runner and are never
promoted into the uploaded artifact.
- `weave-live-stack-acceptance-evidence/failure-diagnostics/health-checks/operator-check.txt`
- `weave-live-stack-acceptance-evidence/failure-diagnostics/support-bundle/weave-support-*.tar.gz`

Required support-safe marker and evidence fields for the v0.1 dogfood candidate:

- marker `WEAVE_TEST_APP_RESULT`;
- `activation=keycloak-required-actions-real-chromium`;
- `humanOAuth=authorization_code_pkce_s256`;
- `workloadOAuth=client_credentials_private_key_jwt`;
- `mcpTool=files.search`;
- `canonicalResourceSeen=true`;
- `revocationDenied=true`;
- `credentialsIncluded=false`, `actionLinksIncluded=false`, and `supportSafe=true`.

The live lane validates product flows. It must not become a junk drawer for admin/control-plane assertions. Admin/provider readiness can be part of the product evidence only when it is consumed through stable backend-owned facades and support-safe member/operator states.

### `persistent-dogfood-verification`

Purpose: run `persistent-dogfood-deployment` for the accepted candidate under the shared non-cancelling lock. The deployment runs twice, uses non-destructive operator checks, verifies the normalized Compose model, static Keycloak migration, and runtime idempotency, records cached provider health, and preserves the PostgreSQL, Mailpit, Caddy, and native Files volumes plus public TLS identity. It receives no human identity writer or realm-admin credential. The later OIDC gate proves the activated owner session through normal product boundaries.

### `ios-dogfood-distribution`

Purpose: run `ios-dogfood-distribution` only after the exact candidate's persistent deployment succeeds. `.github/workflows/ios-dogfood.yml` is triggered by that successful workflow result, verifies the earlier isolated E2E run, runs the five release-required shell tabs plus nested Profile on a fresh iPhone Simulator, binds the resulting `fixture-ui` artifact to the live provider evidence, builds immutable diagnostics, and uploads through the protected `ios-dogfood` environment. The separate Help surface is not claimed by this six-surface evidence contract. TestFlight and development-signed fallback jobs both depend on this Simulator gate. A waiting environment review is `blocked`, not success.

### `physical-human-acceptance`

Purpose: close `physical-iphone-voiceover` and `human-testing-readiness-manifest` after the candidate is installed in place. `.github/workflows/human-testing-readiness.yml` consumes the tester-confirmed physical-device evidence, then runs under the persistent dogfood runner lock to reverify the exact three running image identities and collect a new cached, support-safe provider-health snapshot immediately before validating `human-testing-readiness.json`. It never reuses the deployment-time snapshot as current health. Simulator evidence is functional evidence only and cannot satisfy this lane.

### `release-promotion`

Purpose: turn evidence into a reviewable release candidate decision.

Required gates:

- `release-draft-review` creates a draft release from generated release notes and review artifacts.
- `release-owner-signoff` records the release owner decision with commit, artifact links, blocker/waiver if any, rollback note, and owner.

Promotion must cite the exact-candidate ready manifest. A successful PR-safe CI, isolated E2E, deployment, or TestFlight upload alone is not enough.

## Evidence artifact contract

Every Fresh product-flow artifact directory must include a support-safe manifest:

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
- `weave-test-app-evidence.json` is exact-candidate, support-safe runtime
  evidence and contains the required protocol/result fields;
- `release-blocker` issue evidence is supplied and has no open blockers;
- the exact-candidate human-testing readiness manifest evaluates to `ready` with no mandatory blocker.

If the CI summary is absent, the tool writes a local pointer under `build/evidence/rc-readiness/` but still blocks readiness; generated pointers are not a substitute for green CI. Historical scoped waivers remain visibly `waived`, but they cannot replace the mandatory human-testing readiness manifest.

### RC promotion workflow

1. Merge the candidate through `dev` CI with the pinned corpus and PR-safe checks green.
2. Promote `dev` to `dogfood` only after `testApp` passes and cleans its
   namespace.
3. Let `Test Stack Deploy` update and verify persistent dogfood twice without changing the human member.
4. Let the successful deployment trigger the protected `iOS Dogfood` candidate build and TestFlight upload.
5. Install the build in place, perform every required physical-iPhone row, and submit the support-safe tester-confirmed protocol through `Physical iPhone Human Test`.
6. Feed that exact physical workflow run into `Human Testing Readiness`; the latter validates rather than creates human outcomes, reverifies the manifest-bound persistent runtime plus current provider health, and exports the schema-v3 manifest.
7. Run `tools/release_readiness_check.py` for the exact candidate and record the result with rollback notes.
8. Only a `ready` result may proceed to `main`, tagging, or a human-testing-ready claim.

## Product/context alignment

Sprint 6 changes must keep these layers coherent:

- Gherkin scenario first in `e2e/features`.
- Mapping in `e2e/scenario_mappings.json` with stable evidence markers.
- Executable proof in the smallest appropriate lane:
  - PR-safe unit/contract/static checks for deterministic boundaries;
  - admin/control-plane CI for setup, policy, and readiness APIs;
  - `testApp` for the automatic product journey and a separate physical-device
    AppAuth lane for interactive client sign-in.
- Documentation in `docs/product-acceptance-flows.md`, `docs/acceptance-contracts.md`, and this enterprise release page.
- Release evidence checks in `./gradlew releaseEvidenceCheck` so docs and machine-readable gates drift together.

## Implementation priority

1. Keep the release contract machine-checkable: lanes, gates, manifest, support-safe checks, and docs remain wired into `releaseEvidenceCheck`.
2. Split work by evidence lane:
   - Keycloak federation, user lifecycle, policy, and readiness in admin/control-plane CI;
   - context/member flows in acceptance mappings and client/server tests;
   - credential-free, process-bounded product journeys in `testApp`;
   - interactive AppAuth and assistive-technology evidence only on protected physical-device lanes.
3. Add collaboration-provider operations only after the release spine can prove or block a candidate without ambiguity.

## What this foundation prevents

- Calling an RC green because offline tests passed while the exact-candidate
  product flow or physical-device gate failed.
- Treating Admin Portal work as the cause or solution for every E2E failure.
- Uploading raw live logs as “evidence”.
- Expanding the milestone with unrelated UX/provider work before the release gate is reliable.
- Losing traceability between feature scenarios, runtime markers, release notes, and promotion decisions.
