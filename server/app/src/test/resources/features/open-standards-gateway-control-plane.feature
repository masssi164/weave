Feature: Weave northbound access boundaries
  Weave advertises its generated User API and guarded Matrix profile without exposing deferred public DAV or Calls surfaces.

  Scenario: Authenticated member discovers release access surfaces
    Given an authenticated member has a valid Weave OIDC session
    When the member loads the organization manifest
    Then Files advertises the guarded generated User API without public WebDAV
    And Calendar advertises the guarded generated User API without public CalDAV
    And Chat advertises the guarded Weave Matrix Client-Server endpoint
    And Calls is absent from member access discovery
    And no provider URL, provider credential, raw provider payload, SecretRef value, or admin diagnostic is exposed

  Scenario: OpenAPI retains domain boundaries
    Given the OpenAPI contract is generated
    When the contract is inspected for Files, Calendar, and Chat
    Then it contains generated User domain entrypoints
    And it excludes public DAV and proprietary Chat REST routes

  Scenario: Files setup credentials return a Weave secret once without exposing provider credentials
    Given a member creates a scoped Files WebDAV device credential
    Then no provider credential, provider URL, SecretRef value, bearer token value, app password, or raw downstream payload is exposed
    When the credential is revoked
    Then subsequent Files setup credential use fails support-safely
