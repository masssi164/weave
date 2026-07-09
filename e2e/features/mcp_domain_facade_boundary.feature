Feature: MCP domain facade boundary
  MCP tools use Weave domain facades and never raw provider APIs.

  @mcp-files-facade
  Scenario: MCP Files tools use Weave Files facade semantics
    Given MCP Files tools are enabled
    When a Files tool reads or writes file data
    Then it uses Weave Files facade semantics
    And it does not accept raw provider URLs or unrestricted WebDAV scripting

  @mcp-calendar-facade
  Scenario: MCP Calendar tools use Weave Calendar facade semantics
    Given MCP Calendar tools are enabled
    When a Calendar tool reads or writes calendar data
    Then it uses Weave Calendar facade semantics
    And it does not expose raw provider payloads

  @mcp-chat-facade
  Scenario: MCP Chat tools use Weave or Matrix-governed semantics
    Given MCP Chat tools are enabled
    When a Chat tool sends or reads messages
    Then it uses governed Weave/Matrix semantics
    And it does not call Slack or Teams raw APIs directly

  @mcp-calls-facade
  Scenario: MCP Calls tools use Weave call grants
    Given MCP Calls tools are enabled
    When a Calls tool creates or joins a call
    Then it uses Weave Calls control semantics
    And it never exposes LiveKit admin APIs

  @mcp-no-provider-leakage
  Scenario: MCP outputs contain no provider internals
    Given MCP outputs are inspected
    Then no raw provider URL, credential, token, tenant ID, SecretRef value, or downstream payload is present
