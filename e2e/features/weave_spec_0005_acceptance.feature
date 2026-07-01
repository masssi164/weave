@weave-spec-0005
Feature: WEAVE-SPEC-0005 acceptance

  Space anchors for cross-domain context, guest bounds, and raw provider identifier exclusion.

  @weave-spec-0005-space-anchor-cross-domain
  Scenario: Space anchor links chat files boards meetings and decisions by canonical context
    Given WEAVE-SPEC-0005 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_SPACE_ANCHOR_CROSS_DOMAIN
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers canonical Space context
    And the catalog records the bounded domains spaces, chat, files, boards-tasks, meetings-calls, decisions-evidence

  @weave-spec-0005-guest-bounded-no-provider-ids
  Scenario: Guest Space access remains policy bounded without raw provider identifiers
    Given WEAVE-SPEC-0005 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_GUEST_BOUNDED_NO_PROVIDER_IDS
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers guest bounded access
    And the catalog records the bounded domains spaces, identity-idm, admin-health-ops

  @weave-spec-0005-intent
  Scenario: Spaces intent keeps organization context anchored across domains
    Given WEAVE-SPEC-0005 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_INTENT_SPACE_ANCHOR
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0005-scope
  Scenario: Spaces separate canonical cross-domain anchors from raw provider identifiers
    Given WEAVE-SPEC-0005 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0005-functional-requirements
  Scenario: Spaces functional requirements are scenario-backed
    Given WEAVE-SPEC-0005 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0005-release-impact
  Scenario: Spaces release impact requires anchor evidence before workspace claims
    Given WEAVE-SPEC-0005 defines Release and migration impact
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_RELEASE_IMPACT
    When a product or release claim depends on Release and migration impact
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0005-product-boundaries
  Scenario: Spaces product boundaries keep cross-domain anchors canonical and provider-id free
    Given WEAVE-SPEC-0005 defines Product boundaries
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0005_PRODUCT_BOUNDARIES
    When implementation or release evidence is reviewed against Product boundaries
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
