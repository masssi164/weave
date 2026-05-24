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
