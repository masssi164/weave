Feature: Sprint 27 local Forgejo runner readiness
  The local Forgejo path blocks dispatch until runner and SecretRef readiness are support-safe.

  @sprint27-local-forgejo-runner-readiness
  Scenario: Runner readiness blocks current local dispatch and defines the future allowed transition
    Given the bootstrapper selected providerKey local-forgejo-actions for workflow weave-admin-setup-e2e
    And the local readiness source of truth is Forgejo Actions runner plus customer-owned SecretRefs, not GitHub repository secrets
    When the current local proof observes runner_missing
    Then dispatch is blocked before provider mutation
    And the Admin Console may display the missing name FORGEJO_ACTIONS_RUNNER_REGISTRATION without values
    When a future proof observes runner_registered with required SecretRef names present
    Then the state can transition to dispatch_allowed only after explicit admin approval
    And no secret value, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is persisted
