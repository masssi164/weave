@weave-spec-0002
Feature: WEAVE-SPEC-0002 acceptance

  Context-driven workflow previews, governed execution receipts, drift checks, and compensation boundaries.

  @weave-spec-0002-workflow-preview-context
  Scenario: Workflow preview preserves Space context and policy inputs
    Given WEAVE-SPEC-0002 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_WORKFLOW_PREVIEW_CONTEXT
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers Space context preserved
    And the catalog records the bounded domains spaces, agent-runtime-control, decisions-evidence

  @weave-spec-0002-governed-execution-receipt
  Scenario: Governed workflow execution records receipt drift and compensation state
    Given WEAVE-SPEC-0002 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_GOVERNED_EXECUTION_RECEIPT
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers execution receipt
    And the catalog records the bounded domains agent-runtime-control, decisions-evidence, admin-health-ops

  @weave-spec-0002-intent
  Scenario: Workflow primitive intent keeps context-driven automation bounded
    Given WEAVE-SPEC-0002 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_INTENT_CONTEXT_DRIVEN
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-scope
  Scenario: Workflow primitives separate preview execution and out-of-scope automation
    Given WEAVE-SPEC-0002 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-constraints
  Scenario: Workflow primitives enforce governed approval and drift constraints
    Given WEAVE-SPEC-0002 defines Non-negotiable constraints
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_NON_NEGOTIABLE_CONSTRAINTS
    When a product or release claim depends on Non-negotiable constraints
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-functional-requirements
  Scenario: Workflow primitive functional requirements are scenario-backed
    Given WEAVE-SPEC-0002 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-support-incident
  Scenario: Support incident workflow links notes tasks dry-run summaries and owner approval
    Given WEAVE-SPEC-0002 defines Support incident resolution
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_SUPPORT_INCIDENT_RESOLUTION
    When a product or release claim depends on Support incident resolution
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-sample-workflows
  Scenario: Sample workflows remain mapped to explicit acceptance evidence
    Given WEAVE-SPEC-0002 defines Sample workflows
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_SAMPLE_WORKFLOWS
    When a product or release claim depends on Sample workflows
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-release-impact
  Scenario: Workflow release impact requires support-safe evidence artifacts
    Given WEAVE-SPEC-0002 defines Release and migration impact
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_RELEASE_IMPACT
    When a product or release claim depends on Release and migration impact
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0002-governed-agent-participation
  Scenario: Governed agent participation requires receipts dry-run and owner approval
    Given WEAVE-SPEC-0002 defines US3 - Governed agent participation
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_GOVERNED_AGENT_PARTICIPATION
    When implementation or release evidence is reviewed against US3 - Governed agent participation
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0002-domain-model-contracts
  Scenario: Workflow domain model and contracts link preview execution receipt and compensation
    Given WEAVE-SPEC-0002 defines Domain model and contracts
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0002_DOMAIN_MODEL_CONTRACTS
    When implementation or release evidence is reviewed against Domain model and contracts
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
