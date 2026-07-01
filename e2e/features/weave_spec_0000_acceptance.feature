@weave-spec-0000
Feature: WEAVE-SPEC-0000 acceptance

  Spec corpus metadata, acceptance wiring, and evidence-mode separation for claim control.

  @weave-spec-0000-frontmatter-gates
  Scenario: Spec frontmatter declares acceptance features and evidence gates
    Given WEAVE-SPEC-0000 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_FRONTMATTER_GATES
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers frontmatter acceptance_features
    And the catalog records the bounded domains admin-health-ops, decisions-evidence

  @weave-spec-0000-product-claim-requires-scenario
  Scenario: Product claims require mapped Gherkin before merge
    Given WEAVE-SPEC-0000 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_PRODUCT_CLAIM_REQUIRES_SCENARIO
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers product claim scenario mapping
    And the catalog records the bounded domains decisions-evidence, operator-release

  @weave-spec-0000-intent
  Scenario: Spec framework intent stays reviewable before implementation
    Given WEAVE-SPEC-0000 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_INTENT_REVIEWABLE
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0000-scope
  Scenario: Spec framework separates in-scope delivery evidence from out-of-scope live claims
    Given WEAVE-SPEC-0000 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0000-reviewer-brief
  Scenario: Delivery lead briefs scoped reviewers without leaking unsupported claims
    Given WEAVE-SPEC-0000 defines US2 - Delivery lead briefs scoped reviewers safely
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_REVIEWER_BRIEF
    When a product or release claim depends on US2 - Delivery lead briefs scoped reviewers safely
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0000-functional-requirements
  Scenario: Spec functional requirements are traceable to acceptance artifacts
    Given WEAVE-SPEC-0000 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0000-product-boundaries
  Scenario: Spec product boundaries separate product claims from implementation evidence
    Given WEAVE-SPEC-0000 defines Product boundaries
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_PRODUCT_BOUNDARIES
    When implementation or release evidence is reviewed against Product boundaries
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0000-developer-reviewable-spec
  Scenario: Developer creates a reviewable spec before implementation work starts
    Given WEAVE-SPEC-0000 defines US1 - Developer creates a reviewable spec
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_DEVELOPER_REVIEWABLE_SPEC
    When implementation or release evidence is reviewed against US1 - Developer creates a reviewable spec
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0000-domain-model-contracts
  Scenario: Spec domain model and contracts stay linked to acceptance evidence
    Given WEAVE-SPEC-0000 defines Domain model and contracts
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0000_DOMAIN_MODEL_CONTRACTS
    When implementation or release evidence is reviewed against Domain model and contracts
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
