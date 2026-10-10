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

  @weave-live-native-product
  Scenario: One native sign-in opens Files Calendar and Chat
    Given an admitted member has Files Calendar and Chat access in a fresh Weave installation
    When the member signs in once through the native app and system browser
    Then the member can write and read a file through Weave
    And the member can create update and delete a calendar event through Weave
    And the member can send and read a message in an authorized business room
    And those capabilities remain usable after session refresh and recoverable app and service restarts
    And explicit sign-out removes access to those capabilities
