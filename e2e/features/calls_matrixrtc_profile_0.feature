Feature: Matrix-native Calls through MatrixRTC Profile 0
  Calls use Matrix Native OAuth and one revision-pinned MatrixRTC profile.
  The capability remains Guarded until live interoperability and device evidence passes.

  @calls-matrix-native-oauth
  Scenario: Foreign client discovers Matrix Native OAuth
    Given a member starts with only the organization URL
    When the client discovers the Matrix homeserver and authorization metadata
    Then MAS authenticates through upstream Keycloak using Authorization Code and PKCE S256
    And no Weave Calls endpoint is required

  @calls-profile-exact
  Scenario: Client accepts the exact Profile 0 wire shape
    Given the homeserver advertises MatrixRTC Profile 0
    When the client reads slot membership transport and media key events
    Then only the revision-pinned Profile 0 shape is accepted

  @calls-legacy-shape-rejected
  Scenario: Legacy MatrixRTC shape fails closed
    Given a peer sends an older membership transport or unstable alias shape
    When the Profile 0 reader validates the event
    Then the event is rejected without a compatibility translation

  @calls-rtc-authorizer
  Scenario: RTC Authorizer separates identity from room authorization
    Given a member presents a valid Matrix OpenID credential
    When current room slot member role or organization policy does not match
    Then transport authorization is denied support-safely

  @calls-short-grant
  Scenario: RTC Authorizer issues a short-lived least-privilege transport grant
    Given identity room slot member role policy and replay checks pass
    When the member joins the MatrixRTC call
    Then the LiveKit grant is short-lived permission-bound and nonce-bound
    And no provider administration credential is exposed

  @calls-media-e2ee
  Scenario: Private media requires MatrixRTC media E2EE
    Given the call is private
    When media is published through the SFU
    Then the Matrix room and MatrixRTC media are end-to-end encrypted
    And DTLS-SRTP alone is not accepted as an E2EE claim against the SFU

  @calls-native-coordinator
  Scenario: Native call coordinator remains a thin OS projection
    Given MatrixRTC call state changes
    When CallKit or Android Core-Telecom reports answer decline end hold or mute
    Then the coordinator maps the action idempotently without owning signaling or authorization

  @calls-consent
  Scenario: Recording and transcription remain consent-gated
    Given recording and transcription are disabled by default
    When an administrator proposes enabling either capability
    Then participant-visible consent decryption retention export and deletion evidence is required

  @calls-no-proprietary-api
  Scenario: Member Calls contract has no proprietary route or event
    Given the MatrixRTC strict cutover is active
    When server OpenAPI generated clients and runtime sources are inspected
    Then no member Calls route join-grant model or com.weave.call event exists
