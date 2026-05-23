Feature: Provider stack readiness is backend-owned and fail-closed
  Weave exposes optional collaboration providers through backend-owned facades.
  Provider readiness must be visible to the product app, safe for support bundles,
  and closed by default when optional provider runtimes are disabled or unconfigured.

  Background:
    Given a workspace-scoped product caller

  @backend-provider-registry-visible
  Scenario: Provider registry exposes support-safe backend facade readiness
    When the product app requests provider readiness through Weave
    Then the response status is 200
    And the provider registry is visible through "GET /api/providers/status"
    And backend-owned facades are required
    And direct Flutter provider calls are refused by contract
    And provider modules include files calendar boards office meetings contacts forms source-control issue-tracker ci release and identity-realm
    And meetings readiness uses LiveKit as the active provider and fails closed support-safely
    And Identity Forms and Contacts readiness is mapped to dependent backend PRs
    And disabled or unconfigured optional providers fail closed
    And no provider secrets or raw provider errors are exposed

  @backend-profile-readiness-contract
  Scenario: Profile readiness exposes CEFACADE without direct provider access
    When the product app requests profile readiness through Weave
    Then the response status is 200
    And profile readiness is visible through "GET /api/profile/readiness"
    And profile readiness uses CEFACADE at "/profile/readiness"
    And profile readiness is backend-owned support-safe and forbids direct provider calls
    And no provider secrets or raw provider errors are exposed

  @backend-devops-readiness-fail-closed
  Scenario: DevOps readiness fails closed through the product facade
    When the product app requests DevOps summary for workspace "workspace-default" and channel "channel-general"
    Then the response status is 200
    And DevOps readiness is read-only support-safe and not configured
    And disabled optional DevOps providers expose no linked projects repositories issues merge requests pipelines or releases
    And no provider secrets or raw provider errors are exposed

  @backend-office-readiness-fail-closed
  Scenario: Office capabilities and launch fail closed without leaking provider details
    When the product app requests Office capabilities through Weave
    Then the response status is 200
    And Office capabilities are not configured and promise no edit session
    When the product app safely requests an Office launch session for file "file-123" in mode "edit"
    Then the response status is 503
    And Office launch is refused support-safely with "office-provider-not-configured"
    And no provider secrets or raw provider errors are exposed
