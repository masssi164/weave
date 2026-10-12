Feature: Historical Files WebDAV facade evidence
  These scenarios retain Core-era server integrity and authorization evidence.
  Public northbound WebDAV and Flutter WebDAV consumption are outside #1470.
  Current member Files behavior is covered by files_user_api_generated.feature.

  Each scenario has a stable tag in e2e/scenario_mappings.json. The acceptance
  gate maps these scenarios to deterministic checks so this feature cannot stay
  decorative prose.

  @files-webdav-read-list-download
  Scenario: Historical WebDAV read tests retain provider isolation evidence
    Given the Core-era Weave WebDAV controller is retained
    When its read and download tests run
    Then canonical Weave file references and support-safe errors remain protected
    And these tests do not qualify public DAV as the current Files product boundary

  @files-webdav-write-mvp
  Scenario: Historical WebDAV write tests retain precondition and audit evidence
    Given the Core-era Weave WebDAV controller is retained
    When its guarded mutation tests run
    Then preconditions, support-safe errors, and mutation audit remain protected
    And these tests do not qualify public DAV as the current Files product boundary

  @files-mcp-facade-no-provider-bypass
  Scenario: Historical MCP Files contract records provider bypass restrictions
    Given the Core-era MCP Files contract is retained
    When its archived projection is inspected
    Then raw provider URLs and unrestricted protocol commands remain prohibited
    And #1474 must prove current MCP tools against generated User operations and server policy
