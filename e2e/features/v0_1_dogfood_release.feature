Feature: Weave v0.1 dogfood production release

  Weave v0.1 is an active dogfood-production daily work tool. These scenarios define
  the product spine that must be implemented with executable evidence before
  the release can be called dogfood-production.

  @weave-v01-home-daily-loop
  Scenario: Weave Home starts the daily work loop
    Given a signed-in workspace member opens Weave
    When the home view loads
    Then Weave shows recent channels, open tasks, upcoming meetings, recent decisions, and actionable health warnings
    And every home section has a keyboard and screen-reader path

  @weave-v01-user-ready-organization-flow
  Scenario: A normal member sees a user-ready organization flow
    Given an admin has provisioned the organization and invited a member
    When the member opens Weave and enters a channel workspace
    Then release-scope surfaces use ready, admin-setup-required, policy-disabled, degraded, or hidden states
    And the member does not see preview, scaffold, roadmap, or raw provider setup copy
    And provider diagnostics stay in admin/operator health surfaces

  @weave-v01-admin-provider-categories
  Scenario: Admin sees provider categories before member use
    Given an owner or admin opens Workspace Health before inviting members
    When provider readiness and policy are reviewed
    Then identity/IDM, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Weaver are shown as provider categories
    And current dogfood defaults map to category readiness without becoming member-facing product names
    And Weaver is disabled by default until admin policy explicitly enables it
    And normal members never configure raw providers, service endpoints, provider secrets, or diagnostics

  @weave-v01-admin-health-policy-enforcement
  Scenario: Admin health enforces provider readiness and member policy boundaries
    Given an owner or admin opens Workspace Health after selecting provider categories
    When backend provider readiness and capability policy are evaluated
    Then Workspace Health returns support-safe category readiness for ready, disabled, degraded, policy-blocked, and misconfigured states
    And members receive only usable, disabled, degraded, or policy-blocked impact states without raw provider setup
    And member API writes are denied when IDM capability policy does not grant the required category capability
    And Weaver remains disabled by default unless governed organization policy explicitly enables it

  @weave-v01-idm-rbac-capability-policy
  Scenario: IDM roles and groups decide capability profiles before Weaver runtime
    Given an owner has selected an IDM provider for the organization
    When role and group claims are mapped into workspace capability profiles
    Then Keycloak is the self-hosted default while OIDC and SAML adapters stay provider-neutral
    And capability profiles grant category-level capabilities deny-by-default
    And admins/operators can inspect support-safe policy state
    And members only see ready, disabled, degraded, or policy-blocked impact states
    And Weaver capability placeholders stay disabled by default until a governed runtime policy exists

  @weave-v01-governed-weaver-runtime-policy
  Scenario: Weaver runtime profiles are generated from organization policy
    Given an admin has enabled the Weaver provider category after IDM/RBAC policy is ready
    When a member with an explicit Weaver runtime group requests their runtime profile
    Then Weave generates a per-user Dockerized Weaver/OpenClaw-derived profile from workspace capability policy
    And the profile contains only admin-whitelisted capabilities and provider adapter tools
    And exec and elevated surfaces are disabled unless explicitly constrained by admin policy
    And runtime profile generation is audited and disabled or policy-blocked by default for everyone else

  @weave-v01-channel-workspace
  Scenario: A channel is the primary workspace surface
    Given a workspace member enters a project channel
    When they navigate the channel workspace
    Then chat, files, board, calendar, meetings, and decisions are available as first-class tabs
    And provider details stay behind Weave-owned product surfaces

  @weave-v01-board-write-audit
  Scenario: A user board write is authorized and audited
    Given a workspace member has permission to update a channel board
    When they create or move a task without drag-and-drop
    Then the server checks authorization before touching the provider
    And the write produces an audit record and a support-safe result

  @weave-v01-meeting-capsule
  Scenario: A meeting capsule keeps work connected
    Given a channel event has a linked meeting
    When the meeting starts and finishes
    Then the capsule keeps agenda, files, decisions, and follow-up tasks connected to the channel
    And media-provider secrets never reach the client

  @weave-v01-decision-ledger
  Scenario: Decisions are captured as product records
    Given a channel discussion reaches a decision
    When a member records the decision
    Then Weave stores context, evidence, risks, open questions, and follow-up links
    And the decision is reachable from the channel, meeting, board task, and home view

  @weave-v01-operator-release-path
  Scenario: Operators can deploy, verify, back up, restore, and diagnose safely
    Given an operator installs or updates a Weave stack
    When they run release verification, backup, restore smoke, and support-bundle checks
    Then every step produces deterministic evidence
    And diagnostics are redacted before sharing
