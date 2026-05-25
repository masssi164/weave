Feature: Live Stack product acceptance journey
  The Live Stack app E2E is the sparse executable proof that a person can sign in
  once and use Weave-owned product surfaces for profile, chat, files, calendar,
  and boards. These scenarios are product contracts, not implementation notes.

  Each scenario has a stable tag in e2e/scenario_mappings.json. The mapping
  guard fails if a scenario is not connected to an executable test and evidence
  marker, so this readable layer cannot drift into decorative BDD.

  @weave-live-auth-shell
  Scenario: Sign-in restores the Weave workspace and profile
    Given the Weave workspace is ready for the live test person
    When the person signs in through Weave
    Then Weave restores the signed-in workspace
    And the person can load, edit, reload, and restore their profile name

  @weave-live-matrix-content
  Scenario: Matrix chat sends and reads a workspace message
    Given the signed-in person has the Weave chat surface available
    When the person creates a conversation and sends a message
    Then the message is readable in Weave chat
    And Weave reports the chat connection outcome honestly

  @weave-live-matrix-e2ee
  Scenario: Matrix encryption status is proved honestly
    Given the signed-in person has encryption support available for chat
    When the person sends a message in an encrypted conversation
    Then Weave observes encrypted message evidence without plaintext leakage
    And Weave reports recovery and key-storage readiness honestly

  @weave-live-files-boundary
  Scenario: Files are uploaded, shown, downloaded, and cleaned up in Weave
    Given the signed-in person opens Weave Files
    When the person uploads a unique file
    Then Weave shows the uploaded file
    And downloading the file returns the original content
    And the test removes the file it created

  @weave-live-provider-stack-readiness
  Scenario: Provider stack readiness stays backend-owned and support-safe
    Given the signed-in person has optional providers represented by Weave
    When the app checks provider readiness boundaries
    Then raw provider registry diagnostics are denied to member tokens
    And member readiness is exposed only through backend-owned facades
    And no provider secrets or direct Flutter provider calls are exposed

  @weave-live-calendar-threadrefs
  Scenario: Channel calendar events keep their meeting thread reference
    Given workspace, team, and channel calendar scopes are available in Weave
    When the person creates, reads, updates, and deletes a channel event
    Then the event stays in its channel scope while it exists
    And the meeting thread reference is present and stable after the update

  @weave-live-boards-workspace-nondrag
  Scenario: Boards workspace supports accessible non-drag task work
    Given the Boards workspace is available in Weave
    When the person creates, moves, and completes a task without drag-and-drop
    Then the board still uses Weave product task concepts
    And mapped accessibility evidence covers screen-reader summaries, tap targets, large text, and action-menu alternatives
