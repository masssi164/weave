Feature: Calls WebRTC join grants
  Calls use Weave control APIs and WebRTC media through LiveKit.

  @calls-create
  Scenario: Member creates a Weave call
    Given a member has Calls capability
    When the member creates a call
    Then a Weave call object is created and audited

  @calls-join-grant
  Scenario: Member receives short-lived scoped LiveKit join grant
    Given a Weave call exists
    When the member requests to join
    Then the response contains a short-lived scoped join grant
    And no LiveKit API secret or raw provider diagnostic is exposed

  @calls-flutter-livekit
  Scenario: Flutter joins LiveKit room using Weave join grant
    Given a valid join grant
    When Flutter connects through livekit_client
    Then the participant joins the media room

  @calls-media
  Scenario: Participant can publish and subscribe to media
    Given two participants joined the call
    When audio or video is published
    Then other participants can subscribe according to the grant

  @calls-expired-grant
  Scenario: Expired join grant cannot be reused
    Given a join grant has expired
    When it is reused
    Then media access is denied support-safely

  @calls-revoke-participant
  Scenario: Revoked participant is removed and cannot rejoin with old grant
    Given a participant is in a call
    When the participant is removed
    Then the participant loses access and the old grant cannot be reused

  @calls-audit
  Scenario: Call leave and end record support-safe audit
    Given a call is active
    When participants leave and the call ends
    Then support-safe audit events are recorded

  @calls-provider-error-redaction
  Scenario: LiveKit provider errors are support-safe
    Given LiveKit returns an error
    When the error crosses the Weave boundary
    Then no API secret or raw provider diagnostic is exposed

  @calls-matrix-projection
  Scenario: Optional Matrix call event projection is support-safe when enabled
    Given Matrix call projection is enabled
    When a call starts or ends in a room
    Then a support-safe Matrix event or summary is projected
