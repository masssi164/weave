# Organization embedding contract

Status: strategic foundation for the next Weave sprint. This contract blocks new provider-feature work until the organization, identity, policy, and adapter-replacement boundaries are explicit enough for real deployments.

## Product promise

Weave must fit into organizations instead of forcing organizations to fit into Weave.

That means Weave supports two first-class entry paths:

1. **Existing organization embedding** — an organization keeps current identity, groups, collaboration tools, files, tasks, calendar, and governance where sensible. Weave maps them into stable Weave product domains and exposes a better, more accessible operating layer.
2. **New organization bootstrap** — a new organization can start with Weave's recommended self-hosted defaults, then later replace adapters or mix in cloud/external services without rewriting member workflows.

The dogfood stack is a recommended default, not the product boundary. Provider neutrality is accepted only when mixed self-hosted, managed-cloud, and external-provider deployments preserve the same member-facing Weave concepts.

## Non-negotiable boundaries

- Existing identity and governance remain authoritative unless an admin explicitly chooses Weave-managed ownership for a field or capability.
- Member UX is Weave-owned and provider-neutral. Members do not configure OIDC, SAML, LDAP, SCIM, provider URLs, secrets, readiness probes, or migration plans.
- The Admin Console is the organization control plane, not a shadow identity provider. It maps and explains authority; it does not silently fork identity or policy.
- Every provider-backed object keeps provenance, source-of-truth, mapping status, lossy-field notes, export/delete expectations, and audit references.
- Provider replacement is a planned admin workflow with preflight, dry-run, mapping reports, rollback/retention notes, and support-safe evidence.
- Unknown identities, groups, roles, providers, or mappings fail closed.

## Organization lifecycle

### 1. Intake

An owner or delegated setup admin records:

- organization name and legal/admin contacts;
- verified domains and allowed invite domains;
- whether this is an existing organization, a new organization, or a hybrid migration;
- identity provider choices and constraints;
- provider categories to connect first;
- residency, retention, export, audit, and compliance needs;
- guest/external collaboration policy;
- break-glass account and last-admin protection requirements.

### 2. Tenant and domain binding

Weave creates an organization tenant/namespace and binds verified domains. Domain verification must be explicit and auditable. Multi-domain organizations are normal; single-domain assumptions are not allowed in product contracts.

Required states:

- `pending_verification`
- `verified`
- `rejected`
- `revoked`
- `transferred`

Domain state affects invite policy and SSO routing, but never becomes the primary identity key for users.

### 3. Identity source selection

Admins choose a primary identity source and optional upstream directories:

- Entra ID, Okta, Keycloak, Authentik, Auth0, or another OIDC/SAML provider;
- LDAP/AD as an upstream directory, preferably mediated by Entra, Keycloak, Authentik, or equivalent;
- SCIM 2.0 for provisioning/deprovisioning where available;
- JIT creation or manual invites only for small/dev deployments or explicitly bounded guest paths.

Weave records which system owns each attribute class:

- authentication and MFA;
- lifecycle state;
- groups and roles;
- department/manager/HR attributes;
- local preferences;
- Weave capability policy.

### 4. Provisioning dry-run

Before member go-live, the Admin Console runs a dry-run import/reconcile:

- users and lifecycle states;
- groups, nested groups, and memberships;
- provider roles/claims;
- guest/external identities;
- service principals and connector identities;
- conflicts, duplicates, deleted/recreated accounts, stale groups, and missing owners.

Conflicts are quarantined for manual review. Weave must not auto-merge identities from weak signals such as email alone.

### 5. Role, group, and capability mapping

Provider groups and roles are inputs, not product permissions.

Admins map them into:

- organization roles;
- context roles;
- capability profiles;
- provider-category access;
- delegated admin/operator scopes.

Mapping must be previewable before activation. The preview answers: "What can this person/group do, where, and why?"

### 6. Provider-category selection

Admins select providers by category, not by member-facing feature names:

- fixed Keycloak platform-identity boundary;
- chat;
- files and documents;
- calendar;
- boards/tasks;
- meetings/calls;
- decisions/evidence;
- manuals/help;
- admin health/readiness;
- Weaver runtime, disabled until governed runtime policy exists.

Each category records provider posture:

- `recommended_self_hosted_default`
- `external_existing_provider`
- `managed_cloud_provider`
- `hybrid_composite`

Mixed deployments are valid. For example, an organization may use Entra ID for identity, Teams for chat, SharePoint/OneDrive for files, OpenProject for tasks, and a self-hosted Weave backend for policy/readiness.

### 7. Effective policy and readiness preview

Before inviting members, the Admin Console shows:

- member-visible capability states;
- admin/operator readiness states;
- denied capabilities and reasons;
- stale mappings;
- degraded providers;
- missing consent or expired external app grants;
- support-safe next actions;
- risk notes for cloud/external providers.

Member-visible states are limited to:

- `available`
- `disabled_by_policy`
- `not_configured`
- `degraded`
- `unavailable`
- `coming_later`

Admin/operator-only states may additionally include:

- `admin-action-required`
- `misconfigured`
- `sync-pending`
- `conflict-quarantined`
- `migration-dry-run-required`
- `unsupported`

`usable` may appear as plain-language copy, but contracts should encode `available`.

### 8. Invite/go-live

Only after identity, policy, readiness, and support-safe diagnostics are acceptable should admins invite or activate members.

Allowed entry paths:

- organization auth URL;
- verified-domain discovery;
- invite link;
- deep link into an allowed context after SSO.

The member client fetches the organization manifest and renders only effective Weave capabilities. It does not expose provider setup, raw diagnostics, or secrets.

### 9. Reconciliation and deprovisioning

Weave continuously or periodically reconciles:

- active/suspended/deleted users;
- group rename/removal;
- membership drift;
- guest expiry;
- stale service principals;
- provider consent expiration;
- SecretRef rotation;
- provider readiness changes;
- mapping-loss and conflict reports.

Deprovisioning disables access immediately, revokes sessions/tokens where possible, removes capability grants, and preserves or reassigns content according to retention/legal-hold policy. Weave must never automatically delete the last owner/admin or break-glass route.

## Canonical organization model

Required product-level concepts:

- `Organization`
- `Tenant`
- `Domain`
- `IdentitySource`
- `UserAccount`
- `Person` where identity consolidation is needed
- `ExternalGuest`
- `ServicePrincipal`
- `Group`
- `Role`
- `Context` / `Space`
- `Channel`
- `CapabilityProfile`
- `ProviderConfig`
- `ProviderMapping`
- `Readiness`
- `RiskNote`
- `AuditEvent`
- `SecretRef`

Identity keys must be immutable provider identifiers, not email addresses. Email is contact/routing metadata.

## Role model

### Organization roles

- `owner`: legal/root governance, domain ownership, break-glass, final policy authority.
- `org_admin`: delegated organization administration, user/group/provider setup as allowed by owner policy.
- `security_admin`: audit, retention, compliance, risk notes, policy review.
- `operator`: runtime health, diagnostics, backup/restore, support bundles, readiness remediation; not automatically user or policy admin.
- `member`: normal internal user.
- `guest`: bounded external participant.

### Context roles

- `context_owner`
- `context_admin`
- `context_editor`
- `context_viewer`
- `context_guest`

Context roles are scoped. Workspace membership must not automatically grant every context permission.

### Provider roles

Provider roles and groups are imported evidence. They never directly grant Weave capabilities without a mapping policy.

### Capability permissions

Capability permissions are category-level and deny-by-default, for example:

- `chat.read`, `chat.send`
- `files.read`, `files.upload`, `files.share`
- `calendar.read`, `calendar.manage_events`
- `boards.read`, `boards.update_task`
- `admin.provider.readiness.view`
- `admin.provider.configure`
- `admin.policy.edit`
- `operator.support_bundle.create`
- `operator.backup.restore`
- `agent-runtime.entitled`
- `agent-runtime.profile.read`
- `agent-runtime.lifecycle.write`

### Machine principals

Connectors, migration jobs, and bots use scoped non-human identities. ARC-bound Weaver/OpenClaw cells additionally require a unique confidential Keycloak client, exact workload audience/scope, current cell/profile binding, rotation/revocation, and audit correlation. Shared bot users or shared runtime service accounts are forbidden.

## Organization topologies to support

### Recommended self-hosted default

- Identity: Keycloak/Auth or Authentik.
- Chat: Matrix/Synapse.
- Files/docs: Nextcloud/WebDAV/WOPI-compatible editor where supported.
- Boards/tasks: OpenProject.
- Calls: MatrixRTC Profile 0 with LiveKit as the first replaceable SFU adapter.
- Weave: canonical domains, policy, readiness, member UX, admin console.

### Microsoft-heavy existing organization

- Identity: Entra ID.
- Chat: Teams.
- Files/docs: SharePoint/OneDrive/Microsoft 365.
- Boards/tasks: Planner/Jira/OpenProject depending on team reality.
- Guests: Entra B2B / External ID.
- Weave: accessibility-first operating layer, policy explanations, domain facades, readiness, migration/export evidence.

### Hybrid organization

- Identity: LDAP/AD via Keycloak/AuthentiK/Entra bridge.
- Chat: Slack or Matrix.
- Files: SharePoint, Nextcloud, or S3-compatible storage.
- Boards: OpenProject/Jira/Planner-like adapter.
- Meetings: existing provider or LiveKit.
- Weave: unified organization model, provider-category readiness, anti-silo mapping, audit, and accessible member surfaces.

## Anti-silo acceptance

Weave is not accepted as organization-ready until each provider-backed domain can show:

- source-of-truth declaration;
- stable Weave ID and backend-only provider mapping;
- export path or explicit non-goal;
- delete/deprovision behavior;
- provenance metadata;
- lossy-field report;
- admin-visible risk note;
- support-safe readiness;
- member fallback that does not force a Weave-only lock-in.

## Strategy sprint acceptance

The next sprint should close this contract before new provider feature implementation. It is accepted only when:

- organization lifecycle and setup flows are documented from intake to reconciliation;
- identity provisioning strategy covers OIDC, SAML, SCIM, LDAP/AD, guests, service principals, and deprovisioning;
- effective policy response shape is specified;
- provider replacement/migration contract exists;
- mixed self-hosted/cloud/external topologies are first-class;
- member/admin/operator state vocabulary is normalized;
- acceptance scenarios prove no provider leakage into normal member paths;
- implementation issues are cut from the strategy, not guessed from old epics.
