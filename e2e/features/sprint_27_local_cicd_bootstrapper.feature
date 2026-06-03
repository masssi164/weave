Feature: Sprint 27 local CI/CD bootstrapper
  The local bootstrapper prepares a selected CI/CD target before Admin Console live orchestration.

  @sprint27-local-cicd-bootstrapper
  Scenario: Operator selects Forgejo without persisting GitHub secrets
    Given an operator starts the local Weave setup bootstrapper executable
    And the bootstrapper detects existing GitHub Actions, GitLab CI, Azure Pipelines, and Forgejo workflow files
    When the operator selects the Forgejo CI/CD target and enters only non-secret target values and required secret-name hints
    Then the bootstrapper generates a support-safe config and workflow plan
    And GitHub repository secrets are not required for the Forgejo path
    And commit and push are allowed only for the selected Forgejo remote and branch after explicit request
    And no secret value, raw CI log, provider payload, credential-bearing URL, tenant URL, or member content is persisted
