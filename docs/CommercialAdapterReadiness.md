# Commercial Adapter Readiness

Status: Sprint 28 readiness/specification evidence only. This document does not approve or implement Slack or Microsoft Teams adapters.

Governing spec corpus references:

- `domains/chat/spec.md` at corpus commit `24c746c674da7d98e5c6abc1f1abac033a8774f2`: Chat is provider-neutral; Slack and Microsoft Teams are candidate providers behind Weave-owned contracts.
- `platform/identity-security/spec.md`: Keycloak is the platform identity
  authority; groups, guests, service principals, federation/brokering, and
  deprovisioning stay behind that platform boundary.
- `domains/admin-health-ops/spec.md`: provider readiness, diagnostics, backup/restore, support bundles, and operator evidence are admin/operator-owned.
- `steering/provider-portability-principles.md`: unsupported, lossy, vendor-locked, archived, retained, and manual-review states must be explicit.

Readiness decision terms:

- `blocked`: implementation must not start; missing proof or approval is release-blocking.
- `manual_review`: a human operator/release owner must verify provider terms, tenant policy, or legal/compliance posture before implementation planning.
- `contract_ready`: provider contract work can be proposed, but adapter implementation is still blocked until every implementation-start prerequisite is met.
- `implementation_allowed`: future state only; requires signed readiness approval, provider-specific proof fixtures, acceptance mappings, and claim-gate update.

## Microsoft Teams readiness section

Sprint 28 outcome: Teams is documented as a commercial Chat candidate only. The current go/no-go decision is `blocked` for implementation.

| Readiness area | Required proof before implementation | Sprint 28 state | Blocker / risk |
| --- | --- | --- | --- |
| Auth model | Tenant-admin consent model, OAuth/OIDC flow, delegated vs application permissions, service principal lifecycle, token refresh, SecretRef handling, and break-glass ownership. | `manual_review` | Entra tenant policy and Graph permission scope must be reviewed before any adapter code. |
| API rights | Named Microsoft Graph permissions for channels, chats, messages, replies, users, groups, teams, guests, files, and audit export with least-privilege justification. | `blocked` | No checked-in tenant permission proof or admin-consent receipt exists. |
| Rate limits | Provider throttle classes, retry/backoff policy, quota impact on migration dry-runs, and safe degradation behavior. | `blocked` | No live or fixture-backed throttle evidence exists. |
| History export | Supported export surfaces for teams, channels, private/shared channels, chats, replies, reactions, edits/deletes, and timestamps. | `blocked` | Private/shared channel and chat export semantics can be lossy or license-dependent. |
| Attachment export | SharePoint/OneDrive-backed file mapping, permission inheritance, version history, legal hold, and deleted-file behavior. | `blocked` | Attachment provenance crosses Chat and Files contracts; no proof fixture exists. |
| User and guest mapping | Entra user/group/guest/service-principal mapping to Weave identity, membership, deprovisioning, and external-guest states. | `manual_review` | Guest identity and deprovisioning policy must be explicit. |
| Thread mapping | Teams channel posts/replies, chat messages, meetings, reactions, pins, mentions, and system events mapped to canonical `WeaveConversation`, `WeaveThread`, and `WeaveMessage` objects. | `blocked` | Canonical lossy-field report has not been produced for Teams. |
| Retention | Retention labels, legal hold, eDiscovery/export limits, deleted message visibility, and tenant policy constraints. | `manual_review` | Retention/legal policy cannot be inferred by Weave. |
| E2EE/compliance limits | E2EE, sensitivity labels, DLP, audit, tenant boundary, and compliance-export limitations surfaced as support-safe readiness findings. | `manual_review` | Compliance posture is tenant- and license-dependent. |
| Costs | Required Microsoft 365/Graph/export/eDiscovery licensing, throttling impact, and operator cost of migration evidence. | `manual_review` | Cost model is not approved. |
| Admin consent | Explicit tenant-admin consent receipt, revocation plan, approval actor, scope, and date. | `blocked` | No consent receipt exists and none is requested by this sprint. |
| Rollback capability | Reversible changes, dry-run/apply boundary, rollback receipts, archive-only cases, and no-unaccounted-data-loss report. | `blocked` | No rollback proof or archive-only classification exists. |

Teams readiness exit criteria before any future implementation branch:

1. A Teams-specific provider contract fixture names Graph permissions, lossy-field classes, support-safe diagnostics, and tenant-admin consent evidence.
2. `release/commercial-adapter-readiness/go-no-go-matrix.json` changes Teams from `blocked` only after approval evidence is checked in.
3. The commercial implementation guard is updated with the approved proof reference before any adapter source files are added.
4. Release notes continue to avoid Teams availability, production migration, or customer-ready wording until `release_ready` evidence exists.

## Slack readiness section

Sprint 28 outcome: Slack is documented as a commercial Chat candidate only. The current go/no-go decision is `blocked` for implementation.

| Readiness area | Required proof before implementation | Sprint 28 state | Blocker / risk |
| --- | --- | --- | --- |
| Auth model | Workspace/org install model, OAuth scopes, bot vs user token boundary, app approval/revocation, token rotation, SecretRef handling, and enterprise-grid ownership. | `manual_review` | Workspace and enterprise-grid install policy must be reviewed before adapter code. |
| API rights | Named Slack scopes for conversations, history, replies, users, usergroups, files, reactions, pins, canvases/bookmarks where applicable, and audit/export APIs. | `blocked` | No approved scope set or app-install receipt exists. |
| Rate limits | Slack Web/API tier behavior, backoff rules, pagination, migration dry-run budget, and degradation behavior. | `blocked` | No fixture-backed rate-limit evidence exists. |
| History export | Supported export classes for public channels, private channels, DMs, group DMs, threads, edits/deletes, reactions, pins, huddles references, and enterprise discovery. | `blocked` | Private channel/DM export is plan- and permission-dependent. |
| Attachment export | Slack file export, remote file references, external links, previews, retention/deleted files, and Weave Files provenance mapping. | `blocked` | Attachment mapping can be lossy and crosses Chat/Files contracts. |
| User and guest mapping | Slack users, multi-channel guests, single-channel guests, deleted users, bots, and usergroups mapped to Weave identity/membership/deprovisioning states. | `manual_review` | Guest and bot semantics need explicit canonical mapping. |
| Thread mapping | Channels, DMs, group DMs, thread replies, broadcasts, reactions, mentions, pins, reminders, workflow/system messages mapped to canonical Chat objects. | `blocked` | Canonical lossy-field report has not been produced for Slack. |
| Retention | Workspace retention policy, legal hold/discovery limits, deleted/edited message visibility, and export plan constraints. | `manual_review` | Retention/export behavior depends on workspace plan and admin policy. |
| E2EE/compliance limits | Encryption posture, Enterprise Key Management, audit logs, DLP/discovery, data residency, and compliance-export limitations. | `manual_review` | Compliance posture is plan- and tenant-dependent. |
| Costs | Required Slack plan, Discovery/Audit APIs, export tooling, admin time, and dry-run evidence cost. | `manual_review` | Cost model is not approved. |
| Admin consent | Workspace/org app approval receipt, scopes, approval actor, revocation path, and date. | `blocked` | No app approval receipt exists and none is requested by this sprint. |
| Rollback capability | Reversible writes, non-mutating dry-run first, rollback receipts, archive-only classes, and no-unaccounted-data-loss report. | `blocked` | No rollback proof or archive-only classification exists. |

Slack readiness exit criteria before any future implementation branch:

1. A Slack-specific provider contract fixture names scopes, lossy-field classes, support-safe diagnostics, and workspace/org app approval evidence.
2. `release/commercial-adapter-readiness/go-no-go-matrix.json` changes Slack from `blocked` only after approval evidence is checked in.
3. The commercial implementation guard is updated with the approved proof reference before any adapter source files are added.
4. Release notes continue to avoid Slack availability, production migration, or customer-ready wording until `release_ready` evidence exists.

## Commercial go/no-go summary

The executable source of truth for Sprint 28 is `release/commercial-adapter-readiness/go-no-go-matrix.json`.

| Provider | Current decision | Implementation start allowed? | Why |
| --- | --- | --- | --- |
| Microsoft Teams | `blocked` | No | Tenant-admin consent, Graph permission proof, lossy-field report, rate-limit evidence, export/retention proof, costs, and rollback proof are missing. |
| Slack | `blocked` | No | App approval, scope proof, lossy-field report, rate-limit evidence, export/retention proof, costs, and rollback proof are missing. |

Unsupported claims remain blocked:

- Do not claim Slack or Teams integration is implemented, available, customer-ready, production-ready, migration-ready, or rollback-ready.
- Do not add Slack or Teams adapter implementation files before a future approved readiness update changes the guard from `blocked` to `implementation_allowed`.
- Do not expose raw Slack or Teams configuration to normal members; any future provider setup belongs to Admin/Workspace Health.
