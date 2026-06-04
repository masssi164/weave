Feature: Sprint 28 commercial adapter readiness remains readiness-only
  Commercial chat providers are evaluated before implementation starts so Weave does not overclaim Slack or Teams capability.

  @sprint28-commercial-adapter-readiness
  Scenario: Teams and Slack readiness specs block implementation starts
    Given the commercial adapter readiness specification covers Microsoft Teams and Slack
    When the go/no-go matrix is evaluated for Sprint 28
    Then Microsoft Teams implementation remains blocked
    And Slack implementation remains blocked
    And unsupported commercial adapter availability claims remain blocked
