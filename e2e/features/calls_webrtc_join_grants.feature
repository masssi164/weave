Feature: MatrixRTC Profile 0 and WebRTC media authorization
  Matrix v1.19 plus Weave MatrixRTC Profile 0 is the only member Calls signaling contract.

  @calls-matrixrtc-discovery
  Scenario: Member discovers MatrixRTC through authenticated Matrix APIs
    Given a member has Calls capability
    When the client discovers Matrix authentication and RTC transports
    Then Matrix Native OAuth and Profile 0 signaling are used
    And no member Calls REST API is advertised

  @calls-rtc-authorization
  Scenario: Matrix identity is independently authorized for an RTC slot
    Given a current Matrix OpenID credential for a room member
    When the RTC Authorizer evaluates room, slot, member, device, policy, nonce, audience, and expiry
    Then it may return a short-lived least-privilege SFU token
    And the Matrix OpenID credential itself is never accepted as media authorization

  @calls-flutter-livekit
  Scenario: Flutter joins the configured SFU through MatrixRTC
    Given a valid RTC Authorizer response
    When Flutter connects through the Profile 0 transport discovered from Matrix
    Then the participant joins the media room without a provider secret or proprietary join-grant model

  @calls-media
  Scenario: Private media requires MatrixRTC media E2EE
    Given two authorized participants joined a private call
    When audio or video is published
    Then subscribers receive encrypted media according to the MatrixRTC key lifecycle
    And DTLS-SRTP alone is not claimed as end-to-end encryption

  @calls-expired-sfu-token
  Scenario: Expired SFU token cannot be reused
    Given an SFU token has expired
    When it is reused
    Then media access is denied support-safely

  @calls-revoke-participant
  Scenario: Revoked participant loses current and future media access
    Given a participant is in a call
    When room membership or call authorization is revoked
    Then the participant is removed and the old token cannot be reused

  @calls-audit
  Scenario: RTC authorization and call lifecycle record support-safe evidence
    Given a Profile 0 call is active
    When participants join, leave, or end the slot
    Then support-safe authorization and lifecycle evidence is recorded without tokens

  @calls-provider-error-redaction
  Scenario: SFU adapter failures remain support-safe
    Given the selected SFU returns an error
    When the error crosses the RTC adapter boundary
    Then no API secret, token, provider room name, or raw provider diagnostic is exposed

  @calls-reject-legacy
  Scenario: Legacy Calls contracts are rejected
    Given a client uses a member Calls REST route, proprietary call event, join-grant shape, or older MatrixRTC snapshot
    When the request reaches a Weave boundary
    Then the request is rejected before provider invocation
    And only the pinned Profile 0 read and write shape is accepted
