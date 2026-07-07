Feature: Enterprise target architecture evidence spine

  @enterprise-target-decision-lock
  Scenario: Enterprise hard-plan decisions are locked before implementation lanes expand
    Given the Enterprise Hard Plan is an active restructuring input
    When Weave records the target direction in repository architecture evidence
    Then the decision names the canonical domain kernel, standard projections, persistence, provider adapters, OpenAPI demotion, Matrix, MCP, and Weaver boundaries
    And implementation slices remain linked to bounded issues and gates instead of broad undocumented refactors

  @enterprise-target-boundary-gate
  Scenario: Server boundary drift fails before broad package migration
    Given the current server still has transitional broad packages
    When the architecture gate scans canonical domain and public delivery contracts
    Then domain packages cannot import delivery, provider, runtime, DTO, or mutable storage implementation layers
    And public delivery contracts cannot import concrete provider adapters directly

  @enterprise-target-e2e-spine
  Scenario: Target architecture scenarios stay mapped to support-safe evidence
    Given the enterprise target is delivered through scoped PRs
    When a PR changes persistence, projections, provider switching, Matrix, MCP, Weaver, client/native boundaries, or cleanup paths
    Then the PR updates the mapped product-language E2E spine or records why no product-visible evidence changed
    And support-safe evidence excludes secrets, raw provider payloads, credential-bearing locations, private operator paths, and member content

  @enterprise-target-persistence-foundation
  Scenario: Provider selections gain a gated relational persistence foundation
    Given Admin Console provider selections are strategic Weave-owned mutable state
    When the relational store is explicitly enabled for the first persistence slice
    Then Flyway creates handwritten canonical tables for provider selections
    And provider selection read/write parity and restart recovery are proven without deleting the current file-backed store
    And H2-only evidence is not claimed as PostgreSQL production readiness
