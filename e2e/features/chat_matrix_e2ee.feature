Feature: Matrix chat is end-to-end encrypted by the client Rust core
  Weave stores and relays only public keys and opaque Matrix envelopes while
  installed client devices own verification, recovery, and decryption.
  Physical-device verification, recovery, relaunch, and lost-device behavior
  are release gates, not automated credential-driven Flutter scenarios.

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
