@northstar @spec-kit @claim-control
# Evidence markers: NORTHSTAR_CLAIM_CONTROL NORTHSTAR_IDENTITY_RBAC_SWITCH NORTHSTAR_WEAVER_APPROVAL_RECEIPT NORTHSTAR_DOMAIN_FIRST_MCP
Feature: Northstar Spec Kit decision gates
  Northstar decisions are encoded before implementation claims are promoted.

  @northstar-claim-control
  Scenario: Public customer-ready wording stays blocked until evidence is complete
    Given the release evidence packet is missing manual assistive-technology signoff
    Or open release blockers are greater than zero
    When public v0.1 wording is evaluated
    Then customer-ready and release-ready claims are rejected
    And the evidence names the missing blocker or signoff item

  @northstar-identity-rbac-provider-switch
  Scenario: Identity/RBAC is the first provider-switch proof
    Given an admin prepares an Identity/RBAC provider switch
    When the dry-run is produced
    Then it includes principal continuity, group and role mappings, token and claim parity, SCIM or SSO lifecycle limits, rollback refs, and orphan/trust-artifact cleanup
    And every unsupported or lossy mapping is classified so there is no unaccounted data loss

  @northstar-weaver-approval-receipts
  Scenario: Weaver approvals are product-domain grants, not OpenClaw exec permissions
    Given Weaver is enabled for an opted-in user by organization policy
    When a write-like domain tool requests approval
    Then the approval receipt is scoped to the Weave domain, capability, tool, action, policy version, runtime profile, expiry or revocation state, and audit correlation id
    And it does not grant generic local exec, filesystem, provider-admin, or raw OpenClaw configuration permission

  @northstar-domain-first-mcp-hard-gate
  Scenario: Domain-first MCP naming is a hard gate
    Given a Weaver MCP tool registry contains provider-prefixed or adapter-prefixed tool names
    When the domain-first MCP gate runs
    Then the registry is rejected before discovery or invocation is exposed to members
