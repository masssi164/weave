Feature: OIDC and protocol credential access
  First-party clients use OIDC bearer tokens and generic DAV/CalDAV clients use Weave-issued device credentials.

  @oidc-protocol-access
  Scenario: First-party authentication reaches each protocol through its native authorization boundary
    Given an authenticated member has Files, Calendar, Chat, and Calls capability
    When the member accesses WebDAV, CalDAV, Matrix, and MatrixRTC surfaces
    Then DAV requests use Weave policy and MatrixRTC uses Matrix Native OAuth plus independent RTC authorization
    And no OIDC ID token or Matrix OpenID credential is substituted for an SFU token

  @oidc-revoked-token
  Scenario: Revoked or expired bearer token fails support-safely
    Given a member token is expired or revoked
    When the token is used against any protocol surface
    Then the request is rejected
    And the error is support-safe

  @webdav-device-credential
  Scenario: Files WebDAV device credential can be issued, used, revoked, and denied after revoke
    Given a member creates a scoped Files WebDAV device credential
    When the credential is used against "/dav/files"
    Then access is attributed to the member principal
    When the credential is revoked
    Then subsequent WebDAV access fails support-safely

  @caldav-device-credential
  Scenario: Calendar CalDAV device credential can be issued, used, revoked, and denied after revoke
    Given a member creates a scoped Calendar CalDAV device credential
    When the credential is used against "/caldav"
    Then access is attributed to the member principal
    When the credential is revoked
    Then subsequent CalDAV access fails support-safely

  @no-provider-credentials
  Scenario: Device credentials never expose provider credentials
    Given device credential lifecycle responses are inspected
    Then no provider credential, provider URL, SecretRef value, bearer token value, app password, or raw downstream payload is exposed
