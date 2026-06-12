@weave-spec-0004
Feature: WEAVE-SPEC-0004 acceptance

  Canonical domain registry, reality levels, capability naming, and provider-neutral states.

  @weave-spec-0004-domain-registry-reality-levels
  Scenario: Domain registry exposes capability reality levels without provider internals
    Given WEAVE-SPEC-0004 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_DOMAIN_REGISTRY_REALITY_LEVELS
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers capability reality levels
    And the catalog records the bounded domains admin-health-ops, provider-portability

  @weave-spec-0004-provider-neutral-capability-names
  Scenario: Provider-neutral capability names stay canonical across domain switches
    Given WEAVE-SPEC-0004 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_PROVIDER_NEUTRAL_CAPABILITY_NAMES
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers canonical capability names
    And the catalog records the bounded domains provider-portability, admin-health-ops

  @weave-spec-0004-intent
  Scenario: Domain registry intent makes capability truth canonical before claims
    Given WEAVE-SPEC-0004 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_INTENT_CANONICAL_REGISTRY
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0004-scope
  Scenario: Domain registry separates supported canonical fields from out-of-scope provider internals
    Given WEAVE-SPEC-0004 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0004-functional-requirements
  Scenario: Domain registry functional requirements are scenario-backed
    Given WEAVE-SPEC-0004 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0004-release-impact
  Scenario: Domain registry release impact blocks provider-neutral claims without registry evidence
    Given WEAVE-SPEC-0004 defines Release and migration impact
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_RELEASE_IMPACT
    When a product or release claim depends on Release and migration impact
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0004-product-boundaries
  Scenario: Domain registry product boundaries hide provider internals behind canonical capability state
    Given WEAVE-SPEC-0004 defines Product boundaries
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0004_PRODUCT_BOUNDARIES
    When implementation or release evidence is reviewed against Product boundaries
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
