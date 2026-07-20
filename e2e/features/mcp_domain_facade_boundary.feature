Feature: MCP domain facade boundary
  MCP tools use Weave domain facades and never raw provider APIs.

  @mcp-files-facade
  Scenario: MCP Files tools use Weave Files facade semantics
    Given MCP Files tools are enabled
    When a Files tool searches or reads file metadata
    Then it uses Weave Files facade semantics
    And it does not accept raw provider URLs or unrestricted WebDAV scripting

  @mcp-calendar-facade
  Scenario: MCP Calendar tools use Weave Calendar facade semantics
    Given MCP Calendar tools are enabled
    When a Calendar tool searches calendar data
    Then the read uses Weave Calendar facade semantics
    And it does not expose raw provider payloads
    And calendar writes stay unavailable until trusted approval evidence is implemented

  @mcp-chat-facade
  Scenario: MCP Chat tools use Weave or Matrix-governed semantics
    Given the canonical Chat send tool exists in the fixed catalog
    When runtime-approved discovery is evaluated
    Then the write is not advertised until trusted approval evidence is implemented
    And its future dispatch remains governed by Weave and Matrix semantics
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

  @spring-ai-mcp-transport
  Scenario: MCP keeps the Spring AI transport dark until workload binding exists
    Given the Spring AI Streamable HTTP implementation is installed
    When a human token or unbound service account initializes at /mcp
    Then Spring Security rejects it before the MCP transport
    And the handwritten JSON-RPC and Python FastMCP runtimes are absent

  @spring-ai-mcp-oidc
  Scenario: OIDC admits no human or unbound workload compatibility path
    Given an MCP request carries a human token or an unbound service-account token
    When the request reaches /mcp
    Then Spring Security rejects it before MCP tool or backend dispatch
    And only a future server-owned service-account to cell to RuntimeProfile v2 binding may open the edge

  @mcp-runtime-approved-discovery
  Scenario: Runtime-approved MCP discovery remains unavailable without ARC binding
    Given the ARC workload binding implementation is not yet present
    When any caller attempts to initialize or discover MCP tools
    Then the MCP edge remains dark
    And no obsolete member runtime profile or approved-tools compatibility path is exposed

  @mcp-approval-ownership-boundary
  Scenario: MCP writes fail closed without trusted OpenClaw approval evidence
    Given a governed Weaver write tool requires approval
    When the runtime invokes the tool through Spring AI MCP
    Then OpenClaw remains the owner of approval presentation and decision state
    And Weave does not mint authority from caller-supplied elicitation evidence
    And the write stays unavailable until trusted approval evidence and current domain authorization can both be validated
