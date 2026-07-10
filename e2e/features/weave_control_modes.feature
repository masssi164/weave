Feature: Weave Control setup modes and bootstrap-to-client proof

  @weave-control-plan-preflight-modes
  Scenario: Admin preflights deploy-new, attach-existing, and hybrid modes before mutation
    Given an admin is preparing organization setup in Weave Control
    When the admin selects setup modes for identity, chat, files, calendar, boards, meetings, documents, and Weaver
    Then Weave Control shows a support-safe plan for deploy_new, attach_existing, and hybrid choices
    And unsupported combinations fail closed before mutation
    And the plan shows SecretRef or CredentialRef posture, consequence copy, rollback boundary, and blocked claims without secret values
    And Weaver is represented only as a future governed organization capability and not as a v0.1 Spec 0001 runtime claim

  @weave-control-admin-console-client-responsibility-split
  Scenario: Weave Control, Admin Console, and Client keep separate responsibilities
    Given Weave Control has produced support-safe deployment handoff refs
    And Bootstrap deploys the Control Plane as server plus Admin Console
    When admins review organization readiness in the Admin Console
    Then the Admin Console may show provider, policy, readiness, audit, whitelist, and future Weaver governance states with sanitized refs
    And the Weave App consumes only organization URL, invite link, or deep link handoff plus provider-neutral capability states
    And the GitHub deployment lane remains client-free and never emits member_provider_neutral_join_passed or weave_client_e2e_passed

  @weave-control-deploy-new-github-dogfood-e2e-boundary
  Scenario: Deploy-new proof requires pipeline, server/infra readiness, Weave Control, and client-bootstrap handoff
    Given an admin selects deploy_new for the dogfood stack through the protected GitHub dogfood workflow
    When the approved workflow dispatch reaches a terminal result
    Then Weave Control requires pipeline_terminal_success, server_infra_readiness_passed, weave_control_ready, and client_bootstrap_handoff_ready before a deployment handoff claim
    And dispatch or preflight evidence alone remains dispatch_preflight_only
    And Flutter or App E2E evidence is collected in a separate client lane against the handoff target, not in the GitHub deployment job

  @weave-control-attach-existing-preflight-boundary
  Scenario: Attach-existing proof binds existing systems without redeploying them
    Given an admin selects attach_existing for one or more provider domains
    When Weave Control runs attach preflight against opaque configuration handles
    Then no provider redeploy, destructive migration, or credential rotation is planned unless separately approved
    And readiness maps the existing systems into provider-neutral member capability states
    And attach-existing evidence cannot be satisfied by a deploy-new pipeline run

  @weave-control-hybrid-domain-separation
  Scenario: Hybrid setup keeps per-domain mutation boundaries separate
    Given an organization uses deploy_new for one domain and attach_existing for another domain
    When Weave Control previews the hybrid setup plan
    Then each domain keeps its own mode, mutation boundary, readiness evidence, and rollback expectation
    And unsupported hybrid combinations fail closed before apply
    And the member manifest remains one coherent provider-neutral organization view

  @weave-control-member-bootstrap-invariant
  Scenario: Member joins through invite or organization link after admin bootstrap
    Given Weave Control has completed the approved bootstrap path for the selected setup mode
    And an admin creates, activates, or invites the first member
    When the member opens the organization URL, invite link, or deep link and completes SSO
    Then the Weave App shows product surfaces without provider setup, OIDC endpoint setup, CI/CD targets, SecretRefs, bootstrap diagnostics, or raw provider errors
    And release claims name whether member_provider_neutral_join_passed and weave_client_e2e_passed are proved by current evidence
