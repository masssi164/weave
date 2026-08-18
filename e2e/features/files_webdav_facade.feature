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

  @files-webdav-write-mvp
  Scenario: Member writes files through the Weave WebDAV facade with guarded preconditions
    Given an authenticated member has Files edit capability in a workspace
    And the workspace has a configured Files provider behind Weave
    When the member client writes through PUT, MKCOL, or DELETE on "/dav/files"
    Then Weave enforces ETag generation and If-Match or If-None-Match preconditions at the Files facade
    And conflict, precondition, forbidden, revoked, quota, and storage failures use stable support-safe error codes
    And support-safe audit evidence records attempted and completed WebDAV mutations
    And no legacy OpenAPI Files member data endpoint becomes the fallback data plane

  @files-mcp-facade-no-provider-bypass
  Scenario: Files MCP tools cannot bypass the Files facade
    Given a governed MCP Files tool is allowed by organization policy
    When the tool searches or reads file metadata
    Then the tool operates through Weave Files facade or WebDAV-backed projection semantics
    And the tool returns canonical Weave file references and support-safe metadata
    And the tool does not accept raw provider URLs, provider credentials, downstream payloads, or unrestricted protocol commands
