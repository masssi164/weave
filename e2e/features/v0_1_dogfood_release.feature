Feature: Weave v0.1 dogfood production release

  Weave v0.1 is an active dogfood-production daily work tool. These scenarios define
  the product spine that must be implemented with executable evidence before
  the release can be called dogfood-production.

  @weave-v01-home-daily-loop
  Scenario: Weave Home starts the daily work loop
    Given a signed-in workspace member opens Weave
    When the home view loads
    Then Weave shows recent channels, open tasks, upcoming meetings, recent decisions, and actionable health warnings
    And every home section has a keyboard and screen-reader path

  @weave-v01-channel-workspace
  Scenario: A channel is the primary workspace surface
    Given a workspace member enters a project channel
    When they navigate the channel workspace
    Then chat, files, board, calendar, meetings, and decisions are available as first-class tabs
    And provider details stay behind Weave-owned product surfaces

  @weave-v01-board-write-audit
  Scenario: A user board write is authorized and audited
    Given a workspace member has permission to update a channel board
    When they create or move a task without drag-and-drop
    Then the server checks authorization before touching the provider
    And the write produces an audit record and a support-safe result

  @weave-v01-meeting-capsule
  Scenario: A meeting capsule keeps work connected
    Given a channel event has a linked meeting
    When the meeting starts and finishes
    Then the capsule keeps agenda, files, decisions, and follow-up tasks connected to the channel
    And media-provider secrets never reach the client

  @weave-v01-decision-ledger
  Scenario: Decisions are captured as product records
    Given a channel discussion reaches a decision
    When a member records the decision
    Then Weave stores context, evidence, risks, open questions, and follow-up links
    And the decision is reachable from the channel, meeting, board task, and home view

  @weave-v01-operator-release-path
  Scenario: Operators can deploy, verify, back up, restore, and diagnose safely
    Given an operator installs or updates a Weave stack
    When they run release verification, backup, restore smoke, and support-bundle checks
    Then every step produces deterministic evidence
    And diagnostics are redacted before sharing
