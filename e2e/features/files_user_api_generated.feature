Feature: Generated Files User API member boundary
  This offline contract check asserts the generated transport and independently
  tested client behavior. Live authorization and provider-backed proof remains
  required before #1473 or #1480 can close.

  @files-user-api-generated-client
  Scenario: Flutter uses generated Files operations and stable Weave references
    Given the server exports Files User operations with opaque Weave file IDs
    When an authorized member lists, downloads, creates a folder, or uploads
    Then Flutter calls the generated User client with its Weave session
    And the client validates bounded content, digest, ETag, and upload source failures
    And unsupported mutations or denied actions do not fall back to public DAV or provider URLs
