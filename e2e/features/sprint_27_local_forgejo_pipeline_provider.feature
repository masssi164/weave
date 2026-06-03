Feature: Sprint 27 local Forgejo PipelineProvider
  Backend-owned CI/CD orchestration dispatches and observes local Forgejo through support-safe refs.

  @sprint27-local-forgejo-pipeline-provider
  Scenario: Local Forgejo PipelineProvider gates dispatch and observes support-safe run status
    Given bootstrapper, runner readiness, and domain deployable plan evidence exist for local-forgejo-actions
    And required SecretRef and variable names are present without values
    When the Admin Console requests setup pipeline dispatch before explicit approval
    Then the backend PipelineProvider blocks dispatch as approval_missing
    When explicit admin approval is captured
    Then the backend PipelineProvider can return a support-safe PipelineRunRef for weave-admin-setup-e2e
    And queued, running, terminal failure, timeout, rate limit, unknown, and evidence_complete statuses are represented without raw provider payloads or logs
    And raw secret values, provider URLs, CI logs, credential-bearing links, tenant URLs, or member content fail closed before dispatch
