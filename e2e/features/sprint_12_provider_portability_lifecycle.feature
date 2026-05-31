Feature: Sprint 12 provider portability and lifecycle readiness

  Sprint 12 turns portability, lifecycle, Weaver preflight, accessibility, and
  operations into support-safe evidence contracts without claiming live production
  migration or broad runtime execution.

  @weave-sprint12-portability-v2
  Scenario: Admin reviews provider portability schema v2 evidence
    Given an admin plans a provider change for Files, Calendar, Boards, or Chat
    When the dry-run evidence is generated
    Then every field is classified as portable, lossy, unsupported, manual_review, vendor_locked, or archive_only
    And loss, conflicts, permissions, archive refs, and rollback retention are support-safe
    And member impact uses stable product states only

  @weave-sprint12-documents-wopi-posture
  Scenario: Documents stay honest until WOPI spike evidence exists
    Given an organization has not proven the first Office provider path
    When documents readiness is evaluated
    Then Weave reports available, not_configured, unavailable, manual_review_required, or guarded states only
    And credential-bearing URLs and editing-ready claims are absent

  @weave-sprint12-identity-lifecycle
  Scenario: Identity reconcile and offboarding fail closed before destructive changes
    Given IDM groups, roles, and ownership transfers are under review
    When reconcile or offboarding dry-run evidence contains unknown mappings
    Then admin action is required
    And destructive removal is blocked until ownership transfer and audit retention are modeled

  @weave-sprint12-weaver-preflight-disabled
  Scenario: Weaver runtime and tools remain preflight-only
    Given Weaver runtime execution is disabled by default
    When an admin reviews sandbox, registry, SecretRef, and OAuth evidence
    Then unsigned, overbroad, undeclared-egress, or raw-secret manifests are rejected
    And no marketplace or broad third-party execution is implied

  @weave-sprint12-release-ops-a11y
  Scenario: Release promotion requires accessibility and restore evidence
    Given an RC promotion is requested
    When accessibility, backup, restore, upgrade, and schema migration evidence is incomplete
    Then promotion is blocked unless an exceptional issue-linked expiring waiver exists
    And support bundles redact WOPI, SCIM, Matrix E2EE, provider payload, OAuth, and SecretRef details
