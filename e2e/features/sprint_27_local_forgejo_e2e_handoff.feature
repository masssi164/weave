Feature: Sprint 27 local Forgejo deployment handoff and separate client evidence
  The local setup proof reaches deployment handoff only after real runner, pipeline, Weave Control, server, infra, and client-bootstrap signals exist. App/client E2E is a separate lane against the handoff target, not part of the Forgejo deployment runner.

  @sprint27-local-forgejo-e2e-handoff
  Scenario: Direct local handoff and client evidence pass while Forgejo-runner terminal proof remains explicit
    Given the bootstrapper produced a support-safe local Forgejo plan
    And runner readiness has the concise ~/server service/config/registered/running signal
    And a direct local stack install plus operator-check passed on the current working tree
    And separate client-lane evidence passed against the handoff target
    Then the #665 artifact records forgejo_runner_handoff_and_separate_client_e2e_passed
    And no secret value, runner registration token, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is persisted
    When strict Forgejo-runner closure is required
    Then a current forgejo_runner_workflow_terminal_success ref is still required before claiming local Forgejo workflow completion
    And the Forgejo deployment workflow may emit pipeline_terminal_success, server_infra_readiness_passed, weave_control_ready, and client_bootstrap_handoff_ready only
    And separate client-lane evidence emits member_provider_neutral_join_passed and weave_client_e2e_passed against the handoff target
