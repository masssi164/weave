Feature: Human testing starts from green E2E and one exact installed dogfood build
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
