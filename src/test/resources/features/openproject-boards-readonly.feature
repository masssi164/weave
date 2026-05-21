Feature: OpenProject Boards read-only runtime through Weave
  Weave exposes OpenProject as a backend-owned read-only Boards provider while
  keeping Weave as the product API, preserving Context/Space authorization, and
  refusing unsupported provider writes until audit and consent promotion exists.

  Scenario: OpenProject provider disabled fails closed support-safely
    Given the OpenProject Boards provider is disabled
    When a workspace member previews Boards through Weave
    Then the Boards request fails with "boards-provider_unavailable"
    And the error is support-safe
    And the response does not leak provider secrets or raw OpenProject URLs

  Scenario: OpenProject provider enabled shows read-only boards and tasks through Weave
    Given the OpenProject Boards provider is enabled with backend-held credentials
    And OpenProject has a project "Apollo Launch" with a completed work package "Ship backend seam"
    When a workspace member previews Boards through Weave
    Then Weave returns an OpenProject read-only Boards snapshot
    And the snapshot contains board "Apollo Launch" and task "Ship backend seam"
    And sync metadata is support-safe and read-only
    And the response does not leak provider secrets or raw OpenProject URLs

  Scenario: Missing Context Space authorization exposes no provider data
    Given the OpenProject Boards provider is enabled with backend-held credentials
    And OpenProject has a project "Apollo Launch" with a completed work package "Ship backend seam"
    And the workspace member has no Boards permission for the Context Space
    When a workspace member previews Boards through Weave
    Then the Boards request fails with "boards-forbidden"
    And the error is support-safe
    And OpenProject was not contacted

  Scenario: Provider cursors and metadata stay support-safe
    Given the OpenProject Boards provider is enabled with backend-held credentials
    And OpenProject has a second page of projects
    When a workspace member previews Boards through Weave
    Then sync metadata contains an opaque OpenProject cursor
    And the response does not leak provider secrets or raw OpenProject URLs

  Scenario: Writes comments and archive actions are refused until audit consent promotion
    Given the OpenProject Boards provider is enabled with backend-held credentials
    When a workspace member tries unsupported OpenProject provider actions
    Then provider writes are refused support-safely
    And comments and attachments are refused support-safely
