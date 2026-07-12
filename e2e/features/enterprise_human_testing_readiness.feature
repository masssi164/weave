Feature: Enterprise dogfood readiness is proven across users and delivery gates

  @human-ready-auth-shell
  Scenario: Disposable members restore independent authenticated shells
    Given isolated author and collaborator identities have organization access
    When each member signs in and relaunches from a separate application profile
    Then each member returns to the normal application shell with only their own session
    And Home Chat Files Calendar Settings and Profile are reachable

  @human-ready-home-collaboration
  Scenario: Shared Home activity is visible only to authorized members
    Given author and collaborator share a workspace and outsider does not
    When each shared member commits unique workspace activity
    Then both shared members see the appropriate fresh recent activity
    And outsider sees no private workspace activity

  @human-ready-chat-collaboration
  Scenario: Chat collaboration crosses fresh authorized sessions
    Given author collaborator and outsider use fresh authenticated sessions
    When author sends a unique encrypted workspace message and collaborator replies
    Then collaborator decrypts the author message and author decrypts the reply
    And transport evidence contains ciphertext rather than message content
    And outsider is denied the conversation

  @human-ready-files-collaboration
  Scenario: Files collaboration preserves content and authorization
    Given author and collaborator share a workspace and outsider does not
    When author uploads a unique file and collaborator downloads and updates it
    Then collaborator verifies the original checksum
    And author sees and verifies the updated version
    And outsider cannot read or mutate the file

  @human-ready-calendar-collaboration
  Scenario: Calendar collaboration crosses fresh authorized sessions
    Given author and collaborator share a workspace and outsider does not
    When author creates a unique workspace event and collaborator responds
    Then author observes the response through a fresh session
    And outsider cannot read or mutate the event

  @human-ready-settings-profile
  Scenario: Settings and Profile remain independently durable
    Given author and collaborator use separate application profiles
    When each member changes their own supported profile preference and relaunches
    Then each change survives only in its owning member session
    And support-safe build and server identity are visible
    And each member can sign out without ending the other session

  @human-ready-failure-containment
  Scenario: Calendar failure remains local to Calendar
    Given an authenticated member is using the normal application shell
    When Calendar is made unavailable by the controlled isolated test
    Then Calendar shows its accessible member-safe unavailable state
    And Home Chat Files Settings and Profile remain reachable

  @human-ready-authorization
  Scenario: Representative collaboration operations fail closed
    Given the isolated collaboration resources exist
    When Chat Files and Calendar operations use a missing capability wrong workspace expired token or revoked session
    Then every unauthorized read and write is denied support-safely
    And authorized sessions remain usable

  @human-ready-cleanup
  Scenario: Disposable collaboration cleans only its own namespace
    Given the three-user collaboration suite has completed twice
    When its run namespace is cleaned
    Then run-created artifacts memberships and identities are removed where supported
    And only hashed references timestamps statuses and correlations remain
    And the persistent human dogfood member is unchanged

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
    Then OpenTofu and runtime assets are idempotent
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
    Given automated collaboration dogfood deployment distribution and physical acceptance evidence target one commit
    When the versioned support-safe readiness manifest is evaluated
    Then humanTestingReady is true only if every mandatory gate passed
    And any waiting failed stale degraded or missing gate records an explicit blocker
    And a non-ready candidate cannot be promoted to main
