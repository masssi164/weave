# Sprint 9 product-readiness waterfall evidence

Status: support-safe acceptance and release-readiness contract for `#436`, `#449`, and `#450`.

Sprint 9 is not a marketing sprint. It is a product-readiness waterfall: governance, architecture, control plane, domain implementation, Weaver policy, and release readiness must each leave reviewable evidence before any product-ready claim is made.

## Governance and board evidence

- Milestone: [`Sprint 9 — Product Readiness Waterfall`](https://github.com/masssi164/weave/milestone/9).
- Board: [Weave Delivery Board](https://github.com/users/masssi164/projects/2/views/1).
- Required phases: Governance, Architecture, Control Plane, Domain Implementation, Weaver, Release Readiness.
- Required issue labels: `track:sprint-9`, one `phase:*` label, priority, type, and `release-blocker` for any product-readiness blocker.
- Done rule: no issue moves to Done without acceptance criteria, evidence, and a claim decision.
- README and marketing updates stay blocked until the linked evidence exists.
- Provider apply stays blocked until dry-run, reports, RBAC, audit, rollback, and redaction evidence exist.
- Weaver runtime execution stays blocked until OpenClaw fork image digest/SBOM/scan refs, isolation, tool grants, member opt-in, approval policy, and audit evidence exist.

## Product-ready definition

A Weave capability is product-ready only when all of these are true:

1. The member-facing surface uses Weave-owned domain language and stable states only.
2. Backend policy evaluates capability and role/group grants before provider access.
3. Provider readiness, migration, conflict, rollback, and lossy mapping reports are admin/operator-only and support-safe.
4. Accessibility evidence covers keyboard and screen-reader paths for setup, provider switching, calls, Weaver approvals, and member capability states.
5. Security and privacy evidence proves no raw provider tokens, SecretRef contents, raw provider errors, provider URLs with credentials, private member memory, or personal identifiers are emitted in client, admin console, runtime profile, logs, support bundles, screenshots, docs, or release artifacts.
6. Release evidence points to executable tests, scenario mappings, or explicit release-owner waivers rather than screenshots alone.

## Vertical scenario evidence bundle

The `@weave-product-readiness-waterfall` scenario maps to this support-safe bundle:

| Required evidence | Source-backed pointer | Claim decision |
| --- | --- | --- |
| Domain registry version | `canonical-domain-facade-v1` from `CanonicalDomainDefinition` and `CanonicalDomainFacadeServicesTest` | Registry read is proven through backend domain-facade contracts for files/documents, calendar/meetings, boards/tasks, and identity/admin policy. |
| Migration contract version | `provider-switch-portability-v1` release evidence name covering preflight, portable export/import, cutover, rollback, and recovery | Apply remains a guarded decision; full automated cross-provider migration is not claimed. |
| Keycloak dry-run sample | `AdminControlPlaneControllerTest.adminRealmDryRunIsBackendOwnedDeterministicAndSupportSafe` and `KeycloakRealmDryRunProviderTest.plansRealmImportWithoutEnablingDestructiveApply` | Desired-state dry-run is deterministic, support-safe, and does not enable destructive apply. |
| Provider apply blocked state | `AdminControlPlaneControllerTest.operatorAndMemberCannotApplyIdentityRealmWhenPolicyForbidsProviderConfiguration` | Provider apply requires an authorized owner/admin role and is decision-only in this slice. |
| Boards/OpenProject portability report | `DomainAdapterRegistryMapperTest.coreProductDomainsCarryExecutableAdapterFitContracts` and the Boards portability fragments in `ProviderCapabilityContracts` | Boards portability is dry-run/report evidence, not live data movement. |
| Calls/LiveKit readiness artifact | `docs/meeting-architecture-decision.md` and `docs/roadmap-and-guarded-surfaces.md` | LiveKit remains the active meetings provider contract; join/start stay fail-closed until backend token, media, E2EE, support, and accessibility evidence passes. |
| Weaver tool approval proof | `WeaverRuntimeServiceTest.generatesAuditedPerUserDockerProfileFromCapabilityPolicy` | Generated profiles include only approved tools and are audited; `exec` and elevated surfaces stay disabled by default. |
| Member opt-in and unauthorized tool block | `WeaverRuntimeServiceTest.blocksRuntimeWhenUserPolicyDoesNotGrantWeaverEnabled` and `docs/admin-provisioned-first-use.md` | Members receive a governed profile only after policy grant and opt-in; unauthorized tools are disabled_by_policy and audited/blocked. |
| OpenClaw fork image digest/SBOM/scan refs | `openclaw-fork-image-digest-placeholder`, `openclaw-fork-sbom-placeholder`, `openclaw-fork-scan-placeholder` | Required before runtime execution or RC promotion; placeholder references are explicit blockers, not shipped evidence. |

## Security report

Provider switching threat model:

- Threat: apply before dry-run or before migration/lossy/conflict/rollback evidence exists. Mitigation: Admin Console and backend require dry-run evidence, cutover gates, rollback boundary, recovery actions, and RBAC before guarded apply.
- Threat: raw provider tokens, provider URLs, downstream errors, SecretRef contents, or provider-internal IDs leak into client/admin/runtime/log/evidence paths. Mitigation: SecretRefs are handles only; support-safe checks scan evidence; admin/member tests assert no raw credentials or provider diagnostics are rendered.
- Threat: unauthorized role approves provider apply. Mitigation: backend control-plane tests deny operator/member apply and require `admin.provider.configure` through owner/admin policy.

OpenClaw runtime isolation and Weaver tools threat model:

- Threat: generated runtime exposes broad OpenClaw tools, exec, elevated access, or raw provider adapters by default. Mitigation: Weaver profile generation is disabled by default, per-user, Dockerized, workspace-policy-derived, audited, and tool-allowlisted.
- Threat: a member invokes an unapproved domain tool. Mitigation: runtime profile contains only approved Weave domain tools and disabled_by_policy state for everything else; unauthorized capability paths fail closed before provider access.
- Threat: OpenClaw fork/container vulnerability is ignored. Mitigation: OpenClaw fork image digest, SBOM, and scan refs are mandatory release evidence before runtime execution; until then, runtime execution remains blocked.

Dependency scans, container scans, and OpenClaw fork image scans are release-blocker gates. If scan artifacts are absent, the release evidence must record a blocker or release-owner waiver with expiry.

## Privacy report

- Weaver memory policy: member private memory is user-scoped by default; admins see policy state, approval metadata, and audit refs only unless an explicit authorized export/support workflow exists.
- Export/delete expectations: domain data follows the provider replacement contract with dry-run inventory, export expectation, delete/deprovision expectation, rollback boundary, and support-safe audit evidence.
- Support bundles: only redacted summaries, evidence refs, versions, stable states, and blocker codes are shareable. Raw provider payloads, raw logs, provider URLs with credentials, private member memory, personal display names, and SecretRef contents are excluded.

## Accessibility report

Release readiness requires evidence for:

- Admin setup path keyboard access.
- Provider switching/report review keyboard access.
- Calls join/leave/mute/camera/error states with screen-reader labels and honest fail-closed states.
- Weaver approval flow screen-reader access.
- Member capability states exposed through semantic status text, not color alone.

The canonical checklist is `docs/accessibility-release-gate.md`; Sprint 9 adds the explicit requirement that setup, provider switching/report review, Calls/LiveKit readiness, Weaver approvals, and member capability states are all release-blocking flows.

## Support-safe release evidence bundle

Every Sprint 9 release bundle must include:

- `domainRegistryVersion`: `canonical-domain-facade-v1`.
- `migrationContractVersion`: `provider-switch-portability-v1`.
- `keycloakDryRunSample`: support-safe desired-state dry-run reference only.
- `callsLiveKitReadinessArtifact`: meeting architecture and guarded-surface evidence refs.
- `openclawForkImageDigestRef`, `openclawForkSbomRef`, `openclawForkScanRef`: required before runtime execution, explicit blocker if absent.
- `weaverToolApprovalProof`: audited generated profile with approved Weave domain tools only.
- `scenarioMapping`: `@weave-product-readiness-waterfall` in `e2e/scenario_mappings.json`.
- `redactionCheck`: no raw provider tokens, cookies, private keys, raw provider errors, provider URLs with credentials, personal data, SecretRef contents, or private live logs.

Live-stack execution is required for release-candidate promotion unless a release-owner waiver names the exact blocker, commit, compensating evidence, expiry, and owner. PR-safe CI can merge the mapping and offline evidence, but cannot promote an RC by itself.
