# Acceptance contracts, Gherkin, and ATDD workflow

Weave uses Gherkin as a product acceptance contract and CI evidence layer. It is
not a Cucumber theater layer: a scenario is reviewable only when it has a stable,
machine-readable mapping to executable evidence.

## Current frontend live-stack contract

- Product scenarios live in `e2e/features/live_stack_app.feature`.
- Stable scenario mappings live in `e2e/scenario_mappings.json`.
- The mapping guard is `test/live_stack_feature_mapping_test.dart` and can also
  be run directly with `dart run tool/acceptance_contract.dart guard`.
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

## ATDD/TDD rule

For new product behavior:

1. Write or update the product-language Gherkin scenario first.
2. Add the scenario to `e2e/scenario_mappings.json` with an executable
   test path and evidence marker. The guard should be red until the executable
   evidence exists.
3. Drive the implementation with focused unit, provider, widget, integration, or
   backend tests. Do not push implementation details into the feature file.
4. Keep live-stack E2E sparse. It proves critical end-to-end product contracts
   only; lower-level tests carry the detailed technical coverage. Admin/provider
   setup and policy checks belong in backend/admin/control-plane CI unless the
   member/operator product journey explicitly consumes the stable facade state.
5. Run `make offline-contract-test` before review. Use `make integration-test`
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
