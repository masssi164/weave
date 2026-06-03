Feature: Sprint 26 Admin Console CI/CD setup orchestration
  The Admin Console is the canonical setup surface while CI/CD executes and validates setup.

  @sprint26-admin-cicd-setup
  Scenario: Admin sees missing Forgejo runner registration before pipeline dispatch
    Given an admin registers the local Forgejo CI/CD provider through support-safe refs
    And required Forgejo variables and SecretRefs are named without displaying values
    When setup preflight checks runner readiness before dispatch
    Then the Admin Console shows the missing name "FORGEJO_ACTIONS_RUNNER_REGISTRATION"
    And the setup state is blocked with reason "runner_missing"
    And no pipeline dispatch, secret value, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is exposed
