Feature: Calendar events through the generated Weave product API
  Current member scheduling preserves temporal intent, stable references and access.
  Live evidence requires the isolated product journey, including provider restart.

  @calendar-user-api-runtime
  Scenario: Shared scheduling preserves time and access after restart
    Given an author and collaborator may schedule in the workspace
    And another member has no access to that workspace
    When they create and update all-day, floating, UTC and zoned recurring events
    Then event times remain correct across the daylight-saving transition
    And retrying a create preserves its event identity
    And stale changes cannot overwrite a newer version
    And the other member cannot discover, read, create, change or delete these events
    And the same event identities, scopes, meeting references and content survive restart
    And the author can remove the events using their current versions

  @calendar-flutter-generated-user
  Scenario: Calendar app uses the shared member session and preserves scheduling intent
    Given the member has signed into Weave and may use Calendar
    When the app discovers calendars and reads the selected agenda
    Then scheduling uses the generated product contract and opaque calendar references
    And edits preserve all-day, floating, UTC and zoned times and meeting references
    And create retries keep one event identity and stale updates remain rejected
    And changing the account or workspace cannot reveal an earlier session's events
