Feature: Enterprise target architecture evidence spine

  @enterprise-target-decision-lock
  Scenario: Enterprise hard-plan decisions are locked before implementation lanes expand
    Given the Enterprise Hard Plan is an active restructuring input
    When Weave records the target direction in repository architecture evidence
    Then the decision names the canonical domain kernel, standard projections, persistence, provider adapters, OpenAPI demotion, Matrix, CalDAV, WebDAV, WebRTC, MCP, People/Contacts, and Weaver boundaries
    And implementation slices remain linked to bounded issues and gates instead of broad undocumented refactors

  @enterprise-target-open-standard-northbound
  Scenario: Northbound standards stay separate from southbound providers
    Given Weave exposes a collaboration domain to clients, native OS integrations, or Weaver
    When the domain has a durable data-plane protocol
    Then Identity uses OIDC or OAuth2 for auth, claims, and tokens
    And Files uses the Weave WebDAV projection under "/dav/files"
    And Calendar uses the Weave CalDAV and iCalendar projection under "/caldav"
    And Chat uses a Matrix Client-Server API projection instead of Slack or Teams as northbound product protocols
    And Calls uses MatrixRTC Profile 0 signaling and WebRTC media without a member Calls API
    And Agents use MCP over Weave domain capabilities
    And Admin uses OpenAPI or REST as the control plane

  @enterprise-target-openapi-control-plane-only
  Scenario: OpenAPI does not become the normal collaboration data plane
    Given Files, Calendar, or Chat has a standard northbound projection
    When a member client performs normal domain data access
    Then OpenAPI may provide manifest, setup, readiness, revoke, provider selection, device credentials, generated models, and support-safe admin evidence
    And OpenAPI must not be the fallback list, read, write, event, or message data plane
    And provider APIs remain southbound behind Weave adapters

  @enterprise-target-no-transitional-compatibility
  Scenario: Transitional behavior is not preserved as architecture
    Given a vertical domain slice has a target standard projection or domain use case
    When the slice replaces historical JSON, OpenAPI data-plane, provider-shaped, or route-mirrored behavior
    Then the old path is deleted, blocked, or fenced as fixture/import-only evidence
    And strategic runtime state has one JPA authority without a selectable file-store fallback

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
  Scenario: Strategic control-plane state gains a gated relational persistence foundation
    Given Admin Console provider selections and product profile overrides are strategic Weave-owned mutable state
    When the Server composes its production persistence authority
    Then explicit code-first JPA entities define provider selections and product profile overrides
    And the one-shot Server schema initializer converges and fingerprints that entity model before serving Hibernate validation
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
  Scenario: Provider replacement dry-run records no-drift evidence without provider semantics leaking northbound
    Given provider selections, product profile overrides, audit events, and migration run evidence have persistence foundations
    When an admin computes an offline provider replacement dry-run
    Then Weave records a support-safe baseline snapshot, switch plan, no-unaccounted-data-loss counts, and read-model comparison
    And member-facing capability states remain provider-neutral while apply and production default changes stay blocked
