Feature: Target standard facade hard gate
  Matrix, WebDAV, and CalDAV target-standard claims must be separated from
  the current executable proof. A green mapping gate is not enough when server
  protocol behavior exists but client/native cutover still has explicit
  delivery debt.

  @target-standards-webdav-files-current-proof
  Scenario: Files WebDAV proof is separated from remaining client and native cutover
    # Evidence marker: TARGET_STANDARDS_WEBDAV_FILES_CURRENT_PROOF
    Given Files uses WebDAV under "/dav/files" as the target data plane
    When the hard-gate audit checks executable evidence
    Then server evidence covers WebDAV read, download, and write semantics under "/dav/files"
    And Flutter read and download paths use "/dav/files" instead of OpenAPI member data-plane routes
    And remaining native-provider and client write cutover work stays linked to #969 and #1018 before a complete native Files claim

  @target-standards-caldav-calendar-server-mvp
  Scenario: Calendar CalDAV server MVP is separated from native sync parity
    # Evidence marker: TARGET_STANDARDS_CALDAV_CALENDAR_SERVER_MVP
    Given Calendar uses CalDAV and iCalendar under "/caldav" as the target data plane
    When the hard-gate audit checks executable evidence
    Then server evidence covers OPTIONS, PROPFIND, REPORT calendar-query, REPORT free-busy-query, GET, PUT, and DELETE under "/caldav"
    And missing storage still fails closed with stable support-safe Calendar errors
    And native Calendar setup advertises Weave-owned "/caldav" paths while iOS and Android setup remain unavailable until #967 and #1018 close

  @target-standards-matrix-chat-server-mvp
  Scenario: Matrix Chat server MVP is separated from client and API retirement parity
    # Evidence marker: TARGET_STANDARDS_MATRIX_CHAT_SERVER_MVP
    Given Chat uses the Matrix Client-Server API as the target data plane
    When the hard-gate audit checks executable evidence
    Then the member Chat path uses the Matrix projection rather than a normal REST message data plane
    And the server Matrix projection supports sync, joined rooms, room messages, and send through the canonical Chat facade without provider payloads
    And full Matrix Client-Server parity, raw homeserver cutover, and Chat API-first retirement stay linked to #1017 and #1022 before a complete Chat claim
