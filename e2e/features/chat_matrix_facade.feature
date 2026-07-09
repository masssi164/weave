Feature: Chat Matrix facade
  Chat data-plane behavior is exposed through the OIDC-gated Weave Matrix
  Client-Server facade backed by the shared Rust Matrix core.

  @matrix-connect
  Scenario: OIDC-provisioned member can connect to Matrix
    Given a Weave member has Chat capability
    When the member opens Chat
    Then the member reaches the Weave Matrix facade with the Weave OIDC token support-safely

  @matrix-spaces-rooms
  Scenario: Weave organization spaces and channels map to Matrix spaces and rooms
    Given Weave organization spaces and channels exist
    When the Matrix room list is synchronized
    Then Matrix spaces and rooms match Weave policy

  @matrix-message
  Scenario: Member sends and receives a Matrix message
    Given two members share a Matrix room
    When one member sends a message
    Then the other receives it through Matrix Client-Server API

  @matrix-e2ee-state
  Scenario: E2EE and device state are surfaced support-safely
    Given E2EE is enabled or pending setup
    When the member opens Chat security state
    Then device and recovery state are shown without raw secrets

  @matrix-slack-bridge-no-leak
  Scenario: Slack bridged room message does not leak Slack internals
    Given a Slack-backed bridge room exists
    When a message is bridged into Matrix
    Then no Slack token, raw payload, or provider URL is exposed

  @matrix-teams-bridge-no-leak
  Scenario: Teams bridged room message does not leak Graph internals
    Given a Teams-backed bridge room exists
    When a message is bridged into Matrix
    Then no Graph token, tenant URL, or raw payload is exposed

  @matrix-revoke
  Scenario: Revoked member or device loses Matrix access
    Given a member or device is revoked
    When it attempts Matrix access
    Then access is denied support-safely

  @flutter-matrix-boundary
  Scenario: Flutter Chat repository uses the Weave Matrix facade, not Slack or Teams direct clients
    Given Flutter Chat is exercised
    Then it uses the Rust Matrix core bridge boundary
    And it does not call Slack or Teams client APIs directly
