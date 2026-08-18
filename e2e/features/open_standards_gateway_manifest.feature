Feature: Open Standards Gateway manifest and control-plane boundary
  Weave exposes domain protocol entrypoints while OpenAPI remains control/admin/setup/readiness/revoke/manifest/generated convenience.

  @open-standards-manifest
  Scenario: Authenticated member discovers standard protocol entrypoints
    Given an authenticated member has a valid Weave OIDC session
    When the member loads the organization manifest
    Then Files advertises the Weave WebDAV facade at "/dav/files"
    And Calendar advertises the Weave CalDAV facade at "/caldav"
    And Chat advertises a Matrix Client-Server endpoint
    And Calls advertises MatrixRTC Profile 0 without a member Calls API
    And no provider URL, provider credential, raw provider payload, SecretRef value, or admin diagnostic is exposed

  @openapi-control-only
  Scenario: OpenAPI is only control and generated convenience
    Given the OpenAPI contract is generated
    When the contract is inspected for Files, Calendar, and Chat
    Then it contains only setup, readiness, revoke, manifest, admin, and generated convenience surfaces
    And obsolete Calendar and Chat REST data-plane routes are absent from OpenAPI
    And it does not expose durable Files, Calendar, or Chat member data-plane routes

  @support-safe-capability-states
  Scenario: Disabled or degraded domains return support-safe capability states
    Given a domain provider is disabled, degraded, or not configured
    When the member loads manifest and readiness
    Then the state is provider-neutral and screenreader-friendly
    And raw downstream diagnostics are redacted
