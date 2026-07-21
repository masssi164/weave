@weave-spec-0011
Feature: WEAVE-SPEC-0011 Weaver governed PA target acceptance

  Historical implementation evidence for governed per-user Weaver conformance to Agent Runtime Control.

  @weave-spec-0011-group-policy-gating
  Scenario: Agent runtime provisioning is gated by authoritative Keycloak entitlement
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_GROUP_POLICY_GATING
    And Weave Control reports the configured Keycloak group and derived agent-runtime.entitled capability before rollout
    When a member lacks policy approval or the authoritative entitlement group
    Then no runtime cell workload identity RuntimeProfile or MCP access is provisioned
    And the catalog records the bounded domains agent-runtime-control, identity-idm, admin-health-ops

  @weave-spec-0011-memory-isolation
  Scenario: Runtime state is external encrypted and isolated per cell
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_MEMORY_ISOLATION
    When two entitled cells checkpoint runtime-internal state
    Then each cell has zero durable local bytes and separate encrypted fenced generations with deletion retention privacy and audit rules
    And the catalog records the bounded domains agent-runtime-control, identity-idm, decisions-evidence

  @weave-spec-0011-domain-tool-approval
  Scenario: Workload MCP stays empty until domain tools have current action evidence contracts
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_DOMAIN_TOOL_APPROVAL
    When an entitled cell connects with its per-cell Keycloak service account
    Then MCP admits only the exact workload audience and current cell binding and publishes no domain tools from RuntimeProfile content
    And the catalog records the bounded domains agent-runtime-control, provider-portability, decisions-evidence

  @weave-spec-0011-heartbeat-fallback-audit
  Scenario: Runtime wake processing fails closed with support-safe audit and fallback
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_HEARTBEAT_FALLBACK_AUDIT
    When a cell lacks current entitlement workload identity or RuntimeProfile v2 during wake processing
    Then Agent Runtime Control blocks the wake offers the operator a safe recovery action and records support-safe audit correlation
    And the catalog records the bounded domains agent-runtime-control, admin-health-ops, decisions-evidence
