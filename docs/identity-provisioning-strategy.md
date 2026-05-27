# Identity provisioning strategy

Status: strategic identity/control-plane contract for organization embedding.

## Principle

Identity is a Weave control-plane dependency, not per-app glue. Weave must authenticate through existing organizational identity where possible, provision/deprovision through standard lifecycle channels, and keep authorization explainable through Weave capability policy.

## Standards posture

Preferred standards and protocols:

- OIDC Core 1.0 / OAuth 2.0 for modern authentication.
- Authorization Code + PKCE for interactive app sign-in.
- SAML 2.0 for enterprise/legacy IdP compatibility.
- SCIM 2.0 for user and group provisioning/deprovisioning.
- LDAPv3/AD as an upstream directory source, preferably mediated by Entra ID, Keycloak, Authentik, Auth0, Okta, or similar.
- OAuth 2.0 client credentials or workload identity for machine principals.

Reference anchors:

- OAuth 2.0: RFC 6749.
- PKCE: RFC 7636.
- OAuth metadata discovery: RFC 8414.
- JWT: RFC 7519.
- LDAPv3: RFC 4510/RFC 4511 family.
- SCIM schema/protocol: RFC 7643 and RFC 7644.
- OIDC Core 1.0 and SAML 2.0 remain standards-level contracts even where provider behavior differs.

## Canonical identity keys

Never use email as the primary identity key.

Required stable identity inputs:

- OIDC/SAML: `issuer` + `subject`.
- SCIM: `externalId` plus provider tenant/source metadata.
- Entra ID / Microsoft Graph: object ID.
- LDAP/AD: `objectGUID` or `objectSid`, not DN, CN, or mail.
- Keycloak: realm + user ID / federated identity link.

Canonical identity fields:

- `org_id`
- `account_id`
- optional `person_id` for intentional account consolidation
- `identity_source_id`
- `source_system`
- `issuer`
- `subject`
- `external_id`
- `immutable_provider_id`
- `email`
- `display_name`
- `lifecycle_state`
- `groups`
- `roles`
- `managed_fields`
- `last_reconciled_at`
- `mapping_status`

## Source ownership

Weave must record which source owns each field class.

Typical defaults:

- HRIS or directory: employment state, department, manager, cost center where available.
- IdP: authentication, MFA, SSO lifecycle, group membership where authoritative.
- SCIM source: user/group provisioning and deprovisioning.
- Weave: local preferences, member UI settings, Weave-only context membership, capability profiles, audit references.
- Provider adapters: external object provenance and provider-specific lossy metadata.

If two sources claim ownership of the same field class, Weave must quarantine or require explicit precedence policy.

## Authentication setup

### OIDC/OAuth 2.0

OIDC is preferred for modern organizations.

Requirements:

- authorization code flow with PKCE for interactive clients;
- issuer/audience/signature/expiry validation;
- nonce/state validation;
- JWKS rotation handling;
- metadata discovery where available;
- explicit mapping from claims to Weave account and policy inputs;
- no provider token leakage to the member client beyond the app session contract.

### SAML 2.0

SAML remains necessary for many organizations.

Requirements:

- signed assertions;
- audience and recipient validation;
- clock-skew handling;
- certificate rotation workflow;
- NameID/attribute mapping documented;
- group/role claims treated as inputs, not direct grants.

### LDAP/AD

LDAP/AD should usually be an upstream source behind an identity broker or provisioning bridge.

Direct LDAP integration is acceptable only when:

- immutable IDs are used;
- TLS and bind credentials are SecretRef-backed;
- group nesting, rename, deletion, and disabled accounts are handled;
- reconciliation is audited;
- Weave does not become the password authority unless explicitly deployed that way.

## Provisioning setup

SCIM 2.0 is the preferred provisioning/deprovisioning channel.

Minimum SCIM behavior:

- `/Users` create/read/update/deactivate;
- `/Groups` create/read/update/delete and membership changes;
- PATCH support or documented fallback behavior;
- initial import in dry-run/reconcile mode;
- conflict quarantine;
- regular reconciliation;
- support-safe failure and retry reporting.

Provisioning fallbacks:

- Microsoft Graph or vendor API where SCIM is unavailable;
- LDAP/AD sync bridge;
- just-in-time creation for small/dev deployments;
- manual invites only for bounded or bootstrap cases.

JIT creation does not replace deprovisioning. A JIT-created account still needs lifecycle reconciliation or explicit expiration.

## Conflict policy

Strong matches:

- same immutable provider identity;
- same SCIM `externalId` from the configured source;
- explicit admin-approved account merge into a `person_id`.

Weak matches:

- verified email;
- matching display name;
- matching department/team.

Weak matches can suggest candidates but must not auto-merge.

Never auto-merge:

- internal and guest identities by email alone;
- two different issuers' subjects;
- deleted/recreated accounts without immutable ID continuity;
- service principals and human users;
- accounts from unverified domains.

Conflict outcomes:

- `quarantined`
- `manual_review_required`
- `mapped`
- `ignored_by_policy`
- `rejected`

## Groups, roles, and effective policy

Provider groups and roles are evidence. Weave capabilities are granted only through explicit mapping policy.

The effective-policy response shape should include:

```json
{
  "subject": "weave-account-id",
  "organization": "weave-org-id",
  "context": "optional-context-id",
  "identitySources": ["source-id"],
  "groups": ["weave-group-id"],
  "orgRoles": ["member"],
  "contextRoles": ["context_viewer"],
  "providerRoleMappings": ["mapping-id"],
  "capabilityGrants": ["chat.read"],
  "denies": [
    {
      "capability": "admin.policy.edit",
      "reason": "missing_org_role",
      "source": "policy-id"
    }
  ],
  "readinessImpact": ["boards/tasks: admin-setup-required"],
  "auditRefs": ["audit-event-id"]
}
```

Acceptance rules:

- unknown groups deny by default;
- unknown roles deny by default;
- missing provider readiness denies or degrades according to policy;
- `operator` is a distinct role and not equivalent to `org_admin`;
- context roles do not automatically grant organization admin rights;
- provider roles never directly grant Weave capabilities without a mapping record.

## Keycloak realm desired-state dry-run

The first backend-owned provider-ops slice is a dry-run contract for Keycloak-compatible realm state. Admins/operators submit `currentState` (optional) and `desiredState` to `/api/admin/identity/realm/dry-run`; the backend normalizes and compares realm basics, clients, roles, groups, scopes, redirect origins, claim mappers, and required feature mappings.

The report is deterministic and support-safe:

- every change record has a stable path, action (`create`, `update`, `delete`, `no-op`), classification (`safe`, `risky`, `destructive`), reason code, member-impact summary, and `applyBlocked` flag;
- risky redirect origins degrade readiness until reviewed;
- unknown roles, groups, scopes, or feature mappings produce `admin-action-required` and deny by default;
- destructive removals produce `policy-blocked` and are blocked from apply in this dry-run-only slice;
- provider bodies, provider-internal IDs, credentials, credential URLs, private keys, tokens, and raw logs are never returned.

This endpoint is backend/control-plane only. Member clients consume provider-neutral readiness and capability policy; they must not call Keycloak/provider APIs directly or expose realm setup diagnostics.

## Guest and B2B users

Guests are external identities, not incomplete employees.

Guest records require:

- home issuer or external identity source;
- sponsor/owner;
- expiry/review date;
- allowed contexts/groups;
- accepted terms where required;
- data-sharing/risk note where relevant;
- deprovisioning behavior.

Microsoft-heavy organizations should use Entra B2B / External ID for Microsoft resource access where appropriate. Weave should not sync partner passwords or lifecycle unless explicitly delegated.

## Service principals and machine identities

Machine identities include connectors, migration jobs, bots, backend actors, and future Weaver runtimes.

Requirements:

- separate principal type from human users;
- named owner;
- least-privilege scopes;
- SecretRef-backed credentials;
- rotation policy;
- expiry/review date;
- audit of every sensitive operation;
- no shared unmanaged bot-user accounts as default.

## Deprovisioning

Immediate deprovisioning actions:

- mark user inactive through SCIM or authoritative source;
- revoke sessions/tokens where supported;
- remove or disable group/role/capability grants;
- block new provider operations;
- record audit event.

Deferred actions:

- transfer or archive content;
- preserve legal hold;
- reassign ownership;
- delete after retention window where policy allows.

Guardrails:

- never automatically remove the last owner/admin/break-glass path;
- treat suspended users differently from deleted users;
- reconcile orphaned accounts regularly;
- guest expiry must be enforced even if no upstream lifecycle signal exists.

## Provider notes

### Entra ID / Microsoft 365

- Good fit for existing Microsoft-heavy organizations.
- Use Entra for identity, groups, service principals, B2B/external users, and app consent.
- Use Microsoft Graph for users/groups/service principals where needed.
- Treat Teams/SharePoint/Planner as category providers behind Weave contracts, not member-facing product identity.

### Keycloak

- Good self-hosted identity broker/default.
- Supports OIDC/OAuth2, SAML, LDAP/AD federation, roles/groups, token mappers.
- SCIM may require extension or adjacent provisioning component; do not assume native universal SCIM support.

### Authentik/Auth0/Okta-style brokers

- Useful for OIDC/SAML federation and enterprise SSO.
- SCIM availability varies by product/plan/direction; model it as an adapter capability, not an assumption.

### Matrix/Synapse and Nextcloud

- Synapse supports SSO approaches such as OIDC/SAML/CAS depending on deployment; provisioning may require admin APIs/JIT/custom adapter.
- Nextcloud commonly supports LDAP and OIDC app/user-backend patterns; SCIM availability is deployment/app-specific.
- Both must stay behind Weave domain facades for normal member UX.

### Slack/OpenProject and other providers

- Slack SCIM often depends on plan/edition.
- OpenProject SCIM and OAuth/client modes depend on edition/configuration.
- Adapter capability manifests must capture these constraints before an admin chooses the provider.

## Acceptance criteria

Identity strategy is ready for implementation slicing when:

- the Admin Console can model auth protocol, provisioning protocol, source ownership, and deprovisioning policy separately;
- identity dry-run produces conflicts and mapping previews without mutation;
- effective policy can explain access and denial for a user/context/capability;
- guest and service-principal flows are distinct from member users;
- tests prove email is never treated as primary key;
- unknown groups/roles/providers fail closed;
- provider-specific IDs stay out of member responses and support bundles unless explicitly redacted and admin-only.
