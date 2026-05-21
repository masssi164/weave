Feature: Operator diagnostics and support bundles stay support-safe
  Operator checks are part of the live evidence layer: they must make service
  readiness and failure diagnostics useful without exposing credentials or
  destructive reset paths by default.

  @infra-support-bundle-redaction
  Scenario: Support bundle redacts secrets and provider credentials
    Given generated bootstrap and app config contain representative service secrets
    When the operator creates a support bundle
    Then the bundle contains support-safe public configuration and diagnostics
    And passwords tokens signing secrets and OpenProject credentials are redacted

  @infra-operator-readiness
  Scenario: Operator checks verify the local Weave product topology
    Given the local stack is bootstrapped through the documented install path
    When the operator runs the readiness check
    Then canonical Weave API auth Matrix and files origins are checked
    And diagnostics avoid printing secret environment values

  @infra-reset-guardrails
  Scenario: Destructive reset refuses persistent data deletion without typed confirmation
    Given the operator asks for teardown in dry-run mode
    When persistent volume removal is not explicitly confirmed
    Then identity Matrix Nextcloud and generated secret data are preserved
    And the refusal points to the backup expectations runbook
