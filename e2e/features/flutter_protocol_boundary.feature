Feature: Flutter protocol boundary
  Flutter product repositories use Weave-owned protocol facades.

  @flutter-files-webdav
  Scenario: Flutter Files uses WebDAV for data plane
    Given Flutter Files repository operations are exercised
    Then file data-plane operations use "/dav/files"

  @flutter-calendar-caldav
  Scenario: Flutter Calendar uses CalDAV and iCalendar for data plane
    Given Flutter Calendar repository operations are exercised
    Then calendar data-plane operations use Weave CalDAV/iCalendar

  @flutter-chat-matrix
  Scenario: Flutter Chat uses Matrix for data plane
    Given Flutter Chat repository operations are exercised
    Then chat data-plane operations use the Weave Matrix facade and Rust Matrix core bridge boundary

  @flutter-calls-livekit
  Scenario: Flutter Calls uses Weave Calls API and LiveKit media
    Given Flutter Calls repository operations are exercised
    Then call control uses Weave Calls API
    And media uses LiveKit join grants

  @flutter-openapi-control-only
  Scenario: Flutter uses OpenAPI only for control surfaces
    Given Flutter network calls are inspected
    Then OpenAPI is used only for control, discovery, readiness, revoke, manifest, and generated convenience models
