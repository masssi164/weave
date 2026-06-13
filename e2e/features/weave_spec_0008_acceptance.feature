@weave-spec-0008
Feature: WEAVE-SPEC-0008 acceptance

  weave.test topology truth, local evidence lanes, and live-claim boundaries.

  @weave-spec-0008-weave-test-canonical-url
  Scenario: weave.test remains the canonical local dogfood URL truth
    Given WEAVE-SPEC-0008 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0008_WEAVE_TEST_CANONICAL_URL
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers weave.test canonical URL
    And the catalog records the bounded domains operator-release, admin-health-ops

  @weave-spec-0008-local-evidence-does-not-claim-live-runtime
  Scenario: Local dogfood evidence blocks live-runtime claims until runtime proof exists
    Given WEAVE-SPEC-0008 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0008_LOCAL_EVIDENCE_DOES_NOT_CLAIM_LIVE_RUNTIME
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers offline evidence boundary
    And the catalog records the bounded domains operator-release, provider-portability, decisions-evidence

  @weave-spec-0008-functional-requirements
  Scenario: Local dogfood topology functional requirements are scenario-backed
    Given WEAVE-SPEC-0008 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0008_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected
