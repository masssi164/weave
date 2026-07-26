Feature: MCP domain facade boundary
  MCP is a workload-only protocol edge. A domain projection opens only when its owning domain has a complete, independently authorized read or action contract.

  @mcp-files-facade
  Scenario: MCP Files read slice projects the existing authorized WebDAV facade
    Given Files remains authoritative for its own content and authorization
    When runtime-approved MCP discovery is evaluated
    Then files.search and canonical file resources use bounded WebDAV SEARCH and GET
    And no raw provider URL or unrestricted WebDAV scripting surface is exposed

  @mcp-calendar-facade
  Scenario: MCP Calendar catalog stays empty until the Calendar action contract is complete
    Given Calendar remains authoritative for its own events and authorization
    When runtime-approved MCP discovery is evaluated
    Then no Calendar tool is advertised before its domain catalog and current authorization gates exist
    And no raw provider payload or provider-shaped calendar operation is exposed

  @mcp-chat-facade
  Scenario: MCP Chat catalog stays empty until the Chat action contract is complete
    Given Chat and Matrix remain authoritative for conversation authorization
    When runtime-approved discovery is evaluated
    Then no Chat tool is advertised before its domain catalog and current authorization gates exist
    And no Slack or Teams raw API is exposed

  @mcp-calls-facade
  Scenario: MCP Calls catalog stays empty until MatrixRTC authorization is current
    Given the Calls domain uses the pinned MatrixRTC Profile 0 member contract
    When runtime-approved MCP discovery is evaluated
    Then no Calls tool is advertised before current RTC authorization and action-evidence gates exist
    And no member Calls API, proprietary join grant, or LiveKit admin API is exposed

  @mcp-no-provider-leakage
  Scenario: MCP protocol metadata contains no provider internals
    Given MCP protocol metadata is inspected while domain catalogs are empty
    Then no raw provider URL, credential, token, tenant ID, SecretRef value, or downstream payload is present

  @spring-ai-mcp-transport
  Scenario: MCP admits only a current ARC-bound workload
    Given the Spring AI Streamable HTTP implementation is installed
    When a bound cell negotiates the MCP Client Credentials extension at /mcp
    Then the edge exchanges its exact-audience workload token and resolves current ARC context before protocol dispatch
    And a human token or unbound service account is rejected
    And the handwritten JSON-RPC and Python FastMCP runtimes are absent

  @spring-ai-mcp-oidc
  Scenario: OIDC admits no human or unbound workload compatibility path
    Given an MCP request carries a human token or an unbound service-account token
    When the request reaches /mcp
    Then Spring Security rejects it before MCP tool or backend dispatch
    And only a server-owned service-account to cell to RuntimeProfile v2 binding may enter the transport

  @mcp-runtime-approved-discovery
  Scenario: Runtime-approved MCP workload reaches the standard Server projection while other catalogs stay guarded
    Given ARC has a current workload binding and RuntimeProfile v2
    When the bound cell initializes MCP and invokes the Files search tool
    Then the edge exchanges its workload token and calls the standard WebDAV projection
    And no domain tool is advertised before its catalog, authorization, and evidence gates are implemented
    And no obsolete member runtime profile or approved-tools compatibility path is exposed

  @mcp-approval-ownership-boundary
  Scenario: MCP write catalogs remain empty without trusted OpenClaw decision evidence
    Given a candidate ARC workload domain write would require approval
    When the runtime initializes Spring AI MCP
    Then OpenClaw remains the owner of approval presentation and decision state
    And Weave does not mint authority from caller-supplied elicitation evidence
    And no write tool is advertised until trusted decision evidence and current domain authorization can both be validated
