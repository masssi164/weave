Feature: Product E2E scenario layer before runtime implementation

  @weave-product-org-domain-verification-provisioning
  Scenario: Admin verifies organization domain and provisioning before member go-live
    Given an organization is preparing Weave for members
    When an admin verifies the organization domain and previews identity provisioning
    Then Weave shows whether go-live is allowed, blocked, or requires manual review
    And members are not invited until the readiness evidence is accepted
    And no identity-provider secret or raw directory payload appears in the evidence

  @weave-product-member-degraded-capability-state
  Scenario: Member sees a degraded capability without provider internals
    Given a member belongs to a configured organization
    And one work capability is temporarily degraded
    When the member opens the affected Space
    Then Weave explains the member impact in product language
    And the member can continue with available capabilities where policy allows
    And provider names, endpoints, and raw downstream errors stay out of the member view

  @weave-product-guest-bounded-space-access
  Scenario: External guest receives policy-bounded Space access
    Given an admin invites an external guest to one Space
    When the guest joins through the approved invitation path
    Then Weave grants only the Space capabilities allowed by organization policy
    And the guest cannot see unrelated spaces, admin settings, diagnostics, or provider configuration
    And audit evidence records the bounded guest access without exposing private content

  @weave-product-document-session-launch
  Scenario: Document editing launches through Weave grants and locks
    Given a member can access a document in a Space
    When the member starts an editing session
    Then Weave issues an editor grant scoped to that document and member
    And document locks, callbacks, and save status stay behind the Weave document contract
    And the member sees a provider-neutral document state if editing is unavailable

  @weave-product-meeting-artifacts-followup
  Scenario: Meeting artifacts become linked follow-up evidence
    Given a Space has a scheduled meeting
    When the meeting ends with follow-up material
    Then Weave links permitted artifacts to the meeting, Space, and related decisions
    And retention and recording policy are visible as product states
    And unsupported or unavailable artifacts are reported without provider-shaped language

  @weave-product-support-bundle-redaction
  Scenario: Operator prepares a support bundle without secrets or member content
    Given an operator investigates an organization health problem
    When the operator prepares a support bundle
    Then Weave includes readiness, version, configuration, and recent health evidence
    And Weave redacts secrets, tokens, credential-bearing locations, raw provider bodies, and member content
    And the bundle states which evidence is omitted or requires explicit consent

  @weave-product-backup-restore-readiness
  Scenario: Operator proves backup and restore before a release claim
    Given a release candidate depends on deployability evidence
    When an operator reviews backup and restore readiness
    Then Weave requires a current backup manifest and restore receipt
    And release promotion is blocked if restore evidence is missing or stale
    And failure details remain support-safe and actionable

  @weave-product-export-delete-retention
  Scenario: Admin reviews export, delete, and retention evidence before lifecycle action
    Given an admin is preparing a lifecycle action for a member or Space
    When Weave presents export, delete, retention, and archive evidence
    Then every item is classified as portable, lossy, unsupported, manual review, vendor locked, or archive only
    And destructive actions remain blocked until policy and approval evidence are complete
    And retained or deleted data is reported without exposing private content

  @weave-product-weaver-consent-approval-receipt
  Scenario: Weaver user consents to an approved tool action with an audit receipt
    Given Weaver is enabled for a user by organization policy
    And the requested domain tool is approved for that user and Space
    When the Weaver user consents to a high-risk action
    Then Weave records an approval receipt with scope, policy, and evidence references
    And the action runs only with the user's rights and approved capabilities
    And the user and admin can later audit or revoke the grant

  @weave-product-weaver-channel-only-roundtrip
  Scenario: Weaver chat channel roundtrip stays separate from MCP tools
    Given a member sends one personal Weaver message through the Weave chat entry point
    When Weaver handles the turn through the stable weave-chat channel
    Then the reply returns through weave-chat without requiring any MCP server or domain tool
    And support-safe tenant, conversation, message, and turn ids remain distinguishable from tool and audit ids
    And MCP chat.send_message is not accepted as inbound channel transport

  @weave-product-weaver-mcp-only-visibility
  Scenario: Weaver discovers and invokes Weave MCP tools without a chat message
    Given a signed RuntimeProfile grants Weaver access to the weave-domain-tools MCP server
    When Weaver discovers tools or invokes a governed domain tool outside any inbound chat turn
    Then tool visibility, deny paths, approval receipts, and audit refs are proved without weave-chat message semantics
    And support-safe tool, server, approval, and audit ids stay separate from channel ids
    And raw provider payloads, secrets, and member transcripts stay out of the proof

  @weave-product-weaver-same-turn-channel-mcp-separation
  Scenario: Weaver keeps channel and MCP planes separate in one same-turn flow
    Given a member starts one Weaver turn through weave-chat
    And the turn needs a governed Weave domain tool
    When Weaver invokes the tool during that same turn
    Then the user-facing reply or approval hint still returns through weave-chat
    And the evidence distinguishes channel message ids, turn ids, approval ids, MCP server ids, tool ids, and domain audit ids
    And the proof stays clearly marked as pre-release while #762 manual accessibility evidence remains open

  @weave-product-provider-switch-manual-review
  Scenario: Admin handles provider-switch manual review without changing member language
    Given a provider replacement dry-run finds lossy, unsupported, and vendor-locked items
    When the admin reviews the replacement plan
    Then Weave keeps member capability states stable until an approved migration step changes them
    And manual-review items require explicit admin decisions before execution
    And the plan records rollback, archive, and permission impact evidence
