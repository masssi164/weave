Feature: Enterprise dogfood readiness is proven across users and delivery gates
  Automated activation, OIDC, server, MCP, provider access, and cleanup are
  owned by testApp. Interactive Flutter and accessibility evidence is captured
  only on a physical device through the production system-browser flow.

  @human-ready-native-collaboration
  Scenario: Isolated collaboration proves the native default without external providers
    Given Chat Files and Calendar select weave-native in the isolated stack
    When two members use Matrix WebDAV and CalDAV across a PostgreSQL and backend restart
    Then encrypted Chat file revisions and calendar revisions remain available
    And the outsider remains denied before and after restart
    And no Synapse MAS or Nextcloud runtime dependency is observed

  @human-ready-dogfood-deployment
  Scenario: Persistent dogfood deployment is non-destructive and idempotent
    Given isolated collaboration evidence is green for the candidate
    When the candidate is deployed twice to persistent dogfood
    Then the Compose model static Keycloak migration and runtime assets are idempotent
    And the PostgreSQL Mailpit Caddy and native Files volumes plus public TLS identity remain unchanged
    And deployment holds no human identity writer while later OIDC evidence proves the activated owner session
    And cached provider health is support-safe and fresh

  @human-ready-ios-distribution
  Scenario: iOS distribution uses the verified dogfood candidate
    Given persistent dogfood deployment and verification are green
    When the protected iOS distribution job is approved
    Then it uploads an immutable in-place candidate build with commit version build number and bundle identity
    And a waiting approval is reported as blocked rather than success

  @human-ready-physical-voiceover
  Scenario: Physical iPhone VoiceOver acceptance closes the final gate
    Given the current candidate is installed on the physical acceptance iPhone
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
