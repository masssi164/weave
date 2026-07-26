# Weave product line and Weaver integration plan

Positioning line: **Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.** This line is valid only with the claim gates in the product trust matrix and Sprint 30 evidence pack.

Status: active product direction, reconciled to the 2026-07-19 specification corpus.

## Decision lock

Sprint 21 update: [Product reality foundation](product-reality-foundation.md) is the active release-claim and proof-order gate for the next sprint sequence. Claim boundary: it narrows release/customer-ready wording to named `release_ready` evidence and moves human validation after automated provider-switch, rollback, Weaver runtime, and restore proof.

Weave is planned product-first, not agent-first.

Weave is a provider-neutral organization operating layer and integration suite. It lets an organization federate existing identity sources through Keycloak and keep existing collaboration systems for chat, files, calendar, boards/tasks, documents, meetings, decisions, help/manuals, and release evidence while presenting them through coherent Weave product concepts.

Weaver is a later personal-assistant layer that plugs into this already-governed organization model. It must not define the product architecture by itself.

## Priority realignment, 2026-05-26

Current priority is not Office/ONLYOFFICE integration or another provider feature slice. Office remains a later provider category until the more fundamental organization-embedding, provider-facade, identity/provisioning, policy, and replacement contracts are in place.

The next strategy sprint is [Organization embedding and provider-neutrality proof](strategy-sprint-org-embedding-plan.md). It is supported by [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). New feature/provider work should be sliced from those contracts rather than from a dogfood-stack-shaped assumption.

The active priority order is:

1. **Prove organization embedding.** Weave must support both existing organizations and newly bootstrapped organizations: verified domains, OIDC/SAML auth, SCIM/LDAP/AD provisioning paths, role/group mapping, guests, service principals, deprovisioning, break-glass, and effective policy previews before member go-live.
2. **Complete server-side domain facading.** The client consumes Weave domains such as Chat, Files, Calendar, Boards/Tasks, Meetings, Decisions, and Health. Provider-specific mapping, credentials, readiness, migration, lossy conversion notes, and provider failures stay server/admin/operator side. For example, an admin may later replace Slack with Synapse/Matrix for the Chat domain through the Admin Console, while conversations, membership, history policy, attachments, and support-safe migration evidence are carried over by a server-owned migration path.
3. **Prove adapter replacement and anti-silo guarantees.** Mixed self-hosted/cloud/external deployments, such as Keycloak federated with Entra ID plus Teams + SharePoint + OpenProject or Keycloak + Matrix + SharePoint, are first-class. Every collaboration-provider category needs source-of-truth, export/delete, provenance, lossy-field, risk, and dry-run replacement behavior.
4. **Embed manuals as product help.** The member Help surface embeds the MkDocs user manual in `weave/client`; the Admin Console embeds the admin/operator manual. Both manuals use the same CSS variables/design tokens/corporate design as the app surfaces and must remain accessible in iframe/webview form.
5. **Automate README release evidence and repositioning.** README must automatically include generated release notes, describe what Weave is and where it is going, and stop presenting Weave as merely a collaboration platform. Public marketing copy still needs specialist review before final release positioning.

Issue hygiene rule: close or supersede provider-specific implementation epics that are lower priority than the domain-facade/admin-console path, especially office-first or fixed-stack issues. Keep or rewrite issues only when they directly support domain contracts, provider swaps/migrations, admin readiness, embedded manuals, or release-note automation.

## Product line

### 1. Admin portal and organization setup

The admin portal is the control center for organization setup and policy.

It must let owners/admins manage the fixed platform-identity boundary and choose collaboration-provider categories:

- Platform identity/security: Keycloak authority with optional Entra ID, Authentik/Auth0, OIDC/SAML, SCIM, or LDAP/AD federation/brokering; not a provider-registry choice.
- Chat: Matrix, Microsoft Teams, Slack, Nextcloud Talk, or another supported channel.
- Files: Nextcloud Files, SharePoint/OneDrive, S3-compatible storage, SMB, or another provider.
- Calendar: Weave-managed shared calendar facade backed by the selected provider stack.
- Boards/tasks: Weave task/board model backed by OpenProject first, with other adapters possible.
- Meetings/calls: Matrix v1.19 plus pinned MatrixRTC Profile 0 northbound signaling, with an
  internal RTC Authorizer and replaceable SFU/media adapters.
- Documents/collaboration: provider adapters, not direct product dependency on one vendor.
- Agent Runtime Control: optional and entitlement-bound; OpenClaw/Weaver is the first runtime
  provider, not an independent collaboration domain.

The admin portal owns:

- provider selection and readiness;
- role and group mapping;
- policy profiles;
- capability availability;
- support-safe diagnostics;
- backup/restore and operator evidence;
- Agent Runtime Control entitlement, runtime-provider, and maximum-capability policy.

Normal members do not configure raw providers.

### Weave Client and Organization/Admin Console split

The Weave Client owns member work surfaces: authenticated home, channels, chat, files, calendar, boards/tasks, meetings, decisions, profile, and personal settings. Its setup boundary is intentionally narrow: a member enters or opens an organization auth URL, invite link, or deep link, completes SSO, consumes the authenticated organization manifest, and renders only effective capability states. The client may show member-visible states `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; it must not manage raw provider URLs, secrets, endpoint rotation, provider setup, support diagnostics, policy authoring, or whitelist configuration.

The Organization/Admin Console owns organization bootstrap,
collaborative-domain provider choice, policy, readiness, diagnostics, and
whitelisting. It manages users/groups/roles through Keycloak and configures
Keycloak federation or brokering for Entra, Authentik, Auth0, OIDC/SAML, SCIM,
or LDAP/AD sources.

The handoff contract is: org auth URL or invite/deep link -> SSO -> support-safe organization manifest -> member capability states. Provider/category/admin management must not be pressed into the member client.

### 2. Provider-neutral Weave suite

Weave models collaboration categories, not vendor products.

The product surfaces remain Weave-owned:

- Home and activity overview;
- personal messages;
- channels as workspaces;
- channel tabs for chat, decisions, files, boards/tasks, calendar/events, and meetings; Weaver has no member-facing tab;
- Workspace/Admin Health for readiness and support;
- accessible settings and policy-visible capability states.

Provider adapters sit behind collaborative-domain contracts. A Microsoft-heavy
organization may federate Entra ID through Keycloak while using Teams,
SharePoint, and Planner/Jira-style integrations. A self-hosted organization may
use Keycloak, Matrix, Nextcloud, OpenProject, and LiveKit. Provider swaps are
admin-controlled domain migrations; Keycloak authority is not part of that
patch panel.

The contract seam is category-first: feature capabilities for chat, files,
office/docs collaboration, meetings/calls, boards/tasks, calendar, and Agent
Runtime Control are separate from adapter implementations. Platform identity
and security is a fixed Keycloak boundary with its own readiness contract.
Workspace Health and policy enforcement evaluate stable
context contracts and member impact states, while concrete providers remain admin-selected
adapters. Weaver/OpenClaw appears as an ARC runtime provider, never as a canonical collaboration
domain.

Provider choice is risk-aware, not prohibition-based. Weave recommends the sovereign/self-hosted default posture where it is sensible, but existing organizations may federate their directory through Keycloak and keep external collaboration providers for selected categories, such as Teams chat, SharePoint/OneDrive files, Microsoft 365 Office integration, and OpenProject tasks. Admin/provider readiness records the choice model as `recommended_self_hosted_default`, `external_existing_provider`, `managed_cloud_provider`, or `hybrid_composite`, plus support-safe privacy/compliance risk notes. Member manifest vocabulary remains stable: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; member copy may describe available capabilities as usable.

Adapter seams should prefer well-known interoperability contracts where practical: OIDC/SAML for SSO/federation, SCIM for user and group provisioning/deprovisioning, WebDAV/CMIS for file/content abstraction, CalDAV/iCalendar/VTODO for calendar and task-shaped records where applicable, and WOPI-style seams between storage and web office editors. Apache Camel, Nango, and Open Integration Hub remain research references for connector/adapter plus normalized-model patterns; do not adopt one blindly without an ADR.

This means implementation work should add category contracts and adapter seams before adding vendor-specific UX. Provider names belong in admin/operator readiness and documentation, not as the main member-facing product model.

### 3. Weaver as an ARC-governed personal runtime

Weaver is optional and later.

When enabled, an entitled person may receive one isolated disposable runtime cell whose first
provider is an upstream-first OpenClaw/Weaver image. The organization owns entitlement and the
maximum permitted profile. Portable allowlisted workspace content remains canonical on WebDAV;
runtime state is encrypted in an external RuntimeStateStore; the cell owns zero durable bytes.

The central rule is:

> user-rights, organization-whitelisted capabilities.

The PA may act with the user's normal rights, but only through organization-approved capability channels. Routine operation must not depend on per-call confirmation. Step-up confirmation is reserved for exceptional high-risk actions.

Admin policy controls:

- which ARC entitlement and signed RuntimeProfile v2 a person/group may receive;
- which tools/capabilities are visible to that runtime;
- which provider adapters can be used;
- whether exec-like capabilities exist at all;
- cell isolation, workspace-manifest, external-state, and egress defaults;
- connector/package approval, versioning, revocation, and audit.

OpenClaw configuration remains ephemeral implementation output, not the product model. ARC signs
the desired state and owns current cell binding; OpenClaw owns its runtime loop, Matrix plugin,
session state, and native approval lifecycle. ACP/Codex-style developer assistance may become one
governed capability channel for approved users only inside the same entitlement and current domain
authorization boundary; it must not become autonomous team-agent scope or a shortcut around
disabled exec/elevated defaults.

## Implementation plan

### Phase 0: domain facade and documentation/release reset

Goal: align the next work around Weave domains, provider migrations, embedded manuals, and release evidence before more provider-specific feature epics.

- Define the `weave/server` domain facade contract for Chat first, including canonical conversation/message/membership/history/attachment identifiers, provider mapping records, support-safe migration reports, readiness, and fail-closed behavior.
- Model provider replacement as an admin/operator action with preflight, dry-run, migration plan, reversible evidence where practical, and explicit lossy-field warnings.
- Keep provider names out of normal member navigation; expose them only in Admin Console/provider readiness and support-safe diagnostics.
- Add MkDocs user/admin manual embedding requirements and shared CSS-variable/design-token contract.
- Add README release-note markers and automation contract; generated release notes must come from release metadata and be reviewable before publication.

Evidence gate:

- Specs make Chat provider replacement possible without changing member-facing domain vocabulary.
- No new user-facing surface calls a raw chat/files/docs provider directly.
- Manual embedding and README release-note automation have explicit acceptance criteria.
- Office/provider-specific epics are marked postponed or superseded unless they support the domain-facade foundation.


### Phase A: product foundation

Goal: make the product line explicit before deeper agent work.

- Add this document as the active product-line reference.
- Update README positioning from one fixed self-hosted stack toward provider-neutral organization suite.
- Keep v0.1 honest: existing dogfood stack stays real, but the architecture must not imply Nextcloud/Matrix/Keycloak are the only possible product shape.
- Keep normal-member first use admin-provisioned.

Evidence gate:

- README and architecture docs state provider-neutral categories and admin-first setup.
- No normal-member UX claims raw provider setup or Agent Runtime Control administration.
- Product acceptance includes an admin/provider-category scenario proving categories, dogfood-default mapping, member/admin boundaries, support-safe diagnostics, and fail-closed ARC ordering.

### Phase B: admin/provider model

Goal: implement category-based setup and readiness.

- Define provider category entities in backend/domain docs: identity, chat, files, calendar,
  boards/tasks, meetings, docs/collaboration, and Agent Runtime Control.
- Extend Workspace/Admin Health around category readiness and policy state.
- Keep provider-specific diagnostics support-safe and admin/operator-only.
- Map existing stack to categories: Keycloak/Auth, Matrix/Chat, Nextcloud/Files, backend calendar
  facade, OpenProject Boards validation, MatrixRTC Calls target, and OpenClaw as the first guarded
  ARC runtime provider.

Evidence gate:

- Admin/operator can inspect category readiness.
- Member sees only ready capabilities or impact-level unavailable states.
- Existing provider-specific routes remain behind Weave facades.

Acceptance criteria for the initial #264 slice:

- Workspace/Admin Health shows Keycloak platform identity/security separately
  from chat, files, calendar, boards/tasks, meetings/calls,
  documents/collaboration, and Agent Runtime Control bounded contexts.
- Dogfood domain defaults are mapped as current provider choices only: Matrix/Chat,
  Nextcloud/Files and Calendar backing, OpenProject Boards validation, the MatrixRTC Calls target,
  and guarded OpenClaw runtime evidence.
- Normal members do not configure raw providers, OIDC clients, service endpoints, secrets, backup/restore, or diagnostics.
- Admin/operator diagnostics are support-safe and redact/avoid secrets, credential-bearing URLs, bearer tokens, raw downstream bodies, and unnecessary provider internals.
- Agent Runtime Control remains optional and fail-closed without current Keycloak entitlement,
  signed profile trust, state storage, and workload identity.
- `make acceptance-contract` maps the product-level scenario to executable marker evidence before any runtime implementation work expands scope.

### Phase C: RBAC and whitelisting

Goal: make open-source policy realistic before Weaver.

- Use Keycloak as the authority for identity, groups, roles, sessions, and
  workload clients.
- Integrate Entra ID/Auth0/Authentik/LDAP/AD as upstream Keycloak federation or
  broker sources, not Weave identity adapters.
- Start with simple backend-owned RBAC/policy profiles; evaluate Casbin as the first embedded open-source policy engine if policy complexity grows.
- Model capabilities as category-level permissions first, not low-level tool IDs.

Examples:

- `chat.read`, `chat.send`
- `files.read`, `files.upload`
- `calendar.read`, `calendar.manage_events`
- `boards.read`, `boards.update_task`
- `agent-runtime.profile.read`, `agent-runtime.lifecycle.write`
- `agent-runtime.wake`, `agent-runtime.approval.attest`

Evidence gate:

- Capabilities are deny-by-default.
- Role/group changes affect Weave product surfaces before any agent runtime exists.
- Policy state is visible to admins and support-safe for members.

### Phase D: Agent Runtime Control and Weaver integration

Goal: plug the PA into an already-governed product.

- Add Agent Runtime Control as an optional entitlement-bound admin capability, initially closed.
- Provision one disposable `RuntimeCell` and dedicated `weaver-cell-{cellId}` Keycloak workload
  client per entitled person; do not materialize dynamic clients in OpenTofu.
- Sign a short-lived `RuntimeProfile v2` from current entitlement and organization policy. The
  runtime receives references and maximum capabilities, never member/provider credentials.
- Materialize signed `WorkspaceManifest` revisions from WebDAV into ephemeral staging. Store
  sessions, databases, Matrix crypto state, plugin state, and generated configuration only in an
  encrypted external RuntimeStateStore; cells have no durable volume.
- Generate `openclaw.json`, channel/plugin setup, model defaults, and tool filters ephemerally from
  the current signed profile; they are not member UX or a second policy source.
- Treat Chat provider changes as Weave Chat domain migrations. Admin Console selects Matrix, Teams, iMessage, Slack, Telegram, or another supported Chat provider; Weave runs readiness/migration checks, binds credentials through the Credential Broker, updates backend Chat-domain routing/providerRefs, regenerates RuntimeProfile vNext while preserving the stock `channels.matrix` connection to the Weave northbound Matrix facade, and reloads/restarts that runtime if needed.
- Use the official OpenClaw Matrix plugin and native approval lifecycle. Do not recreate a custom
  `weave-chat` channel or member approval inbox.
- Keep MCP workload-only. Each cell obtains an exact-audience token through the MCP Client
  Credentials extension; the MCP edge exchanges rather than relays it and every domain performs
  current authorization at execution time.
- Keep MCP tools and signed skills absent until fixed catalogs, RuntimeProfile intersection,
  current domain authorization, ApprovalDecisionEvidence v2, ActionEvidence v2, and audit gates
  are executable.
- Use SecretRefs and short-lived workload tokens only. Provider secrets, refresh tokens, Matrix
  credentials, and backend exchange tokens never enter profiles or cell images.
- Track OpenClaw upstream, verify signed stable tags, classify every local patch, and delete a
  patch once its recorded upstream gap closes.

Evidence gate:

- A human or generic service token cannot access MCP or workload-only RuntimeProfile routes.
- Cross-cell profile, state, credential, and workspace access fails closed.
- Kill/recreate on another node succeeds from WebDAV plus external state without cell-owned bytes.
- Revocation wins over queued events, remembered approvals, stale profiles, and retries.
- Audit correlates Keycloak workload, immutable person owner, cell, RuntimeProfile, MCP exchange,
  approval decision evidence, domain operation intent, provider result, and ActionEvidence without
  leaking content or credentials.

## Cross-session tracking rule

Future Weave planning should preserve this order:

1. Product suite and provider categories.
2. Admin portal, Keycloak/RBAC, readiness, and whitelisting.
3. Agent Runtime Control as an optional entitlement-bound layer, with Weaver/OpenClaw as a runtime provider.

Do not regress to agent-first planning. Do not assume one required provider stack. Do not expose raw provider setup to normal members.

## Keycloak/RBAC capability profiles and whitelisting

The admin portal foundation owns Keycloak/RBAC capability profiles and
whitelisting before any Weaver runtime ships. Keycloak is the fixed platform
identity authority. Entra ID, LDAP/Active Directory, Authentik, Auth0, and
other OIDC/SAML sources may be federated or brokered through Keycloak without
changing Weave subjects, membership policy, or northbound client contracts.

Capability profiles are deny-by-default. Roles and groups map to category-level capabilities such as chat.read, chat.send, files.read, files.upload, calendar.read, and boards.update_task. The configured authoritative Keycloak group alone derives `agent-runtime.entitled`; no human role or placeholder Weaver grant does. Admins/operators may inspect support-safe effective policy state and profile keys; normal members only see provider-neutral product impact states such as available, disabled by policy, not configured, degraded, unavailable, or coming later.

Agent Runtime Control remains fail-closed without current entitlement, signed profile trust, workload identity, encrypted external state, and lifecycle reconciliation. RuntimeProfile contents never grant broad tool or collaboration-domain access.

## Agent Runtime Control integration

Agent Runtime Control issues only a support-safe signed RuntimeProfile v2 from current Keycloak
entitlement, organization policy, and an authoritative server-owned cell binding. Generated
OpenClaw configuration is ephemeral
implementation output. Portable workspace files remain on WebDAV, runtime state remains encrypted
outside the cell, and the cell owns zero durable bytes.

The default posture remains fail-closed:

- Agent Runtime Control is optional and disabled without current exact `/capabilities/weaver` organization membership;
- profile signing, portable JPA persistence, external encrypted state, and workload identity must all be
  configured before the administrative controller exists;
- a dedicated cell workload client must match the immutable Keycloak subject/client binding;
- runtime-visible capabilities are an upper bound intersected with current domain authorization;
- exec and elevated surfaces stay disabled unless a future constrained admin profile explicitly enables them;
- runtime profile generation is audited before provisioning.

OpenClaw remains a constrained upstream runtime target. Forking core code is justified only by a
proven upstream gap with an isolated test, owner, upstream reference, and deletion criterion.
