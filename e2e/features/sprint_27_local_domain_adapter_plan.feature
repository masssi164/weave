Feature: Sprint 27 local domain-adapter deployable plan
  Admin setup maps domain adapter choices into a deterministic support-safe local plan.

  @sprint27-local-domain-adapter-plan
  Scenario: Domain choices produce a support-safe Forgejo deployable plan
    Given the Admin setup asks existing-adapter versus self-hosted recommendation per Weave domain
    And the local Forgejo runner readiness signal is present
    When the admin selects adapters for server/backend, infra, identity, chat, files, calendar, boards/tasks, and health/readiness
    Then each domain maps to required SecretRef names, variable names, readiness checks, rollback refs, and evidence refs
    And unsupported adapters, missing refs, missing variables, missing rollback refs, or raw secret values fail closed before dispatch
    And the generated plan is deterministic and contains no secret values, raw CI logs, provider payloads, credential-bearing URLs, tenant URLs, or member content
