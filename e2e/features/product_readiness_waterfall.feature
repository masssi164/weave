Feature: Product-ready sovereign collaboration setup

  Product readiness is a waterfall contract: governance and architecture evidence
  come first, then control-plane dry-runs and provider readiness, then guarded
  Agent Runtime Control, and only then member-visible provider-neutral work.

  @weave-product-readiness-waterfall
  Scenario: Admin bootstraps organization, validates domains, controls an entitled runtime cell, and members work provider-neutrally
    Given an organization owner signs in through Keycloak
    When the owner reviews the domain registry
    And the owner reviews protected Keycloak Identity Ops plan and verify evidence
    And the owner configures Spaces and domain bindings
    And the owner reviews provider readiness for chat, files, documents, calendar, boards, and calls
    And the owner attempts provider apply before migration reports exist
    Then provider apply is blocked
    When the owner reviews migration dry-run, lossy report, conflict report, rollback boundary, and member impact preview
    Then provider apply can be approved only by an authorized role
    When the owner grants the agent runtime entitlement through Keycloak
    And creates a disposable runtime cell for an entitled member
    Then the backend provisions a dedicated Keycloak workload identity for that cell
    And MCP accepts only a cell-bound workload token after exchange and backend context revalidation
    And MCP domain tool catalogs stay empty until each owning domain action contract is implemented
    When the owner deletes runtime state only
    Then the workload identity and encrypted runtime state are revoked without deleting provider data
    And the member sees provider-neutral domain states
    And no raw provider tokens or secrets are exposed
