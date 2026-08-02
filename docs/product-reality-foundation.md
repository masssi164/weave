# Product reality foundation

Status: Sprint 21 foundation artifact.

Massimo's correction is now the governing release posture for the next plan: Weave must prove provider-neutral domains with free/self-hosted providers before human release validation or commercial adapter work. The release truth is evidence-first:

> No statement is release-capable unless proven by E2E/runtime/migration/rollback evidence.

## Immediate claim posture

Allowed wording:

- Weave builds provider-neutral collaboration domains with free self-hosted providers while Keycloak remains the fixed platform identity authority.

Forbidden until named evidence exists for the exact domain, provider pair, version, fixture, and runtime target:

- Forbidden claim: Providers are interchangeable.
- Forbidden claim: Weaver is available.
- Forbidden claim: A PA runs per user.
- Forbidden claim: History remains fully preserved.
- Forbidden claim: Rollback works in production.
- Forbidden claim: v0.1 is release-ready.

No external promise, public release claim, commercial-adapter promise, or human-validation sprint may bypass this file, the claim matrix, and the automated release/reality checks.

## Reality levels

Every domain/provider record must expose exactly one `realityLevel`:

| Level | Meaning | Release claim boundary |
| --- | --- | --- |
| `contract_only` | Contract, manifest, fixture, or architecture only. | Architecture/planning claim only. |
| `configured` | Reproducible configuration exists and is validated support-safely. | Operator setup claim only. |
| `live_read` | Live provider read path is proven with redacted evidence. | Read capability claim for the named provider only. |
| `live_write` | Live provider write path is proven with redacted evidence. | Write capability claim for the named provider only. |
| `migration_dry_run` | Export/map/consequence/lossy/conflict evidence exists without mutation. | Review-only migration claim. |
| `migration_apply_ready` | Apply path is implemented and validated in non-production fixture/lab evidence. | Apply-ready in the named lab only; not customer-ready. |
| `rollback_ready` | Rollback proof generated receipts and post-rollback validation. | Rollback claim for the named fixture/lab only. |
| `release_ready` | E2E/runtime/migration/rollback/support/restore evidence is complete and current. | Customer/release claim for the named scope only. |

Only `release_ready` may be described as customer-ready. All lower levels must use narrow, evidence-scoped language.

## Free provider lab first

Commercial adapters are preparation-only until the free provider lab proves the domains. Start with:

| Domain | First providers | Required proof path |
| --- | --- | --- |
| Chat | Matrix/Synapse and Zulip | Matrix live -> export -> canonical mapping -> Zulip import/apply -> stable Weave UI -> rollback receipt. |
| Files | Nextcloud and MinIO/S3 | WebDAV/S3 boundary, versions/shares/permissions/lossy evidence. |
| Calendar | Nextcloud CalDAV and Radicale | Event/recurrence/attendee/resource mapping and rollback limits. |
| Boards | OpenProject and a second OSS provider later | Board/task workflow mapping and lossy fields. |
| Agent Runtime Control | OpenClaw/Weaver runtime provider | Entitled disposable-cell lifecycle, RuntimeProfile v2 signing, external encrypted state, workload identity, audit, revoke, delete, and kill/recreate proof. |

Platform identity is not a provider-lab adapter category. Keycloak is fixed as the platform authority. Authentik, Entra, Auth0, other OIDC/SAML sources, and LDAP/AD may be exercised as upstream Keycloak federation or brokering fixtures only.

Commercial adapter readiness for Teams and Slack is allowed only as research/spec readiness: auth model, API rights, rate limits, history export, attachments, user/guest/thread mapping, retention, E2EE/compliance limits, costs, admin consent, rollback capability.

## Canonical domain object targets

Sprint 21 must align docs, contracts, and manifests to these object names before new apply claims:

- Chat: `WeaveSpace`, `WeaveConversation`, `WeaveMessage`, `WeaveThread`, `WeaveReaction`, `WeaveAttachment`, `WeaveMembership`, `WeaveHistoryPolicy`, `ProviderRef`, `MigrationReceipt`, `RollbackReceipt`, `LossyFieldReport`.
- Files: `WeaveDrive`, `WeaveFolder`, `WeaveFile`, `WeaveVersion`, `WeaveShare`, `WeavePermission`, `WeaveLock`, `WeaveQuota`, `ProviderRef`.
- Calendar: `WeaveCalendar`, `WeaveEvent`, `WeaveRecurrence`, `WeaveAttendee`, `WeaveResource`, `WeaveAvailability`, `ProviderRef`.
- Agent Runtime Control: `RuntimeEntitlementRef`, `RuntimeProfile`, `ApprovalChallenge`,
  `RuntimeCell`, `WorkspaceRevision`, `RuntimeRevocation`, `RuntimeAuditCorrelation`.

## Provider manifest shape

Every provider manifest must be support-safe and include `providerId`, `domain`, `realityLevel`, `supports`, `history`, `risks`, `secrets`, and `supportEvidence`.

Example shape:

```yaml
providerId: matrix-synapse
domain: chat
realityLevel: live_write
supports:
  readHistory: true
  writeMessage: true
  attachments: true
  reactions: true
  threads: partial
  rollback: partial
history:
  exportSupported: true
  importSupported: partial
  e2eeHistory: blocked_or_archive_only
risks:
  - lossy_threads
  - e2ee_history_not_decryptable
secrets:
  storedIn: credential_broker
supportEvidence:
  redacted: true
```

## Sprint sequence

| Sprint | Name | Exit gate |
| --- | --- | --- |
| 21 | Product Reality Foundation | Reality levels, manifests, canonical domains, claim gates checked in. |
| 22 | Free Provider Lab | Fixed Keycloak authority and upstream Authentik federation fixture, plus Matrix, Zulip, Nextcloud, Radicale, MinIO, and OpenProject, reproducibly start with support-safe evidence. |
| 23 | Chat Provider Switch | Matrix <-> Zulip migration, history classification, audit, and rollback receipt. |
| 24 | Weaver Runtime Factory | Admin enablement -> user opt-in -> per-user container -> health -> audit -> revoke proof. |
| 25 | Weaver Customization | Admin policy and user personalization versioned through RuntimeProfile profile hashes and rollback options. |
| 26 | Operator Recovery | Backup -> destroy -> restore -> validate, with RestoreReceipt, BackupManifest, redacted support bundle. |
| 27 | Cross-Domain Provider Proof | Calendar and Files provider-pair proof plus a separate fixed-Keycloak federation-readiness proof. |
| 28 | Commercial Adapter Preparation | Teams/Slack readiness specs only; no implementation push. |
| 29 | Human-in-the-Loop Release Validation | Human AT/UX/admin/Weaver validation only after automated gates are green. |

## Autonomous execution workstreams

1. **Foundation/claim gate**: tighten forbidden-claim scans; wire reality-level JSON checks into release evidence.
2. **Manifest/schema**: add provider manifest schema and seed free-provider manifests at truthful levels.
3. **Chat switch proof**: build Matrix/Zulip fixture generator, mapping, lossy report, import/apply, rollback receipt, and UI-stability evidence.
4. **Agent Runtime Control**: implement entitlement/profile/cell reconciliation, external encrypted
   state, WebDAV manifest materialization, workload identity, audit, and health ports; prove
   cross-cell isolation and kill/recreate without durable cell bytes.
5. **Operator recovery**: upgrade backup/restore/support-bundle artifacts from offline/static to rehearsed evidence.
6. **Commercial readiness**: maintain Teams/Slack readiness research only after Sprints 21-24 gates are protected.

## Evidence blockers

CI/release checks must block on scoreboard/report mismatch, open release blocker, missing Live-E2E, missing rollback receipt, missing history report, missing Weaver runtime proof, missing restore proof, unsafe support bundle, or wrong public claim.
