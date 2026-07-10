Feature: Native OS integration readiness
  Native Files and Calendar integrations use Weave protocol endpoints and Weave credentials.

  @native-files-webdav
  Scenario: Files native setup returns Weave WebDAV endpoint and Weave device credentials only
    Given a member requests Files native setup
    Then the response contains a Weave WebDAV endpoint
    And it contains Weave device credential setup
    And it contains no provider credential or provider URL

  @native-calendar-caldav
  Scenario: Calendar native setup returns Weave CalDAV endpoint and Weave credential/profile only
    Given a member requests Calendar native setup
    Then the response contains a Weave CalDAV endpoint
    And it contains Weave device credential or signed profile setup
    And it contains no provider credential or provider URL

  @native-revoke
  Scenario: Revoking a native credential disables OS integration access
    Given a native Files or Calendar credential exists
    When the credential is revoked
    Then OS protocol access is denied support-safely

  @native-accessibility
  Scenario: Native setup responses are screenreader-friendly and support-safe
    Given native setup copy is rendered in Flutter
    Then instructions are provider-neutral, concise, and screenreader-friendly
