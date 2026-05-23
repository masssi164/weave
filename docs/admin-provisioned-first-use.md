# Admin-provisioned first use boundary

Status: v0.1 release contract for issues #259, #250, and #212.

## Product rule

Normal organization members land in an admin-provisioned workspace. They sign in with Weave SSO, use Weave-owned chat, files, calendar, boards, meetings, and decisions surfaces, and see either complete capabilities or simple impact/fallback states.

Normal members must not configure OIDC, realms, provider URLs, service endpoints, backup/restore, policy, or infrastructure readiness. They must not see provider setup diagnostics, raw provider errors, scaffold cards, roadmap cards, preview cards, or coming-soon setup explanations in the normal product path.

Admins and operators provision identity, domains, provider stack, policy, backup/restore, support bundles, and release readiness before inviting normal users. Workspace Health is the admin/operator control plane for setup, readiness, degraded services, and support-safe diagnostics.

## Role boundary

| Role | First-use experience | Setup and health scope |
| --- | --- | --- |
| `owner` | Can enter the workspace and administer release readiness. | Full Workspace Health, identity/provider setup, invite activation, policy, backup/restore, and support-bundle actions. |
| `admin` | Can enter the workspace and administer delegated readiness. | Workspace Health, provider readiness, user activation, policy, and support diagnostics allowed by owner policy. |
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
- see only impact/fallback copy for unavailable, disabled, degraded, or policy-blocked capabilities;
- avoid raw provider names and raw provider failures in the core workflow;
- never see provider setup diagnostics, Workspace Health setup internals, roadmap panels, scaffold cards, preview cards, or coming-soon setup language in normal navigation/settings/first-use.

## Admin/operator acceptance criteria

An `owner`, `admin`, or operator must be able to:

- provision identity, OIDC realm/client/roles/groups, domains, providers, policies, backup/restore, and release readiness before member first use;
- inspect Workspace Health as the admin/operator control plane for auth, Matrix, files, calendar, boards, meetings, E2EE posture, backup/restore, support-bundle safety, and latest smoke/E2E state;
- see exact next actions for missing, degraded, disabled, or policy-blocked services without leaking secrets, bearer tokens, credential-bearing URLs, room tokens, or raw downstream errors;
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
