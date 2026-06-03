Feature: Sprint 27 local Forgejo deployed-stack E2E handoff
  The local setup proof reaches Weave E2E only after real runner, pipeline, stack, and E2E signals exist.

  @sprint27-local-forgejo-e2e-handoff
  Scenario: E2E evidence remains blocked until the real local runner and pipeline signals exist
    Given the bootstrapper produced a support-safe local Forgejo plan
    And runner readiness requires the concise ~/server service/config/registered/running signal
    When no pipeline terminal-success, deployed-stack readiness, and Weave E2E signal has been provided
    Then the #665 handoff remains blocked_awaiting_local_runner_and_pipeline_signal
    And no secret value, runner registration token, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is persisted
    When future evidence includes pipeline_terminal_success, stack_readiness_passed, and weave_e2e_passed
    Then the evidence can be correlated by support-safe refs without claiming production cutover
