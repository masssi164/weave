Feature: OpenProject Boards live runtime through Weave infrastructure
  The OpenProject provider is optional and off by default. When enabled, live
  evidence must go through the Weave API and remain context-scoped,
  audit-aware, and support-safe.

  @infra-openproject-disabled-fail-closed
  Scenario: OpenProject provider disabled fails closed through Weave
    Given the local Weave stack is installed without OpenProject runtime enablement
    When an authenticated workspace member requests the Weave Boards workspace
    Then the request fails closed with a Boards API error
    And the response does not leak provider secrets, raw OpenProject URLs, or upstream paths

  @infra-openproject-enabled-workspace
  Scenario: OpenProject provider enabled exposes provider-neutral boards
    Given the backend is configured with OpenProject runtime and backend-held credentials
    And Context Space authorization allows the workspace member to read the mapped space
    When an authenticated workspace member requests the Weave Boards workspace
    Then the response comes from the OpenProject workspace-sync backend facade
    And provider-neutral projects, boards, and tasks are present
    And sync metadata is user-write audited, context-scoped, and support-safe

  @infra-openproject-context-gate
  Scenario: Missing Context Space authorization exposes no provider data
    Given OpenProject runtime is enabled but Context Space authorization denies the member
    When an authenticated workspace member requests the Weave Boards workspace
    Then the request fails closed with a support-safe Boards authorization error
    And no raw OpenProject data is exposed

  @infra-openproject-write-refusal
  Scenario: Provider writes remain refused until audit and consent promotion
    Given OpenProject is the configured Boards provider
    When an authenticated workspace member attempts to create a provider-backed task through Weave
    Then the provider write is refused support-safely
    And comments archive and agent actions remain disabled until a later audit and consent promotion
