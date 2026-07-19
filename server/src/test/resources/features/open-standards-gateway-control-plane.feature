Feature: Open standards gateway control plane
  Weave advertises standard protocol entrypoints while keeping OpenAPI to setup, readiness, revoke, and manifest control-plane work.

  Scenario: Authenticated member discovers standard protocol entrypoints
    Given an authenticated member has a valid Weave OIDC session
    When the member loads the organization manifest
    Then Files advertises the Weave WebDAV facade at "/dav/files"
    And Calendar advertises the Weave CalDAV facade at "/caldav"
    And Chat advertises a Matrix Client-Server endpoint
    And Calls advertises MatrixRTC Profile 0 through Matrix Client-Server
    And no provider URL, provider credential, raw provider payload, SecretRef value, or admin diagnostic is exposed

  Scenario: OpenAPI is only control and generated convenience
    Given the OpenAPI contract is generated
    When the contract is inspected for Files, Calendar, and Chat
    Then it contains only setup, readiness, revoke, manifest, admin, and generated convenience surfaces
    And it does not expose durable Files, Calendar, or Chat member data-plane routes

  Scenario: Files setup credentials return a Weave secret once without exposing provider credentials
    Given a member creates a scoped Files WebDAV device credential
    Then no provider credential, provider URL, SecretRef value, bearer token value, app password, or raw downstream payload is exposed
    When the credential is revoked
    Then subsequent Files setup credential use fails support-safely
