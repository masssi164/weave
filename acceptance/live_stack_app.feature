Feature: Live Stack app collaboration journey
  The Live Stack app E2E remains the executable proof that a user can sign in once
  and use Weave-owned product surfaces for chat, files, calendar, and boards.
  These scenarios are mapped to integration_test/live_stack_app_e2e_test.dart and
  guarded by test/live_stack_feature_mapping_test.dart so the readable scenario
  layer cannot drift into decorative documentation only.

  @weave-live-auth-shell
  Scenario: Auth sign-in restores the Weave workspace shell and profile facade
    Given the local stack exposes the canonical Weave API and Keycloak issuer
    When the live test user signs in through the Weave app session
    Then the authenticated backend session is restored
    And the profile facade can load, update, reload, and restore the display name

  @weave-live-matrix-e2ee
  Scenario: Matrix chat sends messages and proves E2EE posture honestly
    Given the signed-in user has a Matrix client from Weave platform config
    When the user creates a room and sends a message through the Weave chat repository
    Then the message is readable from the Weave timeline
    And an encrypted room emits authoritative encrypted Matrix wire events without plaintext leakage
    And recovery bootstrap and key storage posture are reported in the E2EE result evidence

  @weave-live-files-boundary
  Scenario: Files are browsed, uploaded, and downloaded through the Weave product facade
    Given the signed-in user opens the Weave Files surface
    When the user uploads a unique file through the backend files facade
    Then the Weave Files listing shows the file
    And downloading it through the facade returns the original bytes
    And the test cleans up the uploaded file without depending on raw Nextcloud UI

  @weave-live-calendar-threadrefs
  Scenario: Channel calendar events round trip with stable meeting thread references
    Given workspace, team, and channel calendar scopes are available
    When the user creates, reads, updates, and deletes a channel-scoped event
    Then the event remains in the channel scope
    And the meeting thread reference is present and stable across the update

  @weave-live-boards-preview-nondrag
  Scenario: Boards preview stays provider-neutral and supports non-drag task operations
    Given the Boards preview runtime is enabled for the local Weave backend facade
    When the user reads the preview, creates a task, moves it, and completes it without drag-and-drop
    Then the preview reports the local provider-neutral backend source
    And the task reaches the completed column through Weave API routes
    And the checked-in Boards accessibility tests cover screen-reader summaries, tap targets, and large text without adding a flaky live-stack dependency
