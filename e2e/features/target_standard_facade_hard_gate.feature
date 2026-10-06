Feature: Target standard facade hard gate
  Historical northbound DAV implementation evidence is retained without
  making public DAV the current product contract. Current Files and Calendar
  need the generated User API. Matrix remains the Weave Client-Server facade,
  with Flutter's native Rust/Matrix SDK and a separate server Rust/Ruma/JNI
  wire boundary. Offline checks do not establish integrated interoperability.

  @target-standards-webdav-files-current-proof
  Scenario: Files WebDAV proof is separated from remaining client and native cutover
    # Evidence marker: TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF
    Given the historical Core Files implementation used WebDAV under "/dav/files"
    When the hard-gate audit checks executable evidence
    Then legacy server evidence still covers WebDAV read, download, write, precondition, and authorization behavior
    And this evidence does not qualify public DAV as the #1470 Files product boundary
    And generated User API Files operations require independent #1473 implementation and tests

  @target-standards-caldav-calendar-server-mvp
  Scenario: Calendar CalDAV server MVP is separated from native sync parity
    # Evidence marker: TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP
    Given the historical Core Calendar implementation used CalDAV and iCalendar under "/caldav"
    When the hard-gate audit checks executable evidence
    Then legacy server tests retain CalDAV recurrence, precondition, authorization, and support-safe error behavior
    And this evidence does not qualify public CalDAV as the #1470 Calendar product boundary
    And generated User API Calendar operations require independent #1473 implementation and tests

  @target-standards-matrix-chat-server-mvp
  Scenario: Matrix Chat MVP is separated from full protocol and E2EE parity
    # Evidence marker: TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP
    Given Chat uses the OIDC-gated Weave Matrix Client-Server facade as the target data plane
    When the hard-gate audit checks executable evidence
    Then Flutter uses its native Rust/Matrix SDK against the Weave facade without a proprietary REST message data plane
    And the server Matrix projection supports whoami, sync, joined rooms, room messages, and send through the canonical Chat facade without provider payloads
    And obsolete Chat REST conversation and message routes remain absent from OpenAPI and runtime routing
    And the versioned support profile guards every capability until protocol and real Weave-owned client evidence qualifies it
    And independent third-party Matrix-client OAuth and interoperability remain deferred
