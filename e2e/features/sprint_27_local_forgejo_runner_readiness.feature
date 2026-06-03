Feature: Sprint 27 local Forgejo runner readiness
  The local Forgejo path blocks dispatch until runner and SecretRef readiness are support-safe.

  @sprint27-local-forgejo-runner-readiness
  Scenario: Runner readiness records the real local runner and keeps dispatch gated
    Given the bootstrapper selected providerKey local-forgejo-actions for workflow weave-admin-setup-e2e
    And the local readiness source of truth is a Forgejo Actions runner service/config under ~/server plus customer-owned SecretRefs, not GitHub repository secrets
    When the local proof observes runner_registered with service_exists, config_path_exists, registered, running, and secret_refs_present
    Then the runner existence part of #662 is satisfied without exposing values
    And dispatch remains blocked until the deployable plan, PipelineProvider contract, and explicit admin approval exist
    When those future gates are present
    Then the state can transition to dispatch_allowed only after explicit admin approval
    And no secret value, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is persisted
