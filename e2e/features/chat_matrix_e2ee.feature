Feature: Matrix chat is end-to-end encrypted by the client Rust core
  Weave stores and relays only public keys and opaque Matrix envelopes while
  installed client devices own verification, recovery, and decryption.

  @matrix-e2ee-ciphertext-only
  Scenario: Encrypted room content is opaque to the server
    Given Alice and Bob use distinct trusted Matrix devices
    When Alice sends an encrypted room message and Bob receives it
    Then Bob decrypts the message through the Flutter Rust core
    And server persistence, audit, diagnostics, and provider adapters contain ciphertext only
    And the room rejects a plaintext message write

  @matrix-e2ee-iphone-relaunch
  Scenario: Closing and updating the iPhone preserves encrypted chat state
    Given Alice has decrypted encrypted history on a physical iPhone
    When the app is terminated and relaunched after an app update
    Then the installed profile and OIDC refresh session are restored
    And the same Matrix device and encrypted Rust store are restored
    And existing encrypted history remains decryptable

  @matrix-e2ee-two-device-verification
  Scenario: A member verifies a second device accessibly
    Given Alice signs in on a second Matrix device
    When Alice verifies it from the trusted iPhone with SAS
    Then both devices show matching numeric and labelled emoji alternatives
    And the flow is keyboard and screen-reader operable
    And the second device is cross-signed without server-held private keys

  @matrix-e2ee-recovery
  Scenario: Recovery restores encrypted history on a new device
    Given Alice enabled cross-signing and room-key backup
    When Alice recovers on a new device with her recovery secret
    Then the Rust core restores secret storage and backed-up room keys
    And retained encrypted history becomes decryptable
    And recovery material is absent from server and support evidence

  @matrix-e2ee-lost-device
  Scenario: Revoking a lost device preserves encryption for remaining devices
    Given Alice has a trusted iPhone and a lost second device
    When Alice removes the lost device and rotates affected sessions
    Then the lost device cannot fetch new keys or decrypt new messages
    And its OIDC refresh session cannot rename itself as a new Matrix device
    And remaining devices continue without a plaintext downgrade

  @matrix-e2ee-provider-switch
  Scenario: Provider replacement accounts for encrypted history
    Given an admin dry-runs Chat provider replacement for encrypted conversations
    When the target cannot preserve encrypted events and key envelopes
    Then encrypted history is classified as lossy, unsupported, or archive_only
    And production apply remains blocked without server-side decryption

  @matrix-e2ee-fail-closed-client
  Scenario: A client without E2EE capability cannot downgrade a room
    Given a Matrix client lacks validated verification and recovery support
    When it attempts to enter an encrypted conversation
    Then Weave denies the encrypted capability support-safely
    And no plaintext compatibility room or server-side decryption is offered
