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
  Scenario: Strategic control-plane state gains a gated relational persistence foundation
    Given Admin Console provider selections and product profile overrides are strategic Weave-owned mutable state
    When the relational store is explicitly enabled for the first persistence slices
    Then Flyway creates handwritten canonical tables for provider selections and product profile overrides
    And read/write parity and restart recovery are proven without deleting the current file-backed stores
    And H2-only evidence is not claimed as PostgreSQL production readiness

  @enterprise-target-audit-persistence-foundation
  Scenario: Support-safe audit events gain a gated relational persistence foundation
    Given support-safe audit events are append-only control-plane evidence for provider and policy decisions
    When JDBC audit storage is explicitly enabled for the audit persistence slice
    Then Flyway creates a handwritten canonical audit-event table with tenant idempotency uniqueness
    And the file-backed audit sink remains the default until #1019 completes parity, rollback, and operator migration evidence
    And retrying the same audit event is safe while conflicting idempotency reuse fails closed without leaking database details

  @enterprise-target-migration-evidence-persistence-foundation
  Scenario: Provider-switch migration run evidence gains a gated relational persistence foundation
    Given provider-switch dry-run and apply-gate evidence determines whether no-drift claims may proceed
    When JDBC migration evidence storage is explicitly enabled for the migration evidence persistence slice
    Then Flyway creates a handwritten canonical migration-run evidence table keyed by run and domain
    And the file-backed migration evidence store remains the default until #1019 completes import, rollback, and operator migration evidence
    And restart recovery preserves support-safe object counts, artifact refs, audit refs, and expiration behavior without enabling provider-switch apply

  @enterprise-target-provider-switch-no-drift-foundation
  Scenario: Provider replacement dry-run records no-drift evidence without provider semantics leaking northbound
    Given provider selections, product profile overrides, audit events, and migration run evidence have persistence foundations
    When an admin computes an offline provider replacement dry-run
    Then Weave records a support-safe baseline snapshot, switch plan, no-unaccounted-data-loss counts, and read-model comparison
    And member-facing capability states remain provider-neutral while apply and production default changes stay blocked
