Feature: Current organization isolation in the disposable release candidate
  A foreign organization in the same identity realm cannot use its valid
  member authority to reach the primary organization's product resources.

  @weave-live-foreign-organization-denial
  Scenario: A foreign organization owner cannot cross the Weave boundary
    Given the disposable IdP issued identities for two distinct organizations
    When the foreign owner signs in through browser Authorization Code with PKCE
    Then its generated User Files and Calendar reads and writes are denied
    And its generated Admin access to the primary control plane is denied
    And its Matrix identity and room mutation requests are denied
    And the primary resources remain unchanged
