Feature: Enterprise dogfood readiness is proven across users and delivery gates
  Automated activation, OIDC, server, MCP, provider access, and cleanup are
  owned by testApp. Interactive Flutter and accessibility evidence is captured
  only on a physical device through the production system-browser flow.

  @human-ready-legacy-upgrade
  Scenario: Legacy first-run state cannot return the obsolete screen
    Given a signed-in client is upgraded with former first-run preferences and secure state
    When application bootstrap restores the organization and session
    Then the member enters the normal application shell
    And legacy state is ignored or removed
    And no first-run route copy provider or server endpoint exists

  @human-ready-dogfood-deployment
  Scenario: Persistent dogfood deployment is non-destructive and idempotent
    Given isolated collaboration evidence is green for the candidate
    When the candidate is deployed twice to persistent dogfood
    Then the Compose model Keycloak reconciliation and runtime assets are idempotent
    And the persistent member subject mail database TLS identity and active session remain unchanged
    And cached provider health is support-safe and fresh

  @human-ready-ios-distribution
  Scenario: iOS distribution uses the verified dogfood candidate
    Given persistent dogfood deployment and verification are green
    When the protected iOS distribution job is approved
    Then it uploads an immutable in-place candidate build with commit version build number and bundle identity
    And a waiting approval is reported as blocked rather than success

  @human-ready-physical-voiceover
  Scenario: Physical iPhone VoiceOver acceptance closes the final gate
    Given the current candidate is installed over the obsolete iPhone build
    When the human tester completes the common member tasks with VoiceOver on a physical iPhone
    Then labels headings focus order errors Dynamic Type and touch targets pass
    And force quit refresh and session upgrade pass
    And one real Chat Files and Calendar interaction passes

  @human-ready-manifest
  Scenario: One manifest controls the human-testing readiness claim
    Given automated testApp dogfood deployment distribution and physical acceptance evidence target one commit
    When the versioned support-safe readiness manifest is evaluated
    Then humanTestingReady is true only if every mandatory gate passed
    And any waiting failed stale degraded or missing gate records an explicit blocker
    And a non-ready candidate cannot be promoted to main
