Feature: WEAVE-SPEC-0001 acceptance and evidence mapping

  WEAVE-SPEC-0001 acceptance must prove the admin/provider-neutral product core
  without implying live production migration or Weaver/AI runtime delivery.

  @weave-spec-0001-member-join-invite-sso-passkey
  Scenario: Member joins a configured organization through invite SSO or passkey
    Given an admin has configured organization providers and policy before inviting members
    When a normal member follows an invite, organization URL, or passkey-capable SSO entry
    Then the member lands in assigned Weave workspaces with capabilities already resolved
    And the member never configures OIDC, provider endpoints, secrets, readiness, or repair flows

  @weave-spec-0001-stable-member-capabilities
  Scenario: Member sees stable capabilities only after joining
    Given backend readiness and policy have been evaluated for the organization
    When a normal member opens Weave feature surfaces
    Then every member capability uses available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later
    And raw provider diagnostics, endpoint details, provider setup, and admin controls remain absent from member surfaces

  @weave-spec-0001-admin-readiness-setup
  Scenario: Admin configures domains through setup and reviews readiness evidence
    Given an owner admin or operator opens the Admin Suite before member use
    When they bind domains, validate adapters, and review the readiness dashboard
    Then readiness is shown per provider-neutral domain with support-safe next actions and evidence
    And accessibility, supportability, auditability, and deployability remain release blockers

  @weave-spec-0001-provider-switch-evidence
  Scenario: Provider switch evidence distinguishes spec acceptance from live migration
    Given an admin plans a provider switch for a configured domain
    When preflight and portable export/import evidence are requested
    Then Weave records plan, cutover gates, rollback/recovery, support-safe audit evidence, and export/import contracts
    And the evidence does not claim full automated live production migration without separate release signoff

  @weave-spec-0001-weaver-ai-runtime-excluded
  Scenario: Weaver AI runtime stays excluded from Spec 0001 acceptance
    Given WEAVE-SPEC-0001 defines the Admin Suite and provider-neutral product core
    When acceptance evidence is collected for this spec
    Then Weaver and AI runtime behavior is treated as out of scope
    And runtime profiles, agent tools, and uncontrolled plugin installation cannot be implied as shipped by this spec

  @weave-spec-0001-intent
  Scenario: Admin product core intent remains provider-neutral and support-safe
    Given WEAVE-SPEC-0001 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_INTENT_PROVIDER_NEUTRAL
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0001-constraints
  Scenario: Admin product core enforces non-negotiable provider facade constraints
    Given WEAVE-SPEC-0001 defines Non-negotiable constraints
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_NON_NEGOTIABLE_CONSTRAINTS
    When a product or release claim depends on Non-negotiable constraints
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0001-functional-requirements
  Scenario: Admin product core functional requirements are scenario-backed
    Given WEAVE-SPEC-0001 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0001-support-evidence
  Scenario: SupportEvidence remains redacted backend-owned and member-safe
    Given WEAVE-SPEC-0001 defines SupportEvidence
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_SUPPORT_EVIDENCE
    When a product or release claim depends on SupportEvidence
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0001-closed-questions
  Scenario: Closed product questions remain encoded as acceptance boundaries
    Given WEAVE-SPEC-0001 defines Closed questions
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_CLOSED_QUESTIONS
    When a product or release claim depends on Closed questions
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0001-decision-record
  Scenario: Decision record fixes provider-neutral product-core choices
    Given WEAVE-SPEC-0001 defines Decision record
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_DECISION_RECORD
    When implementation or release evidence is reviewed against Decision record
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0001-required-v01-domains
  Scenario: Required v0.1 domains expose capability states before member use
    Given WEAVE-SPEC-0001 defines Required v0.1 domains
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_REQUIRED_V01_DOMAINS
    When implementation or release evidence is reviewed against Required v0.1 domains
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0001-capability-vocabulary
  Scenario: Capability vocabulary stays stable across providers and UI surfaces
    Given WEAVE-SPEC-0001 defines Capability vocabulary
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_CAPABILITY_VOCABULARY
    When implementation or release evidence is reviewed against Capability vocabulary
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name

  @weave-spec-0001-supportevidence-contract
  Scenario: SupportEvidence is redacted backend-owned and scenario-visible
    Given WEAVE-SPEC-0001 defines SupportEvidence
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0001_SUPPORTEVIDENCE_CONTRACT
    When implementation or release evidence is reviewed against SupportEvidence
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
