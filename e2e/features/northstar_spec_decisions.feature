@northstar @spec-kit @claim-control
# Evidence markers: NORTHSTAR_CLAIM_CONTROL NORTHSTAR_IDENTITY_RBAC_SWITCH NORTHSTAR_WEAVER_APPROVAL_RECEIPT NORTHSTAR_DOMAIN_FIRST_MCP NORTHSTAR_SPEC_COVERAGE_MATRIX NORTHSTAR_DOMAIN_REGISTRY_REALITY NORTHSTAR_SPACE_ANCHOR_CONTEXT NORTHSTAR_LOCAL_DOGFOOD_REALITY NORTHSTAR_WORKFLOW_GOVERNANCE_RECEIPT NORTHSTAR_MEETING_CONSENT_BOUNDARY NORTHSTAR_PORTABILITY_NO_UNACCOUNTED_LOSS
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
  Scenario: Weaver approval decisions are evidence not domain grants or OpenClaw exec permissions
    Given Weaver is enabled for an opted-in user by organization policy
    When a write-like domain tool requests approval
    Then signed single-use decision evidence is bound to the authenticated resolver exact action arguments policy runtime profile expiry and audit correlation id
    And it grants neither domain authority nor generic local exec filesystem provider-admin or raw OpenClaw configuration permission

  @northstar-domain-first-mcp-hard-gate
  Scenario: Domain-first MCP naming is a hard gate
    Given a Weaver MCP tool registry contains provider-prefixed or adapter-prefixed tool names
    When the domain-first MCP gate runs
    Then the registry is rejected before discovery or invocation is exposed to members


  @northstar-spec-coverage-matrix
  Scenario: Northstar decisions require per-spec acceptance coverage
    Given a Northstar product decision changes claim boundaries, governance, provider portability, workspace context, or local dogfood evidence
    When the Spec Kit corpus is evaluated
    Then every affected WEAVE-SPEC projection names the decision in spec, plan or tasks
    And every affected product claim has a mapped Gherkin scenario before implementation or promotion

  @northstar-domain-registry-reality
  Scenario: Domain registry carries reality levels and capability names for Northstar claims
    Given provider-neutral domains are advertised through Weave
    When domain capability and provider candidates are registered
    Then each canonical domain uses Weave-owned names with stable capability keys, aliases, and portability metadata
    And each provider candidate declares a reality level so contract-only evidence cannot become customer-ready wording

  @northstar-space-anchor-context
  Scenario: Space anchor binds Northstar domains without raw provider identifiers
    Given a member enters a Space control room
    When chat, files, boards, calendar, decisions, and Weaver context are shown together
    Then the Space identity remains Weave-owned and provider-neutral
    And domain bindings expose readiness, source of truth, migration state, and lossy notes without raw provider object identifiers

  @northstar-workflow-governance-receipt
  Scenario: Executable workflows require governed receipts and drift checks
    Given a context-driven workflow moves beyond preview-only evidence
    When the workflow proposes a write, destructive, external-send, provider-switch, or release-affecting action
    Then the workflow instance records the policy decision action preview required decision evidence execution outcome rollback or compensation reference and support-safe audit correlation id
    And execution fails closed when the workflow definition policy version decision evidence tool contract runtime profile or referenced context node has drifted since approval

  @northstar-meeting-consent-boundary
  Scenario: Meeting join and transcript claims stay blocked without consent and boundary evidence
    Given a contextual meeting surface is attached to a channel, calendar event, or thread
    When join, caption, transcript, recording, or security wording is evaluated
    Then member availability claims require capability, policy, media-provider readiness, participant-visible consent, retention and storage boundaries, redaction policy, accessibility behavior, support-safe audit, and target-branch evidence
    And provider URLs, meeting tokens, SFU internals, credentials, raw diagnostics, or metadata-as-E2EE claims are rejected

  @northstar-portability-no-unaccounted-loss
  Scenario: Provider portability rejects unaccounted-loss and broad lossless claims
    Given a provider replacement dry-run is prepared for Identity/RBAC or another canonical domain
    When preflight, dry-run, apply or cutover, rollback, and post-cutover receipts are evaluated
    Then every source object, target mapping, token or claim parity result, SCIM lifecycle limit, SSO staleness limit, orphan identity, and trust artifact is classified as supported, lossy, unsupported, manual-review, rollback-only, or archived
    And lossless, full-history, provider-interchangeable, customer-ready, or release-ready wording is rejected unless named release evidence proves it for that scope

  @northstar-local-dogfood-reality
  Scenario: Local dogfood evidence uses weave.test and blocks live claims without runtime proof
    Given local dogfood evidence is collected for a Northstar claim
    When the evidence names URLs, topology, or release posture
    Then weave.test is the only active local URL truth
    And offline-spec evidence remains separate from live-runtime proof until the live stack gate is collected
