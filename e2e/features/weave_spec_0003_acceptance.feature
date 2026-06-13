@weave-spec-0003
Feature: WEAVE-SPEC-0003 acceptance

  Meeting consent, encrypted boundaries, transcript retention, and follow-up artifact handling.

  @weave-spec-0003-meeting-consent-boundary
  Scenario: Meeting join stays blocked without consent and boundary evidence
    Given WEAVE-SPEC-0003 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_MEETING_CONSENT_BOUNDARY
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers meeting consent boundary
    And the catalog records the bounded domains meetings-calls, identity-idm, decisions-evidence

  @weave-spec-0003-transcript-retention-followup
  Scenario: Transcript and follow-up artifacts stay encrypted retained and policy-linked
    Given WEAVE-SPEC-0003 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_TRANSCRIPT_RETENTION_FOLLOWUP
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers transcript retention
    And the catalog records the bounded domains meetings-calls, files, decisions-evidence

  @weave-spec-0003-intent
  Scenario: Meeting contract intent keeps contextual collaboration encrypted and bounded
    Given WEAVE-SPEC-0003 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_INTENT_ENCRYPTED_CONTEXT
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-scope
  Scenario: Meetings separate allowed contextual surfaces from out-of-scope recording claims
    Given WEAVE-SPEC-0003 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-constraints
  Scenario: Meetings enforce consent retention caption and accessibility constraints
    Given WEAVE-SPEC-0003 defines Non-negotiable constraints
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_NON_NEGOTIABLE_CONSTRAINTS
    When a product or release claim depends on Non-negotiable constraints
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-stories
  Scenario: Meeting user admin and operator stories are scenario-backed
    Given WEAVE-SPEC-0003 defines User/admin/operator stories
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_USER_ADMIN_OPERATOR_STORIES
    When a product or release claim depends on User/admin/operator stories
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-functional-requirements
  Scenario: Meeting functional requirements are scenario-backed
    Given WEAVE-SPEC-0003 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-recording-consent
  Scenario: Recording transcription and captions stay blocked without explicit consent and retention evidence
    Given WEAVE-SPEC-0003 defines Recording/transcription defaults and consent requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_RECORDING_CONSENT_RETENTION
    When a product or release claim depends on Recording/transcription defaults and consent requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-release-impact
  Scenario: Meeting release impact requires local and CI evidence before claims
    Given WEAVE-SPEC-0003 defines Release and migration impact
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_RELEASE_IMPACT
    When a product or release claim depends on Release and migration impact
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0003-domain-model-contracts
  Scenario: Meeting domain model and contracts link capsule consent transcript and follow-up artifacts
    Given WEAVE-SPEC-0003 defines Domain model and contracts
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0003_DOMAIN_MODEL_CONTRACTS
    When implementation or release evidence is reviewed against Domain model and contracts
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
