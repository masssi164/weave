Feature: Weave Control setup modes and bootstrap-to-client proof

  @weave-control-plan-preflight-modes
  Scenario: Admin preflights deploy-new, attach-existing, and hybrid modes before mutation
    Given an admin is preparing organization setup in Weave Control
    When the admin selects setup modes for identity, chat, files, calendar, boards, meetings, documents, and Weaver
    Then Weave Control shows a support-safe plan for deploy_new, attach_existing, and hybrid choices
    And unsupported combinations fail closed before mutation
    And the plan shows SecretRef or CredentialRef posture, consequence copy, rollback boundary, and blocked claims without secret values

  @weave-control-deploy-new-local-forgejo-e2e-boundary
  Scenario: Deploy-new proof requires pipeline, stack readiness, and Weave E2E
    Given an admin selects deploy_new for the dogfood stack through a local Forgejo target
    When the approved workflow dispatch reaches a terminal result
    Then Weave Control requires pipeline_terminal_success, stack_readiness_passed, and weave_e2e_passed before a deployed-stack claim
    And dispatch or preflight evidence alone remains dispatch_preflight_only
    And GitHub-only Live Stack evidence is not counted as the local Forgejo deployed-stack proof

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
    And release claims name whether member_provider_neutral_join_passed is proved by current evidence
