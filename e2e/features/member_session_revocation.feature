Feature: Member session revocation preserves identity and authorization

  @weave-live-member-session-revocation
  Scenario: Revoked sessions fail closed and later sign-in restores the same member
    Given an active organization owner has separate User and Admin sessions with current authorization
    When an authorized administrator revokes that member's sessions
    Then retained unexpired User Admin and Matrix bearers are denied
    And the revoked refresh credentials cannot restore access
    When the member reauthorizes through normal browser OIDC with PKCE
    Then the same identity and unchanged member permissions become usable again
    And replaying the completed revocation does not revoke the new sessions
    And logout before Chat initialization denies retained and refreshed member bearers
