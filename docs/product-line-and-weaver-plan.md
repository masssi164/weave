# Weave product line and Weaver integration plan

Positioning line: **Weave – Collaboration Seamlessly Woven with Agentic AI, Activated on Your Terms.** This line is valid only with the claim gates in the product trust matrix and Sprint 30 evidence pack.

Status: active product direction, 2026-05-24.

## Decision lock

Sprint 21 update: [Product reality foundation](product-reality-foundation.md) is the active release-claim and proof-order gate for the next sprint sequence. Claim boundary: it narrows release/customer-ready wording to named `release_ready` evidence and moves human validation after automated provider-switch, rollback, Weaver runtime, and restore proof.

Weave is planned product-first, not agent-first.

Weave is a provider-neutral organization operating layer and integration suite. It lets an organization keep existing systems for identity, chat, files, calendar, boards/tasks, documents, meetings, decisions, help/manuals, release evidence, and collaboration while presenting them through coherent Weave product concepts.

Weaver is a later personal-assistant layer that plugs into this already-governed organization model. It must not define the product architecture by itself.

## Priority realignment, 2026-05-26

Current priority is not Office/ONLYOFFICE integration or another provider feature slice. Office remains a later provider category until the more fundamental organization-embedding, provider-facade, identity/provisioning, policy, and replacement contracts are in place.

The next strategy sprint is [Organization embedding and provider-neutrality proof](strategy-sprint-org-embedding-plan.md). It is supported by [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). New feature/provider work should be sliced from those contracts rather than from a dogfood-stack-shaped assumption.

The active priority order is:

1. **Prove organization embedding.** Weave must support both existing organizations and newly bootstrapped organizations: verified domains, OIDC/SAML auth, SCIM/LDAP/AD provisioning paths, role/group mapping, guests, service principals, deprovisioning, break-glass, and effective policy previews before member go-live.
2. **Complete server-side domain facading.** The client consumes Weave domains such as Chat, Files, Calendar, Boards/Tasks, Meetings, Decisions, and Health. Provider-specific mapping, credentials, readiness, migration, lossy conversion notes, and provider failures stay server/admin/operator side. For example, an admin may later replace Slack with Synapse/Matrix for the Chat domain through the Admin Console, while conversations, membership, history policy, attachments, and support-safe migration evidence are carried over by a server-owned migration path.
3. **Prove adapter replacement and anti-silo guarantees.** Mixed self-hosted/cloud/external deployments, such as Entra ID + Teams + SharePoint + OpenProject or Keycloak + Matrix + SharePoint, are first-class. Every provider-backed category needs source-of-truth, export/delete, provenance, lossy-field, risk, and dry-run replacement behavior.
4. **Embed manuals as product help.** The member Help surface embeds the MkDocs user manual in `weave/client`; the Admin Console embeds the admin/operator manual. Both manuals use the same CSS variables/design tokens/corporate design as the app surfaces and must remain accessible in iframe/webview form.
5. **Automate README release evidence and repositioning.** README must automatically include generated release notes, describe what Weave is and where it is going, and stop presenting Weave as merely a collaboration platform. Public marketing copy still needs specialist review before final release positioning.

Issue hygiene rule: close or supersede provider-specific implementation epics that are lower priority than the domain-facade/admin-console path, especially office-first or fixed-stack issues. Keep or rewrite issues only when they directly support domain contracts, provider swaps/migrations, admin readiness, embedded manuals, or release-note automation.

## Product line

### 1. Admin portal and organization setup

The admin portal is the control center for organization setup and policy.

It must let owners/admins choose and manage provider categories:

- Identity/IDM: Keycloak, Entra ID, Authentik, or another OIDC/SAML source.
- Chat: Matrix, Microsoft Teams, Slack, Nextcloud Talk, or another supported channel.
- Files: Nextcloud Files, SharePoint/OneDrive, S3-compatible storage, SMB, or another provider.
- Calendar: Weave-managed shared calendar facade backed by the selected provider stack.
- Boards/tasks: Weave task/board model backed by OpenProject first, with other adapters possible.
- Meetings/calls: LiveKit or another future provider through backend token facades.
- Documents/collaboration: provider adapters, not direct product dependency on one vendor.
- Weaver: disabled by default until the organization enables the PA runtime and tool policy.

The admin portal owns:

- provider selection and readiness;
- role and group mapping;
- policy profiles;
- capability availability;
- support-safe diagnostics;
- backup/restore and operator evidence;
- later: Weaver capability/tool allowlists.

Normal members do not configure raw providers.

### Weave Client and Organization/Admin Console split

The Weave Client owns member work surfaces: authenticated home, channels, chat, files, calendar, boards/tasks, meetings, decisions, profile, and personal settings. Its setup boundary is intentionally narrow: a member enters or opens an organization auth URL, invite link, or deep link, completes SSO, consumes the authenticated organization manifest, and renders only effective capability states. The client may show member-visible states `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; it must not manage raw provider URLs, secrets, endpoint rotation, provider setup, support diagnostics, policy authoring, or whitelist configuration.

The Organization/Admin Console owns organization bootstrap, provider choice, policy, readiness, diagnostics, and whitelisting. It manages organization creation, users/groups/roles or IDM sync, Keycloak/Entra/Authentik/Auth0/OIDC/SAML/SCIM/LDAP-adapter setup, category provider selection, endpoint URL management and rotation, support-safe health diagnostics, capability/RBAC profiles, deny-by-default policy, provider/tool/agent allowlists, external-provider privacy/compliance/risk notes, audit logs, and org-wide defaults.

The handoff contract is: org auth URL or invite/deep link -> SSO -> support-safe organization manifest -> member capability states. Provider/category/admin management must not be pressed into the member client.

### 2. Provider-neutral Weave suite

Weave models collaboration categories, not vendor products.

The product surfaces remain Weave-owned:

- Home and activity overview;
- personal messages;
- channels as workspaces;
- channel tabs for chat, decisions, files, boards/tasks, calendar/events, meetings, and read-only Weaver scout;
- Workspace/Admin Health for readiness and support;
- accessible settings and policy-visible capability states.

Provider adapters sit behind Weave contracts. A Microsoft-heavy organization should be able to use Entra ID, Teams, SharePoint, and Planner/Jira-style integrations. A self-hosted organization should be able to use Keycloak, Matrix, Nextcloud, OpenProject, and LiveKit. Mixed setups must be valid. Provider swaps are admin-controlled domain migrations, not client rewrites: the server owns mapping old provider objects to canonical Weave domain objects, recording migration evidence, surfacing conflicts/lossy fields, and keeping member UX stable.

The contract seam is category-first: feature capabilities for identity/IDM, chat, files, office/docs collaboration, meetings/calls, boards/tasks, calendar, and Weaver runtime are separate from adapter implementations. Workspace Health and policy enforcement must evaluate category contracts and stable member impact states, while concrete providers remain admin-selected adapters.

Provider choice is risk-aware, not prohibition-based. Weave recommends the sovereign/self-hosted default posture where it is sensible, but existing organizations may keep external providers for selected categories, such as self-hosted identity with Teams chat, SharePoint/OneDrive files, Microsoft 365 Office integration, and OpenProject tasks. Admin/provider readiness records the choice model as `recommended_self_hosted_default`, `external_existing_provider`, `managed_cloud_provider`, or `hybrid_composite`, plus support-safe privacy/compliance risk notes. Member manifest vocabulary remains stable: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; member copy may describe available capabilities as usable.

Adapter seams should prefer well-known interoperability contracts where practical: OIDC/SAML for SSO/federation, SCIM for user and group provisioning/deprovisioning, WebDAV/CMIS for file/content abstraction, CalDAV/iCalendar/VTODO for calendar and task-shaped records where applicable, and WOPI-style seams between storage and web office editors. Apache Camel, Nango, and Open Integration Hub remain research references for connector/adapter plus normalized-model patterns; do not adopt one blindly without an ADR.

This means implementation work should add category contracts and adapter seams before adding vendor-specific UX. Provider names belong in admin/operator readiness and documentation, not as the main member-facing product model.

### 3. Weaver as governed per-user PA runtime

Weaver is optional and later.

When enabled, each user receives a per-user Weaver runtime derived from OpenClaw and isolated in its own Docker container. The organization provides a baseline runtime and policy; the user may configure their personal workspace and agent defaults inside those boundaries.

The central rule is:

> user-rights, organization-whitelisted capabilities.

The PA may act with the user's normal rights, but only through organization-approved capability channels. Routine operation must not depend on per-call confirmation. Step-up confirmation is reserved for exceptional high-risk actions.

Admin policy controls:

- which Weaver runtime profile a user/group receives;
- which tools/capabilities are visible to that runtime;
- which provider adapters can be used;
- whether exec-like capabilities exist at all;
- sandbox/workspace defaults;
- connector/package approval, versioning, revocation, and audit.

OpenClaw configuration remains an implementation target, not the product model. Weave should generate or constrain the Weaver/OpenClaw runtime from organization policy. ACP/Codex-style developer assistance may become one governed capability channel for approved users, but only inside the same opt-in, per-user, organization-whitelisted policy boundary; it must not become autonomous team-agent scope or a shortcut around disabled exec/elevated defaults.

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
- No normal-member UX claims raw provider setup or future Weaver runtime as shipped v0.1.
- Product acceptance includes an admin/provider-category scenario proving categories, dogfood-default mapping, member/admin boundaries, support-safe diagnostics, and Weaver-disabled-by-default ordering.

### Phase B: admin/provider model

Goal: implement category-based setup and readiness.

- Define provider category entities in backend/domain docs: identity, chat, files, calendar, boards/tasks, meetings, docs/collaboration, Weaver.
- Extend Workspace/Admin Health around category readiness and policy state.
- Keep provider-specific diagnostics support-safe and admin/operator-only.
- Map existing stack to categories: Keycloak/Auth, Matrix/Chat, Nextcloud/Files, backend calendar facade, OpenProject Boards validation, LiveKit Meetings.

Evidence gate:

- Admin/operator can inspect category readiness.
- Member sees only ready capabilities or impact-level unavailable states.
- Existing provider-specific routes remain behind Weave facades.

Acceptance criteria for the initial #264 slice:

- Workspace/Admin Health and setup language names identity/IDM, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, and Weaver as first-class categories.
- Dogfood defaults are mapped as current provider choices only: Keycloak/Auth, Matrix/Chat, Nextcloud/Files and Calendar backing, OpenProject Boards validation, and LiveKit Meetings readiness.
- Normal members do not configure raw providers, OIDC clients, service endpoints, secrets, backup/restore, or diagnostics.
- Admin/operator diagnostics are support-safe and redact/avoid secrets, credential-bearing URLs, bearer tokens, raw downstream bodies, and unnecessary provider internals.
- Weaver remains disabled by default and later than admin portal, IDM/RBAC, readiness, and whitelisting.
- `make acceptance-contract` maps the product-level scenario to executable marker evidence before any runtime implementation work expands scope.

### Phase C: RBAC and whitelisting

Goal: make open-source policy realistic before Weaver.

- Use OIDC/IDM as source of identity, groups, and roles.
- Keep Keycloak as the self-hosted default but support Entra ID/Auth0/Authentik-style OIDC through adapter contracts.
- Start with simple backend-owned RBAC/policy profiles; evaluate Casbin as the first embedded open-source policy engine if policy complexity grows.
- Model capabilities as category-level permissions first, not low-level tool IDs.

Examples:

- `chat.read`, `chat.send`
- `files.read`, `files.upload`
- `calendar.read`, `calendar.manage_events`
- `boards.read`, `boards.update_task`
- `weaver.enabled`, `weaver.files_read`, `weaver.exec_disabled`

Evidence gate:

- Capabilities are deny-by-default.
- Role/group changes affect Weave product surfaces before any agent runtime exists.
- Policy state is visible to admins and support-safe for members.

### Phase D: Weaver integration

Goal: plug the PA into an already-governed product.

- Add Weaver as a provider/category in the admin portal, initially disabled.
- Create per-user Dockerized Weaver runtime profiles.
- Generate baseline OpenClaw/Weaver config from Weave policy:
  - per-user workspace;
  - isolated agent directory;
  - plugin allowlist matching selected chat/provider categories;
  - tool/capability allowlist from admin policy;
  - sandbox defaults;
  - exec disabled or heavily restricted by default.
- Generate that runtime config only from a signed Weave `WeaverRuntimeProfile`; `openclaw.json`, channel/plugin setup, MCP servers, model defaults/fallbacks, and tool filters are implementation output, not member UX or a second policy source.
- Treat Chat provider changes as Weave Chat domain migrations. Admin Console selects Matrix, Teams, iMessage, Slack, Telegram, or another supported Chat provider; Weave runs readiness/migration checks, binds credentials through the Credential Broker, updates backend Chat-domain routing/providerRefs, regenerates RuntimeProfile vNext while preserving the stable `channels.weave-chat` OpenClaw channel, and reloads/restarts that channel/runtime if needed.
- Keep the messaging/channel plane separate from the MCP/tool plane. Weave owns product domains, UI/API, spaces/channels/threads, RBAC/policy, approval policy, audit, RuntimeProfile generation, and the Weave MCP server/domain tools. Weaver owns the OpenClaw-derived `weave-chat` ChannelPlugin, session routing, inbound/outbound Weave Chat messaging, approval hint rendering, and MCP client binding to Weave MCP servers. MCP `chat.send_message` is a domain tool an already-running agent may call; it is not the user-to-Weaver chat channel.
- Keep raw OpenClaw dashboard/config/wizard surfaces locked down or RBAC-stripped for members. Member `Mein Weaver` settings may cover style, memory/workspace, admin-approved model aliases, allowed skills, and allowed personal MCP connection flows only.
- Keep MCP servers, skills, `bundle-mcp`, gateway, cron, exec, write, and patch-style tools default-deny unless admin policy explicitly grants a constrained capability. `tools.deny` is the hard global deny layer.
- Use CredentialRefs and short-lived runtime tokens only; provider secrets, OAuth refresh tokens, channel tokens, and MCP OAuth credentials live behind the Weave Credential Broker.
- Add audit for Weaver capability usage.
- Only fork OpenClaw where existing configuration/plugin hooks cannot enforce the required boundary.

Evidence gate:

- Weaver cannot see or call capabilities not enabled by admin policy.
- User workspace customization cannot escape org baseline.
- Exec/elevated surfaces are disabled by default and require explicit admin policy.
- Audit records include `runtimeProfileHash`, user, tool, domain, providerRef, credentialRef where applicable, and decision for model/channel/tool/MCP calls.

## Cross-session tracking rule

Future Weave planning should preserve this order:

1. Product suite and provider categories.
2. Admin portal, IDM/RBAC, readiness, and whitelisting.
3. Weaver PA runtime as optional governed layer.

Do not regress to agent-first planning. Do not assume one required provider stack. Do not expose raw provider setup to normal members.

## IDM/RBAC capability profiles and whitelisting

The admin portal foundation owns IDM/RBAC capability profiles and whitelisting before any Weaver runtime ships. Keycloak/Auth remains the self-hosted default identity choice, but the product contract is provider-neutral: selected IDM adapters may be Keycloak, Entra ID, Authentik, Auth0, or other OIDC/SAML-compatible providers that can supply roles and groups without leaking raw setup to members.

Capability profiles are deny-by-default. Roles and groups map to category-level capabilities such as chat.read, chat.send, files.read, files.upload, calendar.read, boards.update_task, and Weaver placeholder keys. Admins/operators may inspect support-safe effective policy state and profile keys; normal members only see provider-neutral product impact states such as available, disabled by policy, not configured, degraded, unavailable, or coming later.

Weaver remains disabled by default until a later governed runtime policy exists. Issue #265 may expose Weaver placeholder capabilities only to prove whitelisting and fail-closed behavior; it must not start a per-user PA runtime or grant broad tool access.

## Governed Weaver runtime integration

Issue #266 adds the first governed Weaver runtime integration contract without making agents the product model. The Weaver category still follows the product order: provider-neutral Weave suite first, admin/provider/IDM/RBAC/readiness/whitelisting second, optional Weaver PA runtime third.

Weave now generates a support-safe per-user Dockerized Weaver/OpenClaw-derived runtime profile only from organization capability policy. Generated Weaver/OpenClaw config is implementation output from Weave policy, not a second agent policy model. The generated profile includes the baseline image/profile, isolated per-user workspace path, isolated agent directory, Docker network posture, plugin/tool allowlists, and capability allowlist.

The default posture remains fail-closed:

- the Weaver provider category is disabled by default;
- the runtime generator is disabled by default;
- user policy must explicitly grant `weaver.enabled` through an admin-selected group/profile;
- runtime-visible capabilities are intersected with admin-whitelisted capability keys such as `weaver.files_read`;
- exec and elevated surfaces stay disabled unless a future constrained admin profile explicitly enables them;
- runtime profile generation is audited before provisioning.

OpenClaw remains a constrained runtime target. Forking OpenClaw is only justified if configuration, plugin, or runtime hooks cannot enforce the generated policy boundary.
