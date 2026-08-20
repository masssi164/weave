Feature: Files full WebDAV facade
  Files data-plane behavior is exposed through Weave-owned WebDAV at /dav/files.

  @files-webdav-propfind
  Scenario: PROPFIND Depth 0 and 1 list Weave-owned resources with WebDAV properties
    Given an authenticated member has Files read capability
    When the member sends PROPFIND Depth 1 to "/dav/files"
    Then the response is 207 Multi-Status
    And resources include Weave-owned hrefs, getetag, displayname, resourcetype, content metadata, and lock properties
    And no provider URL, provider credential, or raw downstream payload is exposed

  @files-webdav-basicsearch
  Scenario: SEARCH executes the bounded canonical RFC 5323 profile
    Given an authenticated member has Files read capability
    And the selected adapter has current files.webdav_basicsearch qualification
    When the member discovers and submits DAV basicsearch through "/dav/files"
    Then OPTIONS advertises SEARCH and DASL DAV basicsearch
    And the 207 response contains deterministic Weave hrefs, requested 200 and 404 property statuses, and canonical FileIds
    And a partial bounded result contains one 507 response for the search arbiter
    And malformed, unsafe, unsupported, unauthorized, or escaping input fails before provider access
    And no provider URL, provider credential, raw downstream payload, or content bytes are exposed

  @files-webdav-get-head
  Scenario: GET and HEAD stream file content with Weave ETags
    Given a file exists behind the Files facade
    When the member sends GET and HEAD through "/dav/files"
    Then content metadata and Weave ETags are returned support-safely
    And GET streams an exactly verified bounded representation
    And HEAD and an unchanged conditional read open no content body

  @files-webdav-put-create
  Scenario: PUT creates a file with If-None-Match and records audit
    Given no file exists at the target path
    When the member sends a fixed-length or chunked PUT with If-None-Match "*"
    Then the file is created through the Files facade
    And invalid framing, unsupported coding, capacity pressure, and oversize fail before mutation
    And attempted and completed audit events are recorded

  @files-webdav-put-stale
  Scenario: PUT update rejects stale ETags
    Given a file exists with a Weave ETag
    When the member sends PUT with a stale If-Match
    Then the request fails with a stable precondition error
    And no storage mutation occurs

  @files-webdav-mkcol
  Scenario: MKCOL creates a folder and rejects duplicates or missing parents
    Given an authenticated member has Files edit capability
    When the member sends MKCOL
    Then folder creation, duplicate name, and missing parent behavior are support-safe

  @files-webdav-delete
  Scenario: DELETE removes a resource with If-Match and records audit
    Given a file or folder exists
    When the member sends DELETE with a valid If-Match
    Then the resource is removed
    And attempted and completed audit events are recorded

  @files-webdav-move
  Scenario: MOVE handles Destination and Overwrite without provider leakage
    Given a source resource exists
    When the member sends MOVE with Destination and Overwrite headers
    Then Weave enforces conflict and overwrite semantics
    And no provider internals are exposed

  @files-webdav-copy
  Scenario: COPY handles Destination and Overwrite without provider leakage
    Given a source resource exists
    When the member sends COPY with Destination and Overwrite headers
    Then Weave enforces conflict and overwrite semantics
    And no provider internals are exposed

  @files-webdav-lock-unlock
  Scenario: LOCK exposes lockdiscovery and blocks conflicting writes with 423
    Given a resource exists
    When the member locks the resource
    Then lockdiscovery exposes a Weave lock token
    When another write conflicts with the lock
    Then the response is 423 Locked
    When the lock is unlocked
    Then subsequent writes can proceed

  @files-webdav-quota
  Scenario: Quota exceeded maps to 507
    Given the storage quota is exhausted
    When the member writes through WebDAV
    Then the response is 507 Insufficient Storage
    And the error is support-safe

  @files-flutter-webdav
  Scenario: Flutter Files repository uses /dav/files for all file data-plane operations
    Given the Flutter Files repository is exercised
    Then list, download, upload, create folder, delete, move, and copy use the Weave WebDAV facade
    And OpenAPI Files data-plane routes are not used
