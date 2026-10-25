# Implementation brief: share the ordinary product baseline across qualification and Dogfood

Status: planning only; implementation and runtime evidence are pending.
Task: [#1703](https://github.com/masssi164/weave/issues/1703).
This is a bounded implementation handoff, not a second product specification or a release-readiness record.

## Validated starting point

- Implementation: `e5d4f9736bc20d24ce3e80393be199c54e48b2c7`; tree: `4616bed6a53a9363aec51371732eea8455aac604`.
- Accepted corpus: `7129075b7bf3f9f3763ec71a907395b007604c6f`, as pinned by `specs/weave-specs.lock.json`.
- Target lane: `dev`. Verify current source/spec and review only relevant deltas before coding.
- The linked issue owns this task's acceptance checklist; the pinned corpus owns product meaning. Update the issue and this handoff together when scope changes. Do not infer new authority from this plan.

## Governing requirements

- [steering/devops-conformance.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/steering/devops-conformance.md)
- [architecture/adr/0022-resettable-compose-dogfood-and-direct-e2e.md](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/architecture/adr/0022-resettable-compose-dogfood-and-direct-e2e.md)
- [acceptance/features/delivery-lane-validation.feature](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/acceptance/features/delivery-lane-validation.feature)
- [acceptance/features/human-testing-readiness.feature](https://github.com/masssi164/weave-specs/blob/7129075b7bf3f9f3763ec71a907395b007604c6f/acceptance/features/human-testing-readiness.feature)

#1666 first-use/preparation; bounded implementation follow-up to #892 member-flow parity and #1564/#1566 CI/native work; #1592 collaboration; #1470/#1481 release acceptance.

## Observed problem

Exact commit/tree/spec binding, default-Space bootstrap and ordinary deployment/physical functional smoke already exist. The remaining gap is separately maintained product configuration and different app startup paths between isolated qualification and ordinary Dogfood. The previously missing ordinary Calendar binding in #1666 demonstrates this failure class; duplicated settings alone do not prove a current broken binding.

Current compose.dogfood.yaml and compose.e2e.yaml duplicate Files/Calendar binding bootstrap; E2E hardcodes the Calendar adapter while Dogfood uses the configured selector. application-dogfood.yml and application-e2e.yml duplicate ordinary defaults. E2E also enables scoped proof instrumentation and optional runtime test coverage, uses a context principal-claim override and mounts a context-membership seed.

Native dual-iOS qualification launches integration_test/dual_ios_collaboration_test.dart; ordinary smoke launches lib/main.dart with direct discovered routing. Both use real application behavior. The fault relay forwards encrypted traffic for real interruption tests; it is not a mocked product server. Native Files uses filesystem storage in both inspected profiles; do not misreport optional runtime S3 instrumentation as a Files-provider difference.

## Implementation sequence

1. Extract/derive shared ordinary product defaults and Files/Calendar bootstrap through the existing Compose/configuration authority. Compare effective provider selection, auth/audience semantics, authorization/bootstrap, storage/schema and required feature behavior.

2. Keep a small explicit set of justified environment differences: origins/ports, namespaces/resources, secret references, TLS and persistence lifecycle. Treat optional test instrumentation separately; never broadly ignore authentication or permission differences.

3. Reuse the existing ordinary functional-smoke operations inside the current isolated testApp lifecycle, using lib/main.dart, normal discovery, independent real browser PKCE sessions, supported grants and direct configured routing.

4. Ensure test-only actor/session inputs, context authorization seeds or relays cannot grant access or substitute for the ordinary-baseline result. Retain them for their separately identified fault/optional integration coverage where appropriate.

5. Reuse DefaultSpacePreparationJourney's existing supported bootstrap and generated conditional Space grants. Keep macOS, concurrent dual-iOS collaboration/fault recovery, deployment smoke, retained physical-state proof and cleanup.

6. Extend existing evidence only as necessary to distinguish ordinary isolated baseline, instrumented integration, persisted Dogfood and physical/HIL outcomes. Do not create another pipeline or manifest authority.

## Bounded starting paths

- `infra/weave-workspace/compose.yaml`
- `infra/weave-workspace/compose.dogfood.yaml`
- `infra/weave-workspace/compose.e2e.yaml`
- `infra/weave-workspace/scripts/compose_env.py`
- `infra/weave-workspace/scripts/render_config.py`
- `infra/weave-workspace/tests/operator_environment_contract_test.py`
- `server/src/main/resources/application-dogfood.yml`
- `server/src/main/resources/application-e2e.yml`
- `gradle/scripts/prepare_test_app_context.py`
- `gradle/tasks/test-app.sh`
- `tools/dogfood_functional_smoke.py`
- `client/tool/run_native_qualification.py`
- `client/tool/run_dual_ios_collaboration.py`
- `weave-product-e2e/src/main/java/com/massimotter/weave/e2e/DefaultSpacePreparationJourney.java`

Read root and applicable scoped `AGENTS.md` and inspect these owners before editing. Generated outputs must come from existing generation tasks. Narrow any additional edit to the diagnosed/accepted boundary; this list does not authorize a general refactor.

## Acceptance and verification

Use the complete checkable acceptance in [issue #1703](https://github.com/masssi164/weave/issues/1703). Do not close it from this planning change.

Run meaningful mutation cases for the effective configuration, current Compose/profile rendering checks, `infraStatic`, relevant product-flow/functional-smoke tests, `testAppContract` and `releaseEvidenceCheck`. Any change to normative evidence acceptance requires a linked canonical corpus change before claiming conformance.

The implementation requires its scoped independent review and relevant deterministic gates before review readiness. The final candidate retains existing exact-source qualification, deployment and physical-preparation requirements; human acceptance is separate. No runtime test, physical inspection or successful repair is claimed by this document.

## Dependencies and scope limits

Parallel implementation with #1704; both join the existing final qualification. #1666 owns live test-account preparation.

Can start alongside Chat diagnosis. Final integrated proof joins the two changes under existing exact-candidate rules. #1666 retains ownership of incomplete disposable-account setup; do not use a source refactor to imply that live preparation is complete.

No replacement CI pipeline, build-once/Candidate Cut architecture, new release manifest, weaker qualification, shared live database, fake sessions, owner reset or automatic recovery approval. This issue does not by itself diagnose the original Chat defect.

## First implementation action and stop point

Validate the baseline, read the owning code and take the first item of the implementation sequence. Reuse this source-pinned brief; do not restart a whole-project history audit. Stop when this issue's acceptance, required checks and scoped review are satisfied. Record a concrete blocker if required live evidence or a material product decision is unavailable; continue unaffected work within scope.
