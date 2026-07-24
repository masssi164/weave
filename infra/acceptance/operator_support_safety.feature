Feature: Operator diagnostics and support bundles stay support-safe
  Operator checks are part of the live evidence layer: they must make service
  readiness and failure diagnostics useful without exposing credentials or
  destructive reset paths by default.

  @infra-support-bundle-redaction
  Scenario: Support bundle excludes secrets and provider credentials
    Given the selected Compose profile has private mounted SecretRefs
    When the operator creates a support bundle
    Then the bundle contains only normalized service metadata and explicitly support-safe evidence
    And raw logs environment values signed receipts passwords tokens and provider payloads are excluded

  @infra-operator-readiness
  Scenario: Operator checks verify the declared Compose product topology
    Given one exact Compose profile is reconciled through the documented install path
    When the operator runs the readiness check
    Then canonical Weave API auth Matrix and files origins and authenticated DAV evidence are checked
    And the result is a support-safe profile and Compose-project-bound readiness document

  @infra-provider-stack-readiness
  Scenario: Runner checks verify provider readiness fail-closed
    Given the runner boots the exact candidate through the selected Compose profile
    When authenticated readiness checks inspect public endpoints and provider evidence
    Then missing or unsuccessful authenticated DAV evidence blocks readiness
    And a member token receives a forbidden response from the admin control plane

  @infra-reset-guardrails
  Scenario: Destructive teardown rejects persistent projects
    Given the operator invokes the isolated teardown entry point
    When the test deployment context is not an exact run-scoped isolated namespace with matching ownership labels
    Then no Docker volume or network is removed
    And only an exact isolated project can produce support-safe teardown evidence
