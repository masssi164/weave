Feature: Weave northbound access manifest
  Weave exposes generated User operations and the guarded Matrix Client-Server profile without claiming deferred public DAV or Calls support.

  @open-standards-manifest
  Scenario: Authenticated member discovers release access surfaces
    Given an authenticated member has a valid Weave OIDC session
    When the member loads the organization manifest
    Then Files advertises the guarded generated User API without public WebDAV
    And Calendar advertises the guarded generated User API without public CalDAV
    And Chat advertises the guarded Weave Matrix Client-Server endpoint
    And Calls is absent from member access discovery
    And no provider URL, provider credential, raw provider payload, SecretRef value, or admin diagnostic is exposed

  @openapi-domain-boundaries
  Scenario: OpenAPI retains domain boundaries
    Given the OpenAPI contract is generated
    When the contract is inspected for Files, Calendar, and Chat
    Then it contains generated User domain entrypoints
    And it excludes public DAV and proprietary Chat REST routes

  @support-safe-capability-states
  Scenario: Disabled or degraded domains return support-safe capability states
    Given a domain provider is disabled, degraded, or not configured
    When the member loads manifest and readiness
    Then the state is provider-neutral and screenreader-friendly
    And raw downstream diagnostics are redacted
