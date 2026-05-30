Feature: Product-ready sovereign collaboration setup

  Product readiness is a waterfall contract: governance and architecture evidence
  come first, then control-plane dry-runs and provider readiness, then guarded
  Weaver enablement, and only then member-visible provider-neutral work.

  @weave-product-readiness-waterfall
  Scenario: Admin bootstraps organization, validates domains, configures providers, enables Weaver, and member works provider-neutrally
    Given an organization owner signs in through Keycloak
    When the owner reviews the domain registry
    And the owner runs Keycloak desired-state dry-run
    And the owner configures Spaces and domain bindings
    And the owner reviews provider readiness for chat, files, documents, calendar, boards, and calls
    And the owner attempts provider apply before migration reports exist
    Then provider apply is blocked
    When the owner reviews migration dry-run, lossy report, conflict report, rollback boundary, and member impact preview
    Then provider apply can be approved only by an authorized role
    When the owner enables Weaver for a group
    And approves selected tools for that group
    And a member opts in to Weaver
    Then the member sees only approved Weave domain tools
    And the member sees provider-neutral domain states
    And no raw provider tokens or secrets are exposed
