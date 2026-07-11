Feature: Live Stack product acceptance journey
  The Live Stack app E2E is the sparse executable proof that a person can sign in
  once and use Weave-owned product surfaces for profile, chat, and files. Calendar
  and boards remain backend-facade or roadmap evidence until member routes are restored.
  These scenarios are product contracts, not implementation notes.

  Each scenario has a stable tag in e2e/scenario_mappings.json. The mapping
  guard fails if a scenario is not connected to an executable test and evidence
  marker, so this readable layer cannot drift into decorative BDD.

  @weave-live-auth-shell
  Scenario: Sign-in restores the Weave workspace and profile
    Given the Weave workspace is ready for the live test person
    When the person signs in through Weave
    Then Weave restores the signed-in workspace through the organization manifest and capability projection
    And the person sees member-safe Weave readiness without provider setup copy
    And the person can load, edit, reload, and restore their profile name without clearing omitted profile fields

  @weave-live-chat-content
  Scenario: Weave chat sends and reads a workspace message through the backend facade
    Given the signed-in person has the Weave chat surface available
    When the person creates a conversation and sends a message
    Then the message is readable in Weave chat
    And Weave reports the chat connection outcome honestly

  @weave-live-matrix-e2ee
  Scenario: Chat encryption diagnostic status is proved honestly
    Given the signed-in person has encryption support available for chat
    When the person sends a message in an encrypted conversation
    Then Weave observes encrypted message evidence without plaintext leakage
    And Weave reports recovery and key-storage readiness as an explicit diagnostic surface

  @weave-live-files-boundary
  Scenario: Files are uploaded, shown, downloaded, and cleaned up in Weave
    Given the signed-in person opens Weave Files
    When the person uploads a unique file
    Then Weave shows the uploaded file
    And downloading the file returns the original content
    And the test removes the file it created

  @weave-live-provider-stack-readiness
  Scenario: Provider stack readiness stays backend-owned and support-safe
    Given the signed-in person has optional providers represented by Weave
    When the app checks provider readiness boundaries
    Then raw provider registry diagnostics are denied to member tokens
    And member readiness is exposed only through backend-owned facades
    And normal member journeys use Weave backend facades instead of direct Flutter provider calls


  @weave-live-workspace-loop
  Scenario: Workspace loop links Space, Channel, Chat, Files, and Decision
    Given a signed-in member starts from a Weave Space and channel
    When the member records chat context, references a file, and records a decision
    Then the loop uses stable Weave domain language and canonical IDs
    And support-safe evidence marks real writes without provider leakage

  @weave-live-provider-reality-vertical
  Scenario: Provider reality vertical reports domain availability honestly
    Given the signed-in person uses provider-backed Weave domains
    When Weave checks capability reality across files, calls, and documents
    Then files are backed by live backend paths
    And calendar, boards, calls, and documents are either available outside normal member routes or honestly unavailable with member-safe fallback copy
    And provider reality evidence separates live-runtime checks from offline accessibility and release evidence
