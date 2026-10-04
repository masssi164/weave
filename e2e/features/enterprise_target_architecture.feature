Feature: Enterprise target architecture evidence spine

  # Historical tag names are retained for mapping continuity. The current release
  # contract is the pinned 2026-10 product consolidation profile.

  @enterprise-target-decision-lock
  Scenario: The approved consolidation release governs architecture evidence
    Given the pinned specification corpus contains the accepted #1470 release profile
    When Weave records implementation and acceptance evidence
    Then separate generated User and Admin APIs, native Matrix, one active provider per organization and module, and a permission-preserving Files replacement govern current work
    And deferred public DAV, server Matrix facade, Calls, and private Runner work cannot become current release gates

  @enterprise-target-open-standard-northbound
  Scenario: Current client boundaries separate generated HTTP, native Matrix, and private adapters
    Given a member uses Files or Calendar and an entitled Weaver uses a curated MCP tool
    When those operations cross the product boundary
    Then Files and Calendar use the generated User HTTP client with current resource authorization
    And Admin uses a separate generated Admin client and an admin identity
    And Flutter and Weaver retain their native Matrix integrations with separate sessions
    And WebDAV and CalDAV remain southbound provider mechanisms without a public DAV release gate
    And MCP has no provider or IAM administrator credential

  @enterprise-target-openapi-control-plane-only
  Scenario: Separate User and Admin OpenAPI artifacts govern current HTTP access
    Given Server code owns the User and Admin HTTP implementation
    When Flutter, Admin UI, MCP, and product E2E generate their consumers
    Then each consumer uses the appropriate generated operations and transport models
    And the Server enforces User/Admin, organization, and resource authorization
    And OpenAPI generation does not automatically expose operations as MCP tools
    And no proprietary chat message REST API replaces native Matrix

  @enterprise-target-no-transitional-compatibility
  Scenario: Superseded architecture stays historical while current safeguards remain
    Given prior DAV, Matrix-facade, and Runner plans remain available as historical records
    When a current release scenario is mapped to executable evidence
    Then superseded architecture text is not cited as active proof
    And authorization, privacy, permission, data integrity, and recovery assertions remain mapped
    And production schema evolution keeps one authority without an unreviewed fallback

  @enterprise-target-boundary-gate
  Scenario: Server boundary drift fails before broad package migration
    Given the current server still has transitional broad packages
    When the architecture gate scans canonical domain and public delivery contracts
    Then domain packages cannot import delivery, provider, runtime, DTO, or mutable storage implementation layers
    And public delivery contracts cannot import concrete provider adapters directly
    And protocol and MCP projections cannot import concrete provider adapters directly
    And member native setup and MCP contracts cannot expose provider URLs, tenant IDs, SecretRefs, app passwords, bearer tokens, raw diagnostics, or downstream payloads

  @enterprise-target-e2e-spine
  Scenario: Target architecture scenarios stay mapped to support-safe evidence
    Given the enterprise target is delivered through scoped PRs
    When a PR changes persistence, projections, provider switching, Matrix, MCP, Weaver, client/native boundaries, or cleanup paths
    Then the PR updates the mapped product-language E2E spine or records why no product-visible evidence changed
    And support-safe evidence excludes secrets, raw provider payloads, credential-bearing locations, private operator paths, and member content

  @enterprise-target-persistence-foundation
  Scenario: Weave-owned strategic state has one gated relational persistence foundation
    Given Admin Console provider selections and product profile overrides are strategic Weave-owned mutable state
    When the Server composes its production persistence authority
    Then explicit code-first JPA entities define provider selections and product profile overrides
    And one schema initializer applies versioned Flyway migrations and validates Hibernate mappings before serving
    And read/write ordering and restart recovery are proven without a selectable file-store fallback
    And H2-only evidence is not claimed as PostgreSQL production readiness

  @enterprise-target-audit-persistence-foundation
  Scenario: Support-safe audit events gain a gated relational persistence foundation
    Given support-safe audit events are append-only control-plane evidence for provider and policy decisions
    When the Server composes the JPA audit authority
    Then an explicit code-first JPA entity defines the audit-event model and tenant idempotency uniqueness
    And no file-backed audit publisher is composed as a fallback
    And retrying the same audit event is safe while conflicting idempotency reuse fails closed without leaking database details

  @enterprise-target-migration-evidence-persistence-foundation
  Scenario: Provider-switch migration run evidence gains a gated relational persistence foundation
    Given provider-switch dry-run and apply-gate evidence determines whether no-drift claims may proceed
    When the Server composes the JPA migration-evidence authority
    Then an explicit code-first JPA entity defines migration-run evidence keyed by run and domain
    And no file-backed migration-evidence repository is composed as a fallback
    And restart recovery preserves support-safe object counts, artifact refs, audit refs, and expiration behavior without enabling provider-switch apply

  @enterprise-target-provider-switch-no-drift-foundation
  Scenario: Provider replacement dry-run remains a prerequisite rather than cutover proof
    Given provider selections, product profile overrides, audit events, and migration run evidence have persistence foundations
    When an admin computes an offline provider replacement dry-run
    Then Weave records a support-safe baseline snapshot, switch plan, content counts, and read-model comparison
    And a Files switch remains blocked until real target readback preserves all data, stable references, and effective permissions
    And this offline dry-run does not claim activation, rollback, or restore success
