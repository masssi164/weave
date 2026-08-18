@weave-spec-0009
Feature: WEAVE-SPEC-0009 acceptance

  Domain-first MCP tool naming, approved discovery, support-safe metadata, and provider-first rejection.

  @weave-spec-0009-domain-first-tool-names
  Scenario: MCP tools are named by Weave domain capability not provider adapter
    Given WEAVE-SPEC-0009 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0009_DOMAIN_FIRST_TOOL_NAMES
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers domain capability tool names
    And the catalog records the bounded domains agent-runtime-control, admin-health-ops

  @weave-spec-0009-tool-discovery-support-safe
  Scenario: Tool discovery returns approved support-safe metadata only
    Given WEAVE-SPEC-0009 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0009_TOOL_DISCOVERY_SUPPORT_SAFE
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers approved discovery
    And the catalog records the bounded domains agent-runtime-control, admin-health-ops, decisions-evidence

  @weave-spec-0009-acceptance
  Scenario: Domain-first MCP acceptance rejects provider-first tool claims
    Given WEAVE-SPEC-0009 defines Acceptance
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0009_ACCEPTANCE_PROVIDER_FIRST_REJECTED
    When a product or release claim depends on Acceptance
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected
