Feature: Files WebDAV facade data-plane boundary
  The Files capability exposes member data-plane behavior through the
  Weave-owned WebDAV facade while OpenAPI remains a control-plane surface and
  providers stay interchangeable behind server policy.

  Each scenario has a stable tag in e2e/scenario_mappings.json. The acceptance
  gate maps these scenarios to deterministic checks so this feature cannot stay
  decorative prose.

  @files-webdav-read-list-download
  Scenario: Member lists and reads files through the Weave WebDAV facade
    Given an authenticated member has Files read capability in a workspace
    And the workspace has a configured Files provider behind Weave
    When the member client lists or downloads files
    Then the request uses the Weave-owned "/dav/files" Files facade
    And OpenAPI Files calls are used only for discovery, readiness, setup, revoke, credential lifecycle, audit status, or generated control-plane models
    And the response contains canonical Weave file references and support-safe errors
    And the response does not expose provider URLs, provider credentials, raw downstream payloads, or provider-shaped member language

  @files-webdav-writes-fail-closed
  Scenario: Files writes remain blocked until WebDAV write policy is evidenced
    Given WebDAV write policy has not been specified and tested for ETag, conflict, lock, quota, revocation, and audit behavior
    When a client attempts a write-shaped Files operation through WebDAV, Flutter, or MCP
    Then Weave fails closed with a stable support-safe error
    And the delivery evidence names the write-policy follow-up issue
    And no legacy OpenAPI Files member data endpoint becomes the fallback data plane

  @files-mcp-facade-no-provider-bypass
  Scenario: Files MCP tools cannot bypass the Files facade
    Given a governed MCP Files tool is allowed by organization policy
    When the tool searches or reads file metadata
    Then the tool operates through Weave Files facade or WebDAV-backed projection semantics
    And the tool returns canonical Weave file references and support-safe metadata
    And the tool does not accept raw provider URLs, provider credentials, downstream payloads, or unrestricted protocol commands
