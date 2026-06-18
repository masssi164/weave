Feature: Sprint 32 Admin User Weaver Beta path

  Sprint 32 proves one coherent Beta evaluator story across Admin readiness,
  eligible Weaver enablement, member workspace use, governed runtime/tool
  execution, adapter-continuity accounting, and support-safe audit evidence.

  @sprint32-beta-admin-user-weaver-path
  Scenario: Admin prepares readiness, enables Weaver, and a member uses governed Weaver in a workspace
    Given the Admin has category readiness evidence for organization, identity, chat, files, calendar, boards, meetings, health, and Weaver
    And the Admin has a support-safe adapter-continuity dry-run with preserved, lossy, blocked, rollback, and member-impact accounting
    When the Admin enables Weaver for an eligible policy scope
    And the User opens the Client in a workspace context
    And the User asks Weaver for workspace help through the governed runtime/tool path
    Then the result uses canonical support-safe capability language
    And approval-required Weaver tool actions are controlled by the user runtime receipt boundary
    And audit and evidence refs are inspectable without secrets, raw provider payloads, raw Weaver prompts, or user content
    And the live-stack gate is blocked only when the credentialed runtime environment is unavailable
