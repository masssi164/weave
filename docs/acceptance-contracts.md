# Acceptance contracts, Gherkin, and ATDD workflow

Weave uses Gherkin as a product acceptance contract and CI evidence layer. It is
not a Cucumber theater layer: a scenario is reviewable only when it has a stable,
machine-readable mapping to executable evidence.

## Current E2E and acceptance contract

- Product scenarios live in `e2e/features/`. Critical runtime scenarios stay in
  `e2e/features/live_stack_app.feature`; release, admin/provider, operator, and
  spec-projection scenarios use focused feature files in the same directory.
- Stable scenario mappings live in `e2e/scenario_mappings.json`. Each mapping
  declares an `evidenceMode`: `live-runtime` markers must be observed in the
  credentialed Live Stack E2E log, while `offline-spec` markers prove checked-in
  executable/spec evidence and are not counted as live runtime observations.
- Professional suite structure lives in `e2e/suites/scenario_catalog.json`.
  It classifies every mapped scenario by suite, persona, bounded domain, test
  level, and assertion focus; execution lane is inherited from the referenced
  suite. `tools/e2e_structure_check.py` prevents unmapped, unclassified, or
  domain-blind scenarios from landing.
- The mapping guard is `test/live_stack_feature_mapping_test.dart`; the root
  `./gradlew acceptanceContract` gate also runs the suite-structure guard. The
  Dart mapping guard can be run directly with
  `dart run tool/acceptance_contract.dart guard`.
- Live CI writes audit artifacts to `weave-live-stack-acceptance-evidence`:
  - `acceptance-summary.md`
  - `gherkin-scenarios.json`
  - `scenario-mapping-results.json`
  - `evidence-markers.json`
  - `release-evidence-manifest.json`

The artifact includes sanitized marker summaries and a release-evidence manifest
that names the source lane, commit, workflow run metadata, required artifact
files, and RC promotion rule. It must not include raw secrets, tokens, cookies,
private keys, credential-bearing URLs, provider internals, downstream provider
bodies, or full live E2E logs.

Runtime summaries must keep the modes distinct: `live-runtime` markers may be
reported as seen/missing from a live log, and `offline-spec` markers must remain
offline/spec executable evidence even when their test output is present in the
same workflow log.

## ATDD/TDD rule

For new product behavior:

0. Create or update the repo-local spec/spec note when the slice changes product contracts, provider boundaries, auth/policy, or release evidence. Keep unresolved product-core questions as `[NEEDS CLARIFICATION: ...]` while the spec is `draft` or `proposed`.
1. Write or update the product-language Gherkin scenario first.
2. Add the scenario to `e2e/scenario_mappings.json` with an executable
   test path and evidence marker. The guard should be red until the executable
   evidence exists.
3. Add the scenario to `e2e/suites/scenario_catalog.json` with its suite,
   persona(s), bounded domain(s), test level, and assertion focus. Confirm that
   the selected suite's execution lane is correct for the scenario.
4. Drive the implementation with focused unit, provider, widget, integration, or
   backend tests. Do not push implementation details into the feature file.
4. Keep live-stack E2E sparse. It proves critical end-to-end product contracts
   only; lower-level tests carry the detailed technical coverage. Admin/provider
   setup and policy checks belong in backend/admin/control-plane CI unless the
   member/operator product journey explicitly consumes the stable facade state.
5. Run `./gradlew specContract` when specs or product contracts changed, and run `make offline-contract-test` before review. Use `make integration-test`
   only on the dedicated live-stack runner or another explicitly prepared local
   stack, keeping generated evidence sanitized.

## Feature-file language rules

Feature files should be readable by product, engineering, and screen-reader users:

- Use short scenario names and one behavior per scenario.
- Describe people and Weave product surfaces, not JWTs, HTTP routes, repositories,
  provider internals, selectors, or database state.
- Mention accessibility outcomes when they are part of the product contract, and
  map them to widget/provider tests instead of bloating live E2E.
- If a scenario has no deterministic executable mapping yet, keep it out of the
  checked-in feature file or add a mapping that intentionally fails until the
  test/evidence is implemented.
