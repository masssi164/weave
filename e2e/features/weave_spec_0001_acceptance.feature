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
    When preflight and portable export import evidence are requested
    Then Weave records plan, cutover gates, rollback recovery, support-safe audit evidence, and export import contracts
    And the evidence does not claim full automated live production migration without separate release signoff

  @weave-spec-0001-weaver-ai-runtime-excluded
  Scenario: Weaver AI runtime stays excluded from Spec 0001 acceptance
    Given WEAVE-SPEC-0001 defines the Admin Suite and provider-neutral product core
    When acceptance evidence is collected for this spec
    Then Weaver and AI runtime behavior is treated as out of scope
    And runtime profiles, agent tools, and uncontrolled plugin installation cannot be implied as shipped by this spec
