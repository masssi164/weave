Feature: Provider-neutral no-leakage conformance
  All external surfaces are provider-neutral and support-safe.

  @member-protocol-no-leakage
  Scenario: Member protocol responses do not leak provider internals
    Given member protocol responses for Files, Calendar, Chat, and Calls are captured
    Then no provider URL, credential, token, tenant ID, SecretRef value, raw payload, or admin diagnostic is present

  @flutter-error-no-leakage
  Scenario: Flutter-visible errors do not leak provider internals
    Given provider failures occur
    When Flutter displays the resulting errors
    Then the messages are support-safe and provider-neutral

  @native-setup-no-leakage
  Scenario: Native setup responses do not leak provider credentials
    Given Files and Calendar native setup responses are captured
    Then they contain only Weave endpoints and Weave device credentials

  @mcp-output-no-leakage
  Scenario: MCP outputs do not leak provider internals
    Given MCP tool results are captured
    Then no provider internals are exposed

  @support-diagnostics-redacted
  Scenario: Support-safe admin diagnostics redact raw downstream payloads and secrets
    Given admin health and support diagnostics are captured
    Then raw downstream payloads and secrets are redacted
