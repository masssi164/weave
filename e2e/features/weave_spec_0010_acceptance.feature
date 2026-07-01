@weave-spec-0010
Feature: WEAVE-SPEC-0010 full product target acceptance

  Complete Weave target product coverage for setup/governance, space work, provider change, and evidence/audit.

  @weave-spec-0010-setup-governance
  Scenario: Admin setup governs provider-neutral organization capabilities
    Given WEAVE-SPEC-0010 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0010_SETUP_GOVERNANCE
    When an admin configures organization identity spaces provider mappings policies and evidence baseline
    Then members see stable Weave capabilities rather than provider setup mechanics
    And the catalog records the bounded domains identity-idm, spaces, admin-health-ops, provider-portability

  @weave-spec-0010-space-work
  Scenario: Space work joins context across domains
    Given WEAVE-SPEC-0010 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0010_SPACE_WORK
    When a member enters a Space to find context and continue work
    Then chat files documents calendar meetings boards tasks decisions and evidence are linked as Weave objects
    And the catalog records the bounded domains spaces, chat, files, calendar, boards-tasks, decisions-evidence

  @weave-spec-0010-provider-change
  Scenario: Provider changes require dry-run approval rollback and audit
    Given WEAVE-SPEC-0010 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0010_PROVIDER_CHANGE
    When an admin starts a provider change
    Then Weave blocks silent mutation unless preflight dry-run approval cutover rollback and audit evidence are present
    And the catalog records the bounded domains provider-portability, admin-health-ops, decisions-evidence

  @weave-spec-0010-evidence-audit
  Scenario: Decisions and evidence are product domains
    Given WEAVE-SPEC-0010 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0010_EVIDENCE_AUDIT
    When a support auditor reviews work evidence
    Then Weave shows decisions provenance audit metadata and export posture without raw provider secrets
    And the catalog records the bounded domains decisions-evidence, admin-health-ops
