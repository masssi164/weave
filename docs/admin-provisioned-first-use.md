# Admin-provisioned first use boundary

Status: v0.1 release contract for issues #259, #250, and #212.

## Product rule

The detailed strategic contracts are [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). This page remains the v0.1 first-use boundary for member/admin separation.

Normal organization members land in an admin-provisioned workspace. They sign in with Weave SSO, use Weave-owned chat, files, calendar, boards, meetings, and decisions surfaces, and see either complete capabilities or simple impact/fallback states.

Normal members must not configure OIDC, realms, provider URLs, service endpoints, backup/restore, policy, or infrastructure readiness. They must not see provider setup diagnostics, raw provider errors, scaffold cards, roadmap cards, preview cards, or coming-soon setup explanations in the normal product path.

Admins and operators provision identity, domains, provider stack, policy, backup/restore, support bundles, and release readiness before inviting normal users. Workspace Health is the admin/operator control plane for setup, readiness, degraded services, and support-safe diagnostics.

## Client/Admin Console split

Members enter or open only an organization auth URL, invite link, or deep link. After SSO, the Weave Client consumes the support-safe organization manifest and effective capability states, then renders member work surfaces. Member-visible manifest states are limited to `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`.

The Admin Console owns organization creation/bootstrap, IDM/provider setup, provider/category selection, endpoint URL management and rotation, readiness and diagnostics, users/groups/roles, RBAC/capability profiles, deny-by-default policy, org-wide defaults, audit logs, and privacy/compliance/risk notes. Whitelisting belongs to the Admin Console: provider, tool, and agent allowlists are configured there, while the client only consumes effective policy/capabilities.

## Provider-category admin boundary

Provider setup is category-first, not vendor-first. Workspace Health and admin setup must model these product/admin categories before member use:

- identity/IDM;
- chat;
- files;
- calendar;
- boards/tasks;
- meetings/calls;
- documents/collaboration;
- Weaver, disabled by default until a later admin policy enables the governed per-user PA runtime.

The current dogfood defaults map into those categories as provider selections and readiness signals: Keycloak/Auth for identity/IDM, Matrix for chat, Nextcloud for files and calendar backing, OpenProject for boards/tasks validation, and LiveKit for meetings readiness. These names belong in admin/operator setup, readiness, support-safe diagnostics, and documentation. They must not become the normal member-facing product model.

Normal members never configure raw providers, service endpoints, OIDC clients, provider secrets, backup/restore paths, or provider diagnostics. Members see ready Weave capabilities or short impact/fallback states. Admins/operators see support-safe readiness and next actions without secret values, bearer tokens, credential-bearing URLs, raw downstream error bodies, or provider internals that are not needed for remediation.

## Role boundary

v0.1 keeps the compact role vocabulary below, while the strategy contract sharpens delegated scopes for `org_admin`, `security_admin`, context roles, guests, and machine principals. `operator` is intentionally distinct from `admin`: operators may diagnose and execute delegated operational actions, but they do not automatically manage identity, policy, retention, or user invitations.

| Role | First-use experience | Setup and health scope |
| --- | --- | --- |
| `owner` | Can enter the workspace and administer release readiness. | Full Workspace Health, identity/provider setup, invite activation, policy, backup/restore, support-bundle actions, domain ownership, and break-glass governance. |
| `admin` | Can enter the workspace and administer delegated readiness. | Workspace Health, provider readiness, user activation, policy, and support diagnostics allowed by owner policy. |
| `operator` | Can enter the workspace to execute delegated operational readiness. | Workspace Health diagnostics, provider/service readiness verification, backup/restore/support-bundle actions, and support-safe remediation delegated by owner/admin policy; not automatic user or policy administration. |
| `member` | Lands in the ready workspace after invite/activation. | No OIDC/provider/infra setup. Sees only complete capabilities or simple impact/fallback states such as “Calendar is unavailable; ask an admin.” |
| `guest` | Lands only in explicitly permitted guest scopes. | No workspace setup, provider diagnostics, or member/admin affordances. |

The current realm/role generator contract lives in `infra/KEYCLOAK_CONTRACT.md`, `infra/weave-workspace/02-keycloak-setup`, and the local/dev activation helper documented in `infra/docs/admin-user-activation.md`. Those operator paths are not normal-user help.

## Capability state taxonomy

Every release-scope capability shown in product UI must be classified as exactly one of these states:

1. **Ready for users** — visible to members; backed by Weave UI, backend facade, health/readiness state, support-safe errors, and evidence.
2. **Admin setup required** — hidden from members unless an impact-only unavailable state is necessary; visible in Workspace Health with the next admin/operator action.
3. **Disabled by policy** — member copy is short and policy-focused; admin copy points to policy configuration.
4. **Broken/degraded** — member copy states impact and safe fallback; Workspace Health shows support-safe diagnostics and support-bundle evidence.
5. **Not in this release** — absent from normal member navigation and product first-use flows; may appear only in roadmap/docs.

No release-scope product surface may be classified as “preview”, “scaffold”, or “coming soon” for normal users.

## Member acceptance criteria

A newly invited `member` must be able to:

- sign in once through Keycloak-backed Weave SSO;
- reach a ready workspace shell without configuring OIDC, provider URLs, realms, Matrix, Nextcloud, CalDAV, OpenProject/Vikunja/Deck, LiveKit, backup/restore, or infrastructure details;
- see ready channels/home state and use complete capabilities through Weave concepts;
- see only impact/fallback copy for unavailable, disabled-by-policy, not-configured, degraded, or coming-later capabilities;
- avoid raw provider names and raw provider failures in the core workflow;
- never see provider setup diagnostics, Workspace Health setup internals, roadmap panels, scaffold cards, preview cards, or coming-soon setup language in normal navigation/settings/first-use.

## Admin/operator acceptance criteria

An `owner`, `admin`, or operator must be able to:

- provision identity, OIDC realm/client/roles/groups, domains, providers, policies, backup/restore, and release readiness before member first use;
- inspect Workspace Health as the admin/operator control plane for auth, Matrix, files, calendar, boards, meetings, E2EE posture, backup/restore, support-bundle safety, and latest smoke/E2E state;
- see exact next actions for not_configured, degraded, disabled_by_policy, unavailable, or coming_later services without leaking secrets, bearer tokens, credential-bearing URLs, room tokens, or raw downstream errors;
- understand what members can currently do before inviting them;
- use support bundles and diagnostics that share the same sanitized status categories as Workspace Health.

## Test and prompt-quality contract

Future work on first-use, settings, navigation, or Workspace Health must include tests or docs that answer these questions explicitly:

1. Which role sees this surface: owner/admin/operator, member, guest, or nobody in release scope?
2. Is the capability ready, admin setup required, disabled by policy, broken/degraded, or not in this release?
3. What exact member impact/fallback copy appears if the capability is not ready?
4. What exact admin/operator next action appears in Workspace Health?
5. Which deterministic test prevents preview/setup/provider leakage from returning to normal-user paths?

The fast guard is `client/test/architecture/admin_provisioned_first_use_contract_test.dart`; role-specific widget assertions also live under `client/test/features/`.

## IDM/RBAC capability profiles

Admins/operators configure the selected IDM adapter and map roles/groups into Weave capability profiles before inviting members. Keycloak is the self-hosted default, while Entra ID, Authentik, Auth0, and other OIDC/SAML providers remain adapter-compatible choices.

Admins/operators see support-safe effective policy state: IDM category, profile keys, role/group-derived grants, deny-by-default posture, provider readiness, and how policy/readiness maps to member states such as `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`. They do not need secret values in normal health views.

Members only see provider-neutral manifest states: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`. They never see raw provider setup, OIDC/SAML wiring, service endpoints, provider secrets, or diagnostics. Weaver appears only as a disabled-by-policy placeholder until a later governed per-user runtime policy exists.

## Governed Weaver runtime policy

Weaver follows the same admin-provisioned boundary as every other provider category. User-rights, organization-whitelisted capabilities is the rule: a personal assistant may only receive the normal user's rights through capability channels the organization has explicitly enabled.

Admins/operators control the Weaver category, the runtime generator, the groups that may receive `weaver.enabled`, and the capability/tool allowlist. Normal members do not configure Docker, OpenClaw plugins, provider adapters, service endpoints, or secrets. They either receive an available governed profile or an impact-only disabled_by_policy/not_configured state.

The generated runtime profile is support-safe and runtime profile generation is audited. It includes per-user Docker isolation metadata, plugin/tool allowlists, and allowed capability keys, while exec and elevated surfaces remain disabled by default unless a later constrained admin profile explicitly enables them.
