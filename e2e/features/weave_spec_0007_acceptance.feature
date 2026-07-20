@weave-spec-0007
Feature: WEAVE-SPEC-0007 acceptance

  Historical implementation evidence for the canonical Agent Runtime Control contract.

  @weave-spec-0007-runtime-profile-from-policy
  Scenario: Weaver runtime profile is generated from organization policy and roles
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_RUNTIME_PROFILE_FROM_POLICY
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers runtime profile from policy
    And the catalog records the bounded domains agent-runtime-control, identity-idm, admin-health-ops

  @weave-spec-0007-tool-approval-receipt-fail-closed
  Scenario: Weaver tool invocation requires signed decision evidence and current domain authorization
    Given the pinned Agent Runtime Control corpus contract is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_TOOL_APPROVAL_RECEIPT_FAIL_CLOSED
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers signed single-use decision evidence independent domain authorization and immutable action evidence
    And the catalog records the bounded domains agent-runtime-control, decisions-evidence

  @weave-spec-0007-intent
  Scenario: Governed Weaver intent keeps runtime tools policy-derived and auditable
    Given WEAVE-SPEC-0007 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_INTENT_GOVERNED_RUNTIME
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0007-scope
  Scenario: Governed Weaver separates approved tool execution from out-of-scope autonomous actions
    Given WEAVE-SPEC-0007 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0007-functional-requirements
  Scenario: Governed Weaver functional requirements are scenario-backed
    Given WEAVE-SPEC-0007 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0007-runtime-profile
  Scenario: RuntimeProfile projection model reflects policy roles grants and denied tools
    Given WEAVE-SPEC-0007 defines RuntimeProfile projection model
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_RUNTIME_PROFILE_PROJECTION
    When a product or release claim depends on RuntimeProfile projection model
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0007-initial-tool-set
  Scenario: Initial Weaver tool set is explicit approved and policy bounded
    Given WEAVE-SPEC-0007 defines Initial tool set
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0007_INITIAL_TOOL_SET
    When implementation or release evidence is reviewed against Initial tool set
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
