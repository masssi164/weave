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

  @weave-v01-user-ready-organization-flow
  Scenario: A normal member sees a user-ready organization flow
    Given an admin has provisioned the organization and invited a member
    When the member opens Weave and enters a channel workspace
    Then release-scope surfaces use available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later states
    And the member does not see preview, scaffold, roadmap, or raw provider setup copy
    And provider diagnostics stay in admin/operator health surfaces
    And removed Calendar and Deck member routes are absent instead of hidden behind redirects

  @weave-v01-dogfood-member-invite-activation
  Scenario: Dogfood member invite activation reaches the workspace
    Given an admin has provisioned a dogfood member invite without passwords, bearer tokens, provider payloads, or raw secrets
    And the persistent human dogfood member is separate from the disposable automation user
    And the identity provider sends the initial credential setup mail into the local dogfood Mailpit inbox
    When the member completes Keycloak activation in the system browser
    And opens the same secret-free organization access through the email completion link, QR code, or server URI
    And the Organisation access screen offers Sign In
    And the member taps Sign In, completes OIDC Authorization Code with PKCE, and returns to Weave
    Then Weave records support-safe handoff_ready, ready_for_sso, sso_in_progress, authenticated, workspace_bootstrap_loading, and workspace_ready evidence
    And the same dogfood member evidence proves Chat and Files are usable after login
    And the authenticated session is restored after force-quit and reopen
    And trust-preserving app-state reset plus manual sign-in from the saved organization configuration reaches the workspace
    And Mailpit is reachable on the iPhone through the private HTTPS dogfood URL and captures identity mail without external delivery
    And a routine repeated dogfood deployment preserves the same active Keycloak subject without re-inviting or mutating it
    And ordinary Mailpit container replacement preserves captured dogfood mail
    And disposable live-stack E2E cannot remove the persistent dogfood identity or inbox
    And dogfood trust evidence proves stable local TLS certificates, stable iOS signing/provisioning, and no repeated Developer App trust prompt after normal update or app-state reset
    And no member-visible state leaks raw provider errors, setup internals, tokens, credentials, or secret references

  @weave-v01-lost-pending-identity-retirement
  Scenario: Lost never-activated dogfood identity requires protected retirement
    Given no integrity-checked database backup is identity-restorable for the recorded human subject
    And any restored disposable bootstrap identity is uniquely proven and removed through Keycloak
    And the recorded human dogfood subject has accepted evidence only in the pending activation state
    And the current realm contains no matching or ambiguous identity
    When an operator explicitly approves retiring that lost pending identity
    Then the previous raw subject is archived only in private operator recovery state
    And one new pending identity is created through the Keycloak administration boundary
    And shared evidence contains only the previous and replacement subject hashes and approval reference
    And readiness remains blocked until backup restore smoke repeated deployment activation and member verification pass
    But an active disabled ambiguous or insufficiently evidenced subject is never replaced

  @weave-v01-admin-provider-categories
  Scenario: Admin sees provider categories before member use
    Given an owner or admin opens Workspace Health before inviting members
    When provider readiness and policy are reviewed
    Then Keycloak platform identity readiness is shown separately from chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Weaver provider categories
    And current dogfood defaults map to category readiness without becoming member-facing product names
    And Weaver is disabled by default until admin policy explicitly enables it
    And normal members never configure raw providers, service endpoints, provider secrets, or diagnostics

  @weave-v01-org-manifest-client-admin-split
  Scenario: Organization manifest keeps member client separate from admin console
    Given an organization has configured Keycloak federation, provider categories, capability profiles, and whitelists in the Admin Console
    When a member opens Weave with an organization auth URL, invite link, or deep link and completes SSO
    Then the Weave Client receives a support-safe organization manifest and effective capability states
    And member-visible states are only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later
    And provider setup, endpoint rotation, readiness diagnostics, secrets, and provider/tool/agent whitelisting stay in the Admin Console

  @weave-v01-admin-health-policy-enforcement
  Scenario: Admin health enforces provider readiness and member policy boundaries
    Given an owner or admin opens Workspace Health after selecting provider categories
    When backend provider readiness and capability policy are evaluated
    Then Workspace Health returns overall posture, support-safe category readiness, next actions, and evidence for available, disabled_by_policy, not_configured, degraded, unavailable, coming_later, and misconfigured states
    And feature capabilities are separated from default and external provider adapters
    And members receive only provider-neutral capability states without raw provider setup
    And member API writes are denied when Keycloak-derived capability policy does not grant the required category capability
    And Weaver remains disabled by default unless governed organization policy explicitly enables it

  @weave-v01-org-control-plane-provider-facade
  Scenario: Server control plane owns provider policy and audit
    Given an owner or admin opens the Organization/Admin Console
    When provider status, policy whitelists, readiness tests, and audit events are requested
    Then the server returns a support-safe control-plane contract with category readiness and SecretRefs only
    And members cannot call admin control-plane APIs directly
    And provider readiness tests and policy changes produce redacted audit events
    And the member client still receives only effective capability states without raw provider configuration

  @weave-v01-canonical-provider-neutral-models
  Scenario: Self-hosted and external providers map to the same Weave feature models
    Given an organization compares Teams or Slack chat, SharePoint files, and OpenProject or Planner boards while Keycloak remains the platform identity authority
    When the backend provider registry maps each selected provider into Weave feature facades
    Then Matrix or Slack-like chat maps to the same Space, Conversation, Message, Thread, Reaction, Attachment, Membership, and Presence model
    And Nextcloud or SharePoint-like files map to the same Drive, Node, Folder, File, Version, Share, Permission, Lock, and EditSession model
    And CalDAV or Microsoft Graph-like calendar and LiveKit or Teams-like meetings map to the same Calendar, Event, Attendee, Recurrence, Availability, Resource, Meeting, Participant, Recording, Captions, and MediaSession model
    And OpenProject or Planner-like tasks map to the same Board, List, Task, Status, Assignee, Comment, Attachment, Dependency, and CustomField model
    And Keycloak remains the identity authority while upstream LDAP, Active Directory, OIDC, and SAML identities are federated or brokered through it
    And Weave stores actor references and audit evidence without duplicating Keycloak users, memberships, groups, or roles

  @weave-v01-member-provider-neutral-states
  Scenario: Member client sees stable feature states without raw provider details
    Given an admin has selected providers and the backend has evaluated readiness and capability policy
    When a normal member opens Weave and fetches their organization manifest and feature surfaces
    Then the member sees only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later feature states
    And provider names may appear only as product-safe context when necessary
    And provider URLs, raw provider identifiers, downstream payloads, secrets, readiness internals, and adapter diagnostics are not exposed to the member client

  @weave-v01-admin-policy-decides-capabilities
  Scenario: Admin provider policy decides capability availability before provider access
    Given provider configs exist for recommended self-hosted defaults and at least one external provider placeholder
    When capability policy, provider readiness, whitelists, and SecretRefs are evaluated
    Then the backend denies unknown roles, unknown groups, missing readiness, and unapproved providers by default
    And the Admin Console can mark each capability available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later without changing member client APIs
    And provider access happens only after the backend has authorized the canonical Weave capability operation


  @weave-v01-keycloak-rbac-capability-policy
  Scenario: Keycloak roles and groups decide capability profiles before Weaver runtime
    Given an owner has configured Keycloak federation and organization access
    When role and group claims are mapped into workspace capability profiles
    Then Keycloak is the identity authority and upstream OIDC SAML LDAP or Active Directory sources remain behind it
    And capability profiles grant category-level capabilities deny-by-default
    And admins/operators can inspect support-safe policy state
    And members only see available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later impact states
    And Weaver capability placeholders stay disabled by default until a governed runtime policy exists

  @weave-v01-agent-runtime-control-policy
  Scenario: Runtime cells derive from current organization entitlement and policy
    Given an admin has enabled Agent Runtime Control after Keycloak entitlement and signing trust are ready
    When an entitled person is provisioned through the organization-bound administrative API
    Then ARC binds one disposable cell and dedicated Keycloak workload client to the immutable person identity
    And ARC signs a short-lived RuntimeProfile v2 containing references and maximum capabilities only
    And portable workspace content and encrypted runtime state remain in authorities outside the zero-durable-byte cell
    And missing entitlement, stale profile, cross-cell access, or incomplete restore state fails closed

  @weave-v01-mcp-workload-boundary
  Scenario: MCP admits only a current entitled workload and advertises the guarded Files read slice
    Given an ARC-bound cell has an exact-audience Keycloak workload token
    When the cell negotiates the MCP Client Credentials extension over Spring AI Streamable HTTP
    Then the MCP edge exchanges rather than relays the workload token and resolves current backend cell context
    And human tokens, generic service accounts, stale profiles, and upscope attempts are denied
    And domain tool resource and prompt catalogs remain empty until current authorization and evidence gates are executable
    And a future domain side effect still requires independent domain authorization and single-use decision evidence

  @weave-v01-channel-workspace
  Scenario: A Space control room is the primary workspace surface
    Given a workspace member enters a project Space control room
    When they navigate from the same Space identity to chat, files, board, calendar, and decisions
    Then the route exposes first-class tabs with support-safe canonical IDs and evidence refs
    And empty, disabled_by_policy, not_configured, degraded, unavailable, coming_later, and evidence-linked states use provider-neutral wording
    And Sprint 19 evidence proves a dogfood Workspace control room without claiming full domain parity or broad autonomous AI availability

  @weave-v01-chat-domain-facade
  Scenario: Chat uses a canonical backend domain facade
    Given a workspace member has chat.read and chat.send capability in a channel context
    When they synchronize rooms, read messages, and send a message through the OIDC-gated Weave Matrix facade
    Then the backend projects Matrix Client-Server shapes over canonical conversation, message, membership, history-policy, and attachment-policy vocabulary
    And capability policy and Context/Space authorization run before provider access
    And chat writes produce support-safe audit evidence
    And deprecated REST conversation and message routes remain unavailable
    And provider replacement dry-runs redact raw provider identifiers, credentials, URLs, and downstream errors

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

  @weave-v01-infra-control-plane-bootstrap
  Scenario: Infra bootstrap feeds the backend control plane safely
    Given an operator bootstraps the recommended sovereign default Weave stack
    When Keycloak, provider profiles, admin console target metadata, and backend environment are generated
    Then Keycloak is reachable as the central default identity broker
    And the server manifest and provider registry are reachable through Weave backend APIs
    And admin APIs reject member tokens while support bundles keep SecretRefs redacted

  @weave-v01-admin-console-mvp
  Scenario: Organization admins manage provider policy in a separate console
    Given an owner, admin, or operator signs into the Organization/Admin Console
    When they review org overview, provider categories, readiness details, whitelist policy, and audit events
    Then every action goes through backend admin APIs
    And no raw provider calls, provider secrets, or admin diagnostics are exposed to member clients
    And the console remains keyboard reachable with semantic headings, forms, and status text

  @weave-v01-provider-switch-portability
  Scenario: Admin plans a provider switch with portable export/import evidence
    Given an admin has an active provider adapter and a candidate replacement for a domain
    When the admin runs the provider switch dry-run before applying the replacement
    Then the Admin Console shows backend-declared source-of-truth policy, what will move, what will not move, risks, conflicts, and required permissions
    And preflight, export/import manifests, cutover gates, rollback boundary, recovery actions, and support-safe audit evidence are required before irreversible action
    And member-facing surfaces keep provider-neutral available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later states during the switch

  @weave-v01-operator-release-path
  Scenario: Operators can deploy, verify, back up, restore, and diagnose safely
    Given an operator installs or updates a Weave stack
    When they run release verification, backup, restore smoke, and support-bundle checks
    Then every step produces deterministic evidence
    And diagnostics are redacted before sharing
