Feature: Physical client authentication acceptance
  The Flutter client proves the same public OIDC flow used by real members.
  It never receives a username, password, bearer token, or provider credential
  through a build argument. Keycloak owns activation and authentication.

  @weave-live-auth-shell
  Scenario: Activation and sign-in restore the workspace and refresh session
    Given a member invitation was created through the Weave product flow
    And the current candidate is installed on a physical device
    When the member activates the account and signs in through the system browser
    Then the Flutter client returns to the normal Weave workspace
    And the client refreshes the OIDC session through the production AppAuth integration
    And no human credential is written to source, evidence, or Flutter build arguments
