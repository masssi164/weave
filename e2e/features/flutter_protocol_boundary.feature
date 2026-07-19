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

  @flutter-calls-matrixrtc
  Scenario: Flutter Calls uses MatrixRTC Profile 0 and a replaceable RTC transport
    Given Flutter Calls repository operations are exercised
    Then call signaling uses Matrix v1.19 and the pinned MatrixRTC Profile 0
    And transport access is obtained only through the RTC Authorizer without provider credentials

  @flutter-openapi-control-only
  Scenario: Flutter uses OpenAPI only for control surfaces
    Given Flutter network calls are inspected
    Then OpenAPI is used only for control, discovery, readiness, revoke, manifest, and generated convenience models
