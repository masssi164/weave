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

  @matrix-synapse-durable-collaboration
  Scenario: Encrypted collaboration is committed by the selected Chat provider
    Given author and collaborator share an encrypted conversation and outsider does not
    When both authorized members exchange unique messages through fresh Weave sessions
    Then the selected Chat provider acknowledges each committed message exactly once
    And both authorized memberships and opaque encrypted events survive backend and provider restarts
    And outsider remains absent and denied
    And shared evidence contains only support-safe correlations

  @matrix-synapse-outage-idempotency
  Scenario: A Chat provider outage cannot expose or duplicate a message
    Given an encrypted message has a stable transaction identity
    When the selected Chat provider is unavailable before acknowledgement
    Then the message is not visible as committed
    And Chat reports a member-safe unavailable state while other surfaces remain reachable
    When the provider recovers and the same transaction is retried
    Then the message is committed exactly once
    And replayed provider delivery does not duplicate the canonical timeline
