# Sprint 9 product-readiness waterfall evidence

Status: support-safe acceptance and release-readiness contract for `#436`, `#449`, and `#450`.

Sprint 9 is not a marketing sprint. It is a product-readiness waterfall: governance, architecture, control plane, domain implementation, Agent Runtime Control, and release readiness must each leave reviewable evidence before any product-ready claim is made.

## Governance and board evidence

- Milestone: [`Sprint 9 — Product Readiness Waterfall`](https://github.com/masssi164/weave/milestone/9).
- Board: [Weave Delivery Board](https://github.com/users/masssi164/projects/2/views/1).
- Required phases: Governance, Architecture, Control Plane, Domain Implementation, Agent Runtime Control, Release Readiness.
- Required issue labels: `track:sprint-9`, one `phase:*` label, priority, type, and `release-blocker` for any product-readiness blocker.
- Done rule: no issue moves to Done without acceptance criteria, evidence, and a claim decision.
- README and marketing updates stay blocked until the linked evidence exists.
- Provider apply stays blocked until dry-run, reports, RBAC, audit, rollback, and redaction evidence exist.
- Weaver runtime execution stays blocked until a compatible workload-credential projection, disposable-cell isolation, cross-node restore, and immutable image/SBOM/scan evidence exist. Source provenance alone does not make the runtime executable or production-ready.

## Product-ready definition

A Weave capability is product-ready only when all of these are true:

1. The member-facing surface uses Weave-owned domain language and stable states only.
2. Backend policy evaluates capability and role/group grants before provider access.
3. Provider readiness, migration, conflict, rollback, and lossy mapping reports are admin/operator-only and support-safe.
4. Accessibility evidence covers keyboard and screen-reader paths for setup, provider switching, calls, Agent Runtime Control administration, and member entitlement states.
5. Security and privacy evidence proves no raw provider tokens, SecretRef contents, raw provider errors, provider URLs with credentials, private member memory, or personal identifiers are emitted in client, admin console, runtime profile, logs, support bundles, screenshots, docs, or release artifacts.
6. Release evidence points to executable tests, scenario mappings, or explicit release-owner waivers rather than screenshots alone.

## Vertical scenario evidence bundle

The `@weave-product-readiness-waterfall` scenario maps to this support-safe bundle:

| Required evidence | Source-backed pointer | Claim decision |
| --- | --- | --- |
| Domain registry version | `canonical-domain-facade-v1` from `CanonicalDomainDefinition` and `CanonicalDomainFacadeServicesTest` | Registry read is proven through backend domain-facade contracts for files/documents, calendar/meetings, boards/tasks, and identity/admin policy. |
| Migration contract version | `provider-switch-portability-v1` release evidence name covering preflight, portable export/import, cutover, rollback, and recovery | Apply remains a guarded decision; full automated cross-provider migration is not claimed. |
| Keycloak realm baseline evidence | Canonical realm rendering/import tests, the bounded post-import migration contract, and `AdminControlPlaneControllerTest` | Static IAM has one reproducible declarative source; dynamic human identity lifecycle remains Server-owned; Admin Health exposes only support-safe readiness. |
| Provider replacement dry-run state | `AdminControlPlaneControllerTest.providerReplacementDryRunReturnsBackendOwnedPortableSwitchContract` | Product-provider migration planning remains a domain control-plane contract and is separate from Keycloak baseline reconciliation. |
| Boards/OpenProject portability report | `DomainAdapterRegistryMapperTest.coreProductDomainsCarryExecutableAdapterFitContracts` and the Boards portability fragments in `ProviderCapabilityContracts` | Boards portability is dry-run/report evidence, not live data movement. |
| Calls/MatrixRTC readiness artifact | `docs/meeting-architecture-decision.md` and `docs/roadmap-and-guarded-surfaces.md` | MatrixRTC Profile 0 is the member signaling target and LiveKit is only a replaceable SFU; join/start stays fail-closed until RTC authorization, TURN, media E2EE, support, and accessibility evidence passes. |
| ARC workload identity and lifecycle proof | `AgentRuntimeAdminControllerTest`, `AgentRuntimeAdminServiceTest`, `McpWorkloadAuthorizationServiceTest`, and `SpringAiMcpTransportTest` | An entitled cell receives its own Keycloak workload client; MCP admits only the exchanged, cell-bound workload context; deletion revokes the client and runtime state without deleting provider data. |
| Entitlement and empty-catalog fail-closed proof | `KeycloakRuntimeIdentityAuthorityTest`, `McpWorkloadAuthorizationServiceTest`, and `SpringAiMcpTransportTest` | Keycloak Organizations are the entitlement authority. RuntimeProfile and OpenClaw approval data are not domain grants, and MCP domain catalogs remain empty until their owning domain action contracts are implemented. |
| Weaver/OpenClaw upstream provenance | `weaver/UPSTREAM.md`, `weaver/weaver.fork-policy.json`, and `pnpm check:weaver-fork` on the pinned `v2026.7.1` baseline | The thin-fork source delta and upstream commit are reviewable. An executable release still requires immutable runtime image digest, SBOM, and scan artifacts; missing artifacts are blockers, never placeholder evidence. |

## Security report

Provider switching threat model:

- Threat: apply before dry-run or before migration/lossy/conflict/rollback evidence exists. Mitigation: Admin Console and backend require dry-run evidence, cutover gates, rollback boundary, recovery actions, and RBAC before guarded apply.
- Threat: raw provider tokens, provider URLs, downstream errors, SecretRef contents, or provider-internal IDs leak into client/admin/runtime/log/evidence paths. Mitigation: SecretRefs are handles only; support-safe checks scan evidence; admin/member tests assert no raw credentials or provider diagnostics are rendered.
- Threat: unauthorized role approves provider apply. Mitigation: backend control-plane tests deny operator/member apply and require `admin.provider.configure` through owner/admin policy.

OpenClaw runtime isolation and workload MCP threat model:

- Threat: a cell reuses a human token, shared service account, or another cell's identity. Mitigation: each cell gets a private-key-authenticated Keycloak client; MCP accepts workload tokens only, validates issuer/type/audience/scopes, performs token exchange, and asks the backend to revalidate the current cell/member binding.
- Threat: a signed RuntimeProfile or native OpenClaw approval is treated as a Weave domain grant. Mitigation: RuntimeProfile is desired state only, OpenClaw approvals remain runtime evidence only, MCP catalogs are empty by default, and every future domain action must be reauthorized by its owning Weave domain.
- Threat: a cell restart loses state or reads durable local bytes. Mitigation: complete encrypted runtime generations live in the backend-owned Runtime State Store; cell-local paths are ephemeral. Production readiness remains blocked until disposable orchestration and cross-node reconstruction prove that boundary.
- Threat: the thin fork or runtime image drifts from reviewed OpenClaw source. Mitigation: the fork guard pins the upstream tag/commit and patch budget; release promotion additionally requires real immutable image digest, SBOM, and scan artifacts.

Dependency scans, container scans, and runtime image scans are release-blocker gates. If scan artifacts are absent, the release evidence must record a blocker or release-owner waiver with expiry; placeholder values are invalid evidence.

## Privacy report

- Runtime state policy: complete OpenClaw state is encrypted outside the disposable cell and bound to one cell/member identity. Admins see lifecycle state and support-safe audit refs, not private runtime contents or key material.
- Export/delete expectations: domain data follows the provider replacement contract with dry-run inventory, export expectation, delete/deprovision expectation, rollback boundary, and support-safe audit evidence.
- Support bundles: only redacted summaries, evidence refs, versions, stable states, and blocker codes are shareable. Raw provider payloads, raw logs, provider URLs with credentials, private member memory, personal display names, and SecretRef contents are excluded.

## Accessibility report

Release readiness requires evidence for:

- Admin setup path keyboard access.
- Provider switching/report review keyboard access.
- Calls join/leave/mute/camera/error states with screen-reader labels and honest fail-closed states.
- Agent Runtime Control lifecycle status, create, suspend, resume, revoke, delete-state, and recreate actions with screen-reader access.
- Member capability states exposed through semantic status text, not color alone.

The canonical checklist is `docs/accessibility-release-gate.md`; Sprint 9 adds the explicit requirement that setup, provider switching/report review, Calls/MatrixRTC readiness, Agent Runtime Control administration, and member entitlement states are all release-blocking flows.

## Support-safe release evidence bundle

Every Sprint 9 release bundle must include:

- `domainRegistryVersion`: `canonical-domain-facade-v1`.
- `migrationContractVersion`: `provider-switch-portability-v1`.
- `keycloakRealmBaselineEvidence`: support-safe realm import and bounded post-import migration references only.
- `callsMatrixRtcReadinessArtifact`: meeting architecture and guarded-surface evidence refs.
- `arcWorkloadIdentityProof`: per-cell Keycloak provisioning, token exchange, MCP admission, backend context revalidation, and revocation evidence refs.
- `runtimeProviderProvenanceRefs`: pinned OpenClaw tag/commit plus the checked thin-fork budget; an executable runtime release must also include non-placeholder image digest, SBOM, and scan refs.
- `scenarioMapping`: `@weave-product-readiness-waterfall` in `e2e/scenario_mappings.json`, marked as `offline-spec` evidence because it aggregates checked-in executable and documentation contracts rather than a single live runtime marker.
- `redactionCheck`: no raw provider tokens, cookies, private keys, raw provider errors, provider URLs with credentials, personal data, SecretRef contents, or private live logs.

Live-stack execution is required for release-candidate promotion unless a release-owner waiver names the exact blocker, commit, compensating evidence, expiry, and owner. PR-safe CI can merge the mapping and offline evidence, but cannot promote an RC by itself.
