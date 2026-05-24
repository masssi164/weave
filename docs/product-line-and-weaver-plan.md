# Weave product line and Weaver integration plan

Status: active product direction, 2026-05-24.

## Decision lock

Weave is planned product-first, not agent-first.

Weave is a provider-neutral organization suite and integration layer. It lets an organization keep existing systems for identity, chat, files, calendar, boards/tasks, documents, meetings, and collaboration while presenting them through coherent Weave product concepts.

Weaver is a later personal-assistant layer that plugs into this already-governed organization model. It must not define the product architecture by itself.

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

The Weave Client owns member work surfaces: authenticated home, channels, chat, files, calendar, boards/tasks, meetings, decisions, profile, and personal settings. Its setup boundary is intentionally narrow: a member enters or opens an organization auth URL, invite link, or deep link, completes SSO, consumes the authenticated organization manifest, and renders only effective capability states. The client may show member-visible states `ready`, `disabled`, `degraded`, or `policy-blocked`; it must not manage raw provider URLs, secrets, endpoint rotation, provider setup, support diagnostics, policy authoring, or whitelist configuration.

The Organization/Admin Console owns organization bootstrap, provider choice, policy, readiness, diagnostics, and whitelisting. It manages organization creation, users/groups/roles or IDM sync, Keycloak/Entra/Authentik/Auth0/OIDC/SAML/SCIM/LDAP-adapter setup, category provider selection, endpoint URL management and rotation, support-safe health diagnostics, capability/RBAC profiles, deny-by-default policy, provider/tool/agent allowlists, external-provider privacy/compliance/risk notes, audit logs, and org-wide defaults.

The handoff contract is: org auth URL or invite/deep link -> SSO -> support-safe organization manifest -> member capability states. Provider/category/admin management must not be pressed into the member client.

### 2. Provider-neutral Weave suite

Weave models collaboration categories, not vendor products.

The product surfaces remain Weave-owned:

- Home and activity overview;
- personal messages;
- channels as workspaces;
- channel tabs for chat, files, boards/tasks, calendar/events, meetings, and decisions;
- Workspace/Admin Health for readiness and support;
- accessible settings and policy-visible capability states.

Provider adapters sit behind Weave contracts. A Microsoft-heavy organization should be able to use Entra ID, Teams, SharePoint, and Planner/Jira-style integrations. A self-hosted organization should be able to use Keycloak, Matrix, Nextcloud, OpenProject, and LiveKit. Mixed setups must be valid.

The contract seam is category-first: feature capabilities for identity/IDM, chat, files, office/docs collaboration, meetings/calls, boards/tasks, calendar, and Weaver runtime are separate from adapter implementations. Workspace Health and policy enforcement must evaluate category contracts and stable member impact states, while concrete providers remain admin-selected adapters.

Provider choice is risk-aware, not prohibition-based. Weave recommends the sovereign/self-hosted default posture where it is sensible, but existing organizations may keep external providers for selected categories, such as self-hosted identity with Teams chat, SharePoint/OneDrive files, Microsoft 365 Office integration, and OpenProject tasks. Admin/provider readiness records the choice model as `recommended_self_hosted_default`, `external_existing_provider`, or `managed_cloud_provider`, plus support-safe privacy/compliance risk notes. Member UX vocabulary remains stable: usable, disabled, degraded, or policy-blocked.

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

OpenClaw configuration remains an implementation target, not the product model. Weave should generate or constrain the Weaver/OpenClaw runtime from organization policy.

## Implementation plan

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
- Add audit for Weaver capability usage.
- Only fork OpenClaw where existing configuration/plugin hooks cannot enforce the required boundary.

Evidence gate:

- Weaver cannot see or call capabilities not enabled by admin policy.
- User workspace customization cannot escape org baseline.
- Exec/elevated surfaces are disabled by default and require explicit admin policy.

## Cross-session tracking rule

Future Weave planning should preserve this order:

1. Product suite and provider categories.
2. Admin portal, IDM/RBAC, readiness, and whitelisting.
3. Weaver PA runtime as optional governed layer.

Do not regress to agent-first planning. Do not assume one required provider stack. Do not expose raw provider setup to normal members.

## IDM/RBAC capability profiles and whitelisting

The admin portal foundation owns IDM/RBAC capability profiles and whitelisting before any Weaver runtime ships. Keycloak/Auth remains the self-hosted default identity choice, but the product contract is provider-neutral: selected IDM adapters may be Keycloak, Entra ID, Authentik, Auth0, or other OIDC/SAML-compatible providers that can supply roles and groups without leaking raw setup to members.

Capability profiles are deny-by-default. Roles and groups map to category-level capabilities such as chat.read, chat.send, files.read, files.upload, calendar.read, boards.update_task, and Weaver placeholder keys. Admins/operators may inspect support-safe effective policy state and profile keys; normal members only see product impact states such as ready, disabled, degraded, or policy-blocked.

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
