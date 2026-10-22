Feature: Human testing starts from green E2E and one exact installed dogfood build
  Automated activation, OIDC, server, MCP, provider access, and cleanup are
  owned by testApp. Interactive Flutter and accessibility evidence is captured
  only on a physical device through the production system-browser flow.

  @human-ready-native-collaboration
  Scenario: Isolated collaboration proves the native default without external providers
    Given Chat Files and Calendar select weave-native in the isolated stack
    When members use Matrix and the generated Files and Calendar User APIs across a PostgreSQL and backend restart
    Then encrypted Chat file revisions and calendar revisions remain available
    And the outsider remains denied before and after restart
    And no Synapse MAS or Nextcloud runtime dependency is observed

  @human-ready-dogfood-deployment
  Scenario: Dogfood deployment starts the exact green source through Compose
    Given Full Compose E2E is green for the exact dogfood commit
    When the dogfood push deploy job runs
    Then Gradle starts the direct Compose stack from that exact source
    And ordinary updates preserve PostgreSQL native Files and Mailpit session volumes
    And an explicit reset deletes only those session volumes while preserving public TLS identity

  @human-ready-ios-distribution
  Scenario: Physical iPhone preparation installs the exact green dogfood commit
    Given Full Compose E2E and direct dogfood deployment are green for one commit
    When Prepare Human Test builds the development-signed iOS app
    Then it updates the stable bundle in place with exact commit and build identity
    And no TestFlight credential or environment approval blocks testing

  @human-ready-physical-voiceover
  Scenario: The tester records the real physical iPhone result
    Given the exact green dogfood build is installed on the physical acceptance iPhone
    When the human tester completes the common member tasks with VoiceOver on a physical iPhone
    Then labels headings focus order errors Dynamic Type and touch targets pass
    And force quit refresh and session upgrade pass
    And one real Chat Files and Calendar interaction passes

  @human-ready-manifest
  Scenario: Human testing requires no candidate manifest gate
    Given repository CI Full Compose E2E and dogfood deployment are green
    When the exact development-signed build is installed in place
    Then the tester may begin without a candidate manifest or versioned readiness document
    And a human result cannot automatically publish a tag or production release

  @human-ready-owner-space-preparation
  Scenario: Physical preparation establishes usable owner workspace access
    Given the qualified Dogfood candidate has one activated current organization owner
    And the default Space has never existed
    When explicit physical preparation establishes workspace access
    Then the durable default Space admits the actual owner
    And temporary setup authority is removed before installation
    And Files and Calendar require no manual Space setup

  @human-ready-owner-space-access-preserved
  Scenario: Repeated preparation preserves explicit Space access decisions
    Given a default Space already has durable memberships
    When physical preparation verifies current owner access
    Then valid access is accepted without changing memberships
    And revoked missing suspended or ambiguous access blocks readiness
    And proof without complete setup cleanup is rejected

  @human-ready-ordinary-functional
  Scenario: Ordinary startup proves authenticated domain behavior
    Given isolated exact-candidate qualification and deployment succeeded
    And supported test members have normal browser sessions and current domain grants
    When the normal application starts against direct reviewed Dogfood endpoints
    Then actual UI operations prove bidirectional Chat and conversation reopening
    And a created folder and uploaded file are read back by exact identity in their original scope
    And Calendar create read update delete have independent peer readback
    And restart and in-place update preserve the admitted identity and state
    And isolated actor injection test entrypoints and relays cannot satisfy this proof

  @human-ready-physical-functional
  Scenario: Physical readiness includes the installed authenticated application
    Given the exact candidate is installed in place on the approved iPhone
    When physical preparation verifies the preserved owner session through actual UI operations
    Then the functional result identifies the installed build and final qualification run and attempt
    And configuration discovery and a handoff-ready screen alone cannot satisfy it
    And owner data memberships signing trust and local stores are preserved
    And automation does not establish human VoiceOver or E2EE acceptance

  @human-ready-auth-incomplete
  Scenario: Required authentication remains an explicit incomplete boundary
    Given a real administrator or physical owner session requires sign-in passkey or MFA
    When preparation cannot complete that normal authentication autonomously
    Then independent work is completed before requesting the precise human authentication action
    And no session is fabricated copied or replaced by operator setup authority
    And the functional result stays incomplete and no ready handoff is emitted

  @human-ready-write-readback
  Scenario: Successful writes and failed refreshes are not conflated
    Given a Calendar or Files write has a confirmed stable resource identity
    When its subsequent same-context refresh has not observed that identity
    Then the interface preserves the confirmed write and original destination
    And recovery retries readback without silently creating another resource
    And an uncertain write retains its exact idempotent operation until resolved
