# Weaver security, privacy, accessibility, and support-safe evidence report

Status: Sprint 8/9 contract evidence seed for issues #446-#449.

## Security

| Requirement | Evidence |
| --- | --- |
| Provider switching threat covered | Provider switching remains admin/operator-only and approval-gated; see `docs/admin-suite-readiness-setup-contract.md` and `@weave-v01-provider-switch-portability`. |
| OpenClaw runtime isolation covered | `docs/governed-weaver-runtime-security-contract.md` defines per-user workspace, memory, session, and no-cross-user isolation. |
| Weaver tools and approvals covered | `WeaverToolRegistryTest` proves grant-filtered discovery, blocked unauthorized calls, approval-required writes, redaction, and audit. |
| No raw provider tokens | Runtime profile exposes `secretrefs-only-no-raw-provider-tokens`; tool results redact raw provider payloads. |
| SecretRefs everywhere | Product contract requires SecretRefs only for runtime profile, support bundles, logs, docs, and release evidence. |
| RBAC control-plane actions | Runtime profile generation intersects IDM/RBAC capability policy with admin allowlists and remains disabled by default. |

## Privacy

| Requirement | Evidence |
| --- | --- |
| Weaver memory policy documented | Governed contract records per-user memory store and admin metadata-only visibility. |
| Member private memory hidden from admins by default | Admins see policy/audit metadata only unless an explicit audited support authorization exists. |
| Export/delete expectations | Memory export/delete follows member rights and domain export/delete policies; raw memory is excluded from support bundles by default. |
| Support bundle redaction | Release evidence records only image digest, SBOM/scan refs, policy version, and support-safe approval proof. |

## Accessibility

| Surface | Evidence expectation |
| --- | --- |
| Admin setup path | Admin Console remains keyboard reachable with headings, forms, and status text. |
| Provider switching/report review | Existing provider switch portability scenario requires support-safe risks, conflicts, and recovery actions before irreversible action. |
| Calls controls | Existing accessibility release gate covers join/leave/mute/camera/error states. |
| Weaver approval flow | Approval requests must expose action, scope, risk, expiry, approve/deny controls, and receipt status to screen readers without color-only state. |
| Member capability states | Member surfaces use the stable capability vocabulary only. |

## Support-safe release evidence bundle contents

Before release readiness is claimed, the bundle must include:

- canonical domain registry version;
- migration/provider-switch contract version;
- Keycloak dry-run sample reference;
- Calls/LiveKit readiness artifact reference;
- OpenClaw-derived fork URL, pinned upstream commit/tag, image digest, SBOM ref, and scan refs;
- Weaver tool approval proof showing policy version, receipt ref, audit ref, and redacted tool/result payload.

Current PR evidence:

- `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`
- `server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java`
- `docs/governed-weaver-runtime-security-contract.md`
- `e2e/features/v0_1_dogfood_release.feature`
- `e2e/scenario_mappings.json`
