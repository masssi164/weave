@weave-spec-0011
Feature: WEAVE-SPEC-0011 Weaver governed PA target acceptance

  Historical implementation evidence for governed per-user Weaver conformance to Agent Runtime Control.

  @weave-spec-0011-group-policy-gating
  Scenario: Weaver provisioning is gated by organization policy and weaver-group membership
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_GROUP_POLICY_GATING
    And Weave Control previews Weaver policy enablement and required weaver-group eligibility before rollout
    When a member lacks policy approval or weaver-group membership
    Then no Weaver runtime profile tool grants memory or automation are provisioned
    And the catalog records the bounded domains agent-runtime-control, identity-idm, admin-health-ops

  @weave-spec-0011-memory-isolation
  Scenario: Weaver memory is isolated per user
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_MEMORY_ISOLATION
    When two Weaver Users store personal context
    Then each user memory remains isolated and governed by export delete retention privacy and audit rules
    And the catalog records the bounded domains agent-runtime-control, identity-idm, decisions-evidence

  @weave-spec-0011-domain-tool-approval
  Scenario: Weaver uses domain-first tools with signed decision and action evidence
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_DOMAIN_TOOL_APPROVAL
    When Weaver performs a risky write external-send provider-change or administrative action
    Then execution requires signed single-use decision evidence plus current independent domain authorization matching policy profile tool contract arguments and user scope
    And the catalog records the bounded domains agent-runtime-control, provider-portability, decisions-evidence

  @weave-spec-0011-heartbeat-fallback-audit
  Scenario: Weaver automation heartbeat fails closed with support-safe audit and fallback
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0011_HEARTBEAT_FALLBACK_AUDIT
    When Weaver lacks authority evidence or a current runtime profile during heartbeat automation
    Then it explains the block asks only necessary follow-up questions offers safe fallback and records support-safe audit
    And the catalog records the bounded domains agent-runtime-control, admin-health-ops, decisions-evidence
