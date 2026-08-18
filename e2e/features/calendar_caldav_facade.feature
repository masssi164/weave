Feature: Calendar CalDAV facade
  Calendar data-plane behavior is exposed through Weave-owned CalDAV and iCalendar.

  @caldav-discovery
  Scenario: CalDAV well-known discovery and principal discovery work
    Given an authenticated member has Calendar capability
    When the member discovers CalDAV endpoints
    Then well-known and principal discovery return Weave-owned URLs only

  @caldav-home
  Scenario: Calendar home lists calendar collections
    Given the member has calendars
    When the member sends PROPFIND to the calendar home
    Then calendar collections and properties are returned support-safely

  @caldav-calendar-query
  Scenario: calendar-query returns VEVENT objects for a date range
    Given events exist in a calendar
    When the member sends REPORT calendar-query with a date range
    Then matching VEVENT resources are returned as valid iCalendar

  @caldav-calendar-multiget
  Scenario: calendar-multiget returns selected calendar objects
    Given selected event hrefs exist
    When the member sends REPORT calendar-multiget
    Then the selected iCalendar objects are returned

  @caldav-get-put-delete
  Scenario: GET, PUT, and DELETE use ETags and stable precondition handling
    Given a calendar object exists
    When the member reads, updates, and deletes it through CalDAV
    Then ETags and conflict behavior are enforced support-safely

  @caldav-sync
  Scenario: sync-collection returns changed objects after mutation
    Given the client has a sync token
    When a calendar object changes
    Then REPORT sync-collection returns the changed object and a new sync token

  @caldav-freebusy
  Scenario: free-busy-query returns VFREEBUSY
    Given events exist for a time range
    When the member sends REPORT free-busy-query
    Then the response contains valid VFREEBUSY information

  @caldav-recurrence-dst
  Scenario: Recurring event across DST boundary is expanded consistently
    Given a recurring event crosses a DST boundary
    When the member queries the affected range
    Then recurrence expansion is deterministic and timezone-safe

  @caldav-canonical-thread
  Scenario: Channel event keeps one canonical meeting-thread reference
    Given a member creates a channel calendar event through CalDAV
    When the event is queried, read, and updated through the Calendar facade
    Then its iCalendar projection keeps the same Weave context, channel, and meeting-thread identifiers

  @calendar-flutter-caldav
  Scenario: Flutter Calendar repository uses CalDAV and iCalendar for calendar data
    Given the Flutter Calendar repository is exercised
    Then calendar list, event query, event read, event write, delete, and free/busy use Weave CalDAV/iCalendar
