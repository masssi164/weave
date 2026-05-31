# Provider replacement and anti-silo contract

Status: strategic contract for proving provider neutrality beyond wording.

## Principle

Provider neutrality is not proven by listing adapters. It is proven when an organization can choose, mix, replace, export from, and reconcile providers without changing member-facing Weave product concepts or losing governance visibility.

Weave must remain an operating layer, not a new lock-in silo.

## Anti-silo guarantees

Every provider-backed capability must define:

- canonical Weave objects;
- source-of-truth policy;
- provider mapping table ownership;
- provenance metadata;
- export/delete behavior;
- lossy-field notes;
- support-safe readiness;
- audit events;
- admin-visible risk notes;
- member-visible fallback states;
- replacement/migration path or explicit non-goal.

Stable Weave IDs are acceptable only with provenance, export, delete/deprovision, and reverse-mapping guarantees. Otherwise they become another lock-in mechanism.

## Capability/provider matrix

| Capability | Canonical objects | Dogfood/default provider examples | External/cloud provider examples | Required anti-silo evidence |
| --- | --- | --- | --- | --- |
| Identity/IDM | Organization, UserAccount, Person, Group, Role, IdentitySource, CapabilityPolicy | Keycloak, Authentik | Entra ID, Okta, Auth0, SAML/OIDC, LDAP/AD bridge | immutable IDs, SCIM/deprovisioning, group/role mapping, guest/service-principal model, effective-policy explanation |
| Chat | Space, Conversation, Message, Thread, Reaction, Attachment, Membership, Presence | Matrix/Synapse as the current real release provider path | Teams, Slack, Nextcloud Talk as contract-only/coming-later targets until adapter evidence promotes them | thread/message mapping, membership/retention source, attachment provenance, E2EE/retention caveats, export path |
| Files/docs | Drive, Node, Folder, File, Version, Share, Permission, Lock, EditSession | Nextcloud/WebDAV/WOPI-capable editor | SharePoint/OneDrive, S3, CMIS repository | permissions/share mapping, versioning, locks, external link risk, export/delete behavior |
| Calendar | Calendar, Event, Attendee, Recurrence, Availability, Resource | backend/shared calendar facade, CalDAV-compatible backing | Microsoft Graph calendars, Google/other enterprise calendars where supported | RRULE/time-zone fidelity, attendee semantics, resource booking, conflict/loss notes |
| Boards/tasks | Board, List, Task, Status, Assignee, Comment, Attachment, Dependency, CustomField | OpenProject | Planner, Jira-like providers | workflow/status mapping, custom fields, comments, dependencies, multi-assignee fidelity, lossy report |
| Meetings/calls | Meeting, Participant, Recording, Captions, MediaSession | LiveKit | Teams/Zoom/other future providers | token boundary, recording/caption provenance, consent, media retention, provider outage behavior |
| Decisions/evidence | Decision, SourceRef, Status, AuditRef | Weave-owned backend | imported decision records/future external sources | source citations, immutable audit refs, export, retention |
| Admin health/readiness | ProviderConfig, Readiness, RiskNote, SupportBundle, AuditEvent | Weave backend/infra probes | provider APIs/admin APIs | redaction plus actionable next action, no raw secrets/provider bodies |
| Weaver runtime | RuntimeProfile, ToolCapability, ApprovalReceipt, AuditEvent | disabled by default; later OpenClaw-derived runtime | future org-approved harnesses | user-rights/org-whitelisted tools, no default autonomous writes, per-user isolation, audit |


### Matrix Chat portability boundary

Matrix/Synapse is the v0.1 real-time Chat path. Weave maps rooms, timeline events, encrypted/unsupported message placeholders, send state, read markers, membership, and support-safe readiness into canonical Chat objects. Matrix remains the source of truth for message history, E2EE room keys, device trust, recovery, and server-side retention until a migration dry-run explicitly declares otherwise.

Non-Matrix chat providers are portability contracts, not current member chat implementations. Teams, Slack, and Nextcloud Talk may appear in admin/provider comparison and migration preflight language, but normal member Chat must not claim live non-Matrix send/read support until provider adapters, redacted error mapping, accessibility states, export/import boundaries, and release evidence are promoted.

## Provider capability manifest

Each adapter must publish a capability manifest before it is selectable in admin setup.

Required fields:

```json
{
  "providerKey": "microsoft-teams",
  "capability": "chat",
  "posture": "external_existing_provider",
  "auth": ["oidc", "oauth2-client-credentials"],
  "provisioning": ["graph-api", "scim", "jit"],
  "groups": "read",
  "guests": "native_external",
  "deprovisioning": "disable_and_reconcile",
  "audit": "pull_or_webhook",
  "supportsDryRun": true,
  "supportsRollback": "partial",
  "lossyFields": ["provider-specific rich cards"],
  "riskNotes": ["external data residency depends on tenant configuration"]
}
```

Manifests are admin/operator contracts. Member clients consume only Weave capability states and domain objects.

## Provider replacement workflow

### 1. Preflight

Admin/operator selects source and target providers for one capability category.

Preflight checks:

- source and target readiness;
- identity and group mapping availability;
- required OAuth/app consent;
- SecretRef presence and rotation state;
- rate limits and API scope sufficiency;
- retention/legal hold constraints;
- data residency/compliance differences;
- downtime/window estimate;
- rollback feasibility;
- dry-run availability.

No data is mutated during preflight.

The backend exposes this as an Admin Console contract at `POST /api/admin/providers/replacements/dry-run`. The request carries `category`, `currentAdapter`, `targetAdapter`, `choiceModel`, `secretRef`, and a source-of-truth declaration. The response is support-safe: it reports canonical objects, lossy mapping risks, export/delete/deprovision expectations, readiness before activation, cutover gates, and audit references without returning raw secrets, tenant URLs, bearer tokens, or downstream provider bodies.

### 2. Dry-run mapping

Dry-run maps source provider objects to canonical Weave objects and target-provider capabilities.

Dry-run outputs:

- count of mapped objects;
- unmapped/conflicting objects;
- lossy fields;
- identity/group mismatches;
- permissions that cannot be faithfully represented;
- external sharing risks;
- estimated user-visible impact;
- audit preview;
- support-safe report path.

Conflicts must be actionable. "Some objects failed" is not acceptable as the only admin signal.

### 3. Admin decision

Admin chooses one of:

- cancel;
- adjust mapping and rerun dry-run;
- accept read-only coexistence;
- apply migration/switch where supported;
- keep current provider and record risk/non-goal.

High-risk changes require explicit owner/security-admin approval where policy says so. Operators alone should not be able to change identity providers, retention, or policy unless delegated.

### 4. Apply, if supported

Apply may be unavailable for many providers. When available, it must:

- use service principals/migration identities with least privilege;
- emit audit events;
- preserve provider mapping history;
- never leak raw provider payloads to member clients;
- be resumable or safely abortable;
- have rollback/restore notes.

### 5. Post-reconcile

After apply or coexistence activation, Weave reconciles:

- object counts;
- permissions;
- group memberships;
- links/attachments;
- lossy-field acknowledgements;
- member capability states;
- support-safe readiness;
- audit completeness.

## Mixed-provider deployments

Mixed deployments are not edge cases. They are a core design target.

Examples:

- Entra ID + Teams chat + SharePoint files + OpenProject tasks + Weave admin/readiness.
- Keycloak + Matrix chat + SharePoint files + Planner-like tasks.
- LDAP/AD via Authentik + Slack chat + Nextcloud files + LiveKit meetings.
- New organization starts with self-hosted defaults, later moves files to SharePoint while keeping Matrix chat and OpenProject tasks.

Member UX must stay Weave-owned across those deployments. Provider names may appear only where they matter for admin/operator decisions, risk notes, support, or external launch context.

## Source-of-truth policy

Each category must declare source-of-truth per object and field class.

Examples:

- identity lifecycle: Entra ID or Keycloak;
- chat message history: Teams, Slack, or Matrix source provider;
- file binary storage: SharePoint, Nextcloud, or object storage;
- task workflow status: OpenProject/Jira/Planner-like provider or Weave-owned task service;
- decisions: Weave-owned canonical record with external source references;
- readiness: Weave backend/admin control plane.

Weave may cache and map, but it must not silently become the source of truth for provider-owned data.

## Loss report schema

Every lossy mapping or replacement dry-run should produce a support-safe report like:

```json
{
  "reportId": "loss-report-id",
  "capability": "boards/tasks",
  "sourceProvider": "openproject",
  "targetProvider": "planner-like",
  "summary": {
    "objectsMapped": 120,
    "objectsBlocked": 4,
    "lossyFields": 18
  },
  "losses": [
    {
      "objectKind": "Task",
      "field": "customField.priorityMatrix",
      "severity": "medium",
      "memberImpact": "visible_as_note_only",
      "adminAction": "accept_loss_or_keep_source_provider"
    }
  ],
  "conflicts": [
    {
      "kind": "assignee",
      "reason": "external_identity_not_mapped",
      "resolution": "map_guest_or_remove_assignment"
    }
  ],
  "auditRefs": ["audit-event-id"]
}
```

## Support-safe diagnostics

Redaction must not make diagnostics useless. Admin/operator views should show:

- provider category;
- selected provider key;
- readiness state;
- affected capability;
- redacted mapping ID;
- stale group/provider/object counts;
- next operator action;
- support bundle reference;
- audit references.

They must not show:

- secrets;
- bearer tokens;
- credential-bearing URLs;
- cookies;
- private keys;
- raw downstream error bodies;
- provider-internal IDs in member responses;
- unredacted private content.

## What not to build before this is accepted

- connector marketplace or public SDK;
- broad Teams/Slack migration tooling;
- Office/ONLYOFFICE-first feature work;
- autonomous Weaver writes;
- generic provider marketplace UI;
- raw provider admin inside the member client;
- preview/scaffold/coming-soon member surfaces.

## Acceptance criteria

This contract is ready for implementation slicing when:

- at least the self-hosted, Microsoft-heavy, and hybrid topologies are documented end to end;
- every provider-backed category has an anti-silo row with source-of-truth, export/delete, loss, risk, and replacement notes;
- dry-run replacement shape is specified for at least Chat and Boards/Tasks;
- unknown or stale mappings fail closed or degrade safely;
- admin/operator diagnostics are both redacted and actionable;
- member surfaces remain provider-neutral across mixed deployments;
- acceptance tests or scenario mappings prevent provider setup/leakage from returning to member UX.
