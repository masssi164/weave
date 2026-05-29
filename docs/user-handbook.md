# User Handbook

This handbook is for people using a Weave organization after an owner/admin has provisioned it. Members do not configure raw providers, service endpoints, OAuth clients, backup jobs, provider URLs, or secrets.

## Onboarding and login

1. Open the organization invite, deep link, or organization auth URL from your admin.
2. Complete SSO in the configured identity provider.
3. Weave fetches the authenticated organization manifest and shows only the work surfaces and capability states available to you.

If your organization is not usable yet, Weave should explain the impact in plain language: `usable`/`ready`, `disabled`, `degraded`, or `policy-blocked`. `usable` is the member-facing concept; backend manifests may encode the same state as `ready`. Provider setup details stay with admins/operators.

## Workspaces and channels

Channels are workspace containers for collaboration. A channel can expose tabs for chat, decisions, files, boards/tasks, calendar, meetings, and read-only Weaver scout when those capabilities are enabled for your organization and role.

Expected member boundaries:

- Use Weave-owned channel and work surfaces.
- Follow links or prompts shown by Weave for available capabilities.
- Report degraded or blocked states with the support-safe text shown in the app.
- Do not enter raw Matrix, Nextcloud, OpenProject, LiveKit, Keycloak, or other provider admin details in the member client.

## Chat

Chat is a Weave product experience backed by the organization-selected chat provider. Matrix is the current dogfood provider, but provider names are not the everyday product model.

Use chat for:

- personal messages;
- channel conversations;
- accessible message composition;
- reactions, attachments, and threaded context as the backend facades expose them.

## Files and documents

Files use Weave-owned product routes and backend facades. The client should not receive storage-provider credentials or raw provider URLs with secrets. Documents/collaboration launch through safe backend-issued sessions when enabled.

## Tasks, boards, meetings, and calendar

Boards/tasks, shared calendar, and meeting capsules are capability-backed workspace surfaces. They may be available, disabled by policy, not configured, degraded, unavailable, or coming later depending on admin/provider readiness and your role.

- Calendar focuses on shared workspace/team/channel scheduling in the current product path.
- Boards/tasks use a Weave task model behind provider adapters.
- Meetings use backend token facades; media-provider secrets stay server-side.

## Capability states

| State | What it means for you |
| --- | --- |
| `usable` / `ready` | The capability is available for your role and organization. |
| `disabled` | The organization has not enabled this capability. |
| `degraded` | The capability exists but has a temporary health or provider problem. |
| `policy-blocked` | Your role/group does not currently have access. |

## Accessibility

Weave treats accessibility as release scope. Member surfaces should support keyboard use, screen readers, visible focus, sufficient target sizes, localized copy, and non-color-only state communication. See [Accessibility Release Gate](accessibility-release-gate.md) for the evidence model.

## Troubleshooting

Before asking an admin/operator for help, capture the support-safe information shown by Weave:

- the organization name/auth URL you used;
- the visible capability state and plain-language message;
- the time of the failure;
- your platform, app target, and app version if available.

Do not paste secrets, bearer tokens, cookies, private keys, raw provider URLs with credentials, or full downstream provider responses into support reports.

## Signing in

Your organization owner/admin configures SSO and workspace providers. As a normal member, use the Weave sign-in link or app entry point your organization provides; you do not configure Keycloak, OIDC, realms, client IDs, redirect URIs, or provider credentials. If a capability is unavailable, degraded, disabled, or policy-blocked, Weave shows the product-level state and your admin handles provider readiness in Workspace Health.
