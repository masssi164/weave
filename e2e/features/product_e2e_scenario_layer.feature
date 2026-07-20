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
  Scenario: Weaver user consents to a guarded tool action with signed evidence
    Given Weaver is enabled for a user by organization policy
    And the requested domain tool is approved for that user and Space
    When the Weaver user consents to a high-risk action
    Then Agent Runtime Control signs short-lived single-use decision evidence for the exact action and arguments
    And the owning domain independently reauthorizes the user's rights and approved capabilities before the action
    And the final observed result is recorded as immutable action evidence

  @weave-product-provider-switch-manual-review
  Scenario: Admin handles provider-switch manual review without changing member language
    Given a provider replacement dry-run finds lossy, unsupported, and vendor-locked items
    When the admin reviews the replacement plan
    Then Weave keeps member capability states stable until an approved migration step changes them
    And manual-review items require explicit admin decisions before execution
    And the plan records rollback, archive, and permission impact evidence
