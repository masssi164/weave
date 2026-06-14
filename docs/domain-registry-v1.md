# Canonical domain registry v1

The machine-readable implementation conformance fixture for Sprint 8 domain vocabulary is `specs/0004-domain-registry/canonical-domain-registry-v1.json`. The pinned Weave Specification Corpus remains canonical product/domain truth. The backend carries an identical runtime resource at `server/src/main/resources/canonical-domain-registry-v1.json`; `./gradlew domainRegistryCheck` fails if the implementation copies drift.

The registry defines the provider-neutral Weave domain keys, stable member states, admin/operator readiness states, provider reality levels, source-of-truth modes, compatibility aliases for older provider-category names, and the required Provider Adapter Manifest fields.

## Stable member states

Member-facing code must use only these registry states:

- `available`
- `disabled_by_policy`
- `not_configured`
- `degraded`
- `unavailable`
- `coming_later`

Members must not receive provider setup controls, raw provider diagnostics, secrets, or migration reports. Admin/operator surfaces translate these member states into setup and readiness actions.

## Stable admin states

Admin-facing code must use only these readiness states:

- `provider_not_configured`
- `secret_missing`
- `ready`
- `degraded`
- `dry_run_required`
- `lossy_mapping_pending`
- `apply_blocked`
- `migration_ready`

Unknown provider states fail closed into a support-safe admin state and a stable member impact state.

## Provider reality levels

Every provider candidate has a stable `providerRealityLevel` in the canonical registry:

- `contract_only`
- `configured`
- `live_read`
- `live_write`
- `migration_dry_run`
- `migration_apply_ready`
- `rollback_ready`
- `release_ready`

Member capability states are derived from policy, readiness, admin selection, and reality level. A `contract_only` provider must never produce member state `available`; Admin Console and support bundles must instead show the remediation needed to promote the candidate without exposing URLs, tenant IDs, credentials, provider-internal IDs, or raw downstream bodies.

## Adapter declarations

A provider adapter declares supported domains by publishing a Provider Adapter Manifest with the fields listed in the registry-level `adapterManifestRequirements` array:

- `adapterKey`
- `domainKeys`
- `apiProfile`
- `canonicalObjects`
- `capabilityKeys`
- `readinessChecks`
- `unsupportedFields`
- `migrationLimits`
- `auditEvents`
- `secretBoundary`

The adapter's `domainKeys` must reference canonical domain keys, not provider names. Existing provider-category names such as `identity-idm`, `files-docs`, `documents-collaboration`, `boards-tasks`, `meetings-calls`, `decisions-evidence`, `admin-control-plane`, and `release-evidence` are compatibility aliases only. Slash-style labels used in member copy or acceptance language, for example `boards/tasks` or `meetings/calls`, are display text; they must map to canonical keys or registry aliases before they enter machine-readable contracts.

## Portability evidence

Every domain entry names the required portability evidence classes. Provider replacement and migration language must use **no unaccounted data loss**: unsupported fields, lossy mappings, conflicts, rollback limits, and retention boundaries are reported and approved before any guarded apply path is enabled.

## Canonical-domain adapter status registry

This section is the human-readable adapter posture companion for issues #708, #710, and #751. It summarizes adapter/provider implementation state per canonical domain. The machine-readable registry remains authoritative for domain keys and provider reality levels; this table is the public-safe index for admins, operators, and docs reviewers.

| Domain | Adapter/provider implementation | Sovereignty/data-sovereignty posture | Implementation state | Provider/jurisdiction posture | Evidence/readiness link | Caveats | Migration/replacement path |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `identity` | Keycloak realm; generic OIDC | Self-hosted/default path records operator-controlled hosting and SecretRef boundaries. | `release_ready` for current auth path; other candidates vary. | Commercial candidates such as Entra ID/Auth0 require tenant-specific exposure notes before promotion. | `tools/domain_registry_check.py`, server resource copy, Admin Health readiness. | Email rename, subject collision, guest/service-principal lifecycle, last-admin lockout. | OIDC/SAML/SCIM/LDAP mapping with dry-run group/role reconciliation before apply. |
| `people` | Weave person directory plus SCIM/OIDC/LDAP candidates | Person data posture follows selected identity/directory adapter and Weave directory retention policy. | Registry-backed contract; implementation promotion is adapter-specific. | HRIS/directory imports may add external processors and retention constraints. | Domain registry fixture and people capability tests when implemented. | Duplicate contacts, stale profiles, guest visibility, identity/person merge. | Export/import people records with ProviderRef and conflict reports. |
| `spaces` | Weave-owned Space manifest with provider bindings | Weave-owned context anchor; bound provider containers retain their own posture. | Contract foundation. | Exposure is composite across bound chat/files/boards/calendar/calls adapters. | Space anchor contract and acceptance mappings. | Split ownership, partial provisioning, renamed upstream containers. | Rebind domain adapters per Space with mapping history and loss reports. |
| `chat` | Matrix/Synapse current path; Slack/Teams/Nextcloud Talk candidates | Current self-hosted Matrix posture depends on operator hosting and E2EE/retention configuration. | Matrix path is current dogfood implementation; non-Matrix candidates are lower-level evidence until promoted. | Slack/Teams expose commercial tenant/provider posture and require adapter-specific evidence. | Chat provider switch contract, Matrix migration proof boundary, claim gates. | Thread semantics, E2EE key recovery, attachment retention, cards/mentions. | Dry-run maps conversations/messages/membership/attachments before any provider switch. |
| `files` | Nextcloud/WebDAV current path; SharePoint/OneDrive, S3, SMB candidates | Current self-hosted file posture is adapter-specific; object storage or commercial files add provider exposure. | Current dogfood path plus guarded candidates. | SharePoint/OneDrive and managed S3 require tenant/region/subprocessor notes. | Provider replacement contract and Admin Health readiness. | Permissions, public links, versions, locks, quota, checksum mismatches. | Export/delete plus lossy permission/version reports before replacement. |
| `documents` | WOPI-style editor seam; Collabora/OnlyOffice-style candidates | Document posture follows storage adapter plus editor provider/session boundary. | Contract/candidate posture unless adapter evidence promotes. | External web editors add processing and launch-token exposure. | Architecture/provider portability and documents contract evidence. | Comments, suggestions, native formats, conversion loss, unsafe launch. | Keep storage canonical, replace editor through guarded launch contract and conversion reports. |
| `calendar` | Backend/shared facade; CalDAV/Graph/Google candidates | Current facade posture follows selected calendar backing provider. | Contract plus dogfood facade evidence. | Graph/Google calendars require tenant/region/risk notes. | Acceptance contract and domain registry gates. | RRULE/time zones, attendees, private events, resource booking. | Dry-run recurrence/attendee/resource fidelity before provider change. |
| `boards` | OpenProject-style adapter; Jira/Planner/Deck/Vikunja candidates | Current self-hosted OpenProject posture depends on operator deployment. | OpenProject readiness evidence exists where gates reference it; candidates are guarded. | Jira/Planner add commercial provider exposure and API limits. | Provider replacement contract and boards/task domain evidence. | Custom fields, workflow transitions, multi-assignee semantics, comments. | LossyFieldReport and workflow/status mapping before migration apply. |
| `calls` | LiveKit token/readiness facade; Jitsi/Zoom/Teams/Meet candidates | Current LiveKit posture follows operator hosting and media/recording settings. | Current dogfood readiness scoped to LiveKit. | External meeting systems add media, recording, transcript, and tenant exposure. | README claim matrix and meeting architecture docs. | E2EE boundary, consent, retention, captions, provider outages. | Replace through join-grant/readiness facade with consent/recording reports. |
| `decisions` | Weave-owned decision ledger plus external references | Weave-owned canonical record; linked systems retain their own exposure. | Contract foundation. | External issue/doc references require link and permission caveats. | Domain registry and audit/evidence docs. | Legal retention, stale links, conflicting decision sources. | Export decisions with immutable audit refs and source citations. |
| `notifications` | Weave notification facade with email/push/chat/webhook transports | Delivery posture follows selected transport adapter. | Contract foundation. | External email/push/webhook vendors add delivery metadata exposure. | Domain registry and future notification tests. | Duplicate delivery, unsubscribe semantics, cross-provider identity. | Switch transports with delivery-attempt audit and preference export. |
| `health` | Weave backend/admin readiness and support bundles | Support-safe evidence only; raw provider details stay operator-only. | Ready foundation for current diagnostics boundaries. | Composite posture across configured adapters. | Docs checks, support bundle redaction checks, release evidence. | False green states, secret leakage risk, missing restore evidence. | Adapter readiness cards point to next safe action and evidence refs. |
| `weaver` | Governed OpenClaw-derived runtime/harness adapter candidate | Per-user runtime posture requires isolation, signed profiles, SecretRefs, audit, and approval evidence. | Guarded/future; disabled by default. | Model/runtime/tool providers require adapter-specific posture before enablement. | Governed Weaver runtime security contract and OpenClaw approval/harness fixture evidence. | Over-broad tools, private memory, cross-user bleed, unaudited actions. | RuntimeProfile vNext preserves the stable Weave channel while domain tools/adapters change behind Weave facades. |

Adapter names in this table are public-safe candidates and current implementation paths. They do not by themselves prove interchangeability, a universal compliance shield, or release readiness.
