Feature: Sprint 8 domain control-plane evidence

  Sprint 8 acceptance proves that domain setup and provider-control-plane
  boundaries are executable product contracts, not provider-specific intent.

  @weave-sprint8-domain-control-plane-evidence
  Scenario: Admin validates domains, dry-runs identity, checks portability, and members see provider-neutral states
    Given an organization admin opens the Admin Console
    When the admin reviews canonical domain setup
    And runs a Keycloak desired-state dry-run
    And reviews domain-first readiness states
    And attempts provider switch without dry-run evidence
    Then the provider switch is blocked
    When the admin reviews a Boards portability dry-run report
    Then the member client still sees provider-neutral domain states only
    And Weaver remains disabled by default unless organization policy enables it
    And live-stack evidence is green, waived, or explicitly not required for this slice
