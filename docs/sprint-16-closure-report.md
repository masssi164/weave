# Sprint 16 closure report — Organization Setup Control Plane & Suite Facade Expansion

Date: 2026-06-01

## Scope delivered

Sprint 16 promotes the admin control plane from provider-category status into a pre-member-go-live readiness surface while keeping provider setup and secrets out of member flows.

- Admin setup/readiness control plane: `AdminControlPlaneResponse` now includes support-safe `goLiveReadiness`, identity readiness, suite domain readiness, SecretRef inventory, provider selections, policy preview, and Weaver runtime projection in one backend-owned response.
- Suite facades: Files/Documents, Boards/Tasks, and Calendar readiness rows expose canonical object kinds, capability states, portability/loss notes, selected adapter posture, audit refs, and next actions without raw provider config or credential-bearing URLs.
- Governed Weaver runtime projection: Admin Console and backend expose a signed-profile-preview shape with projected chat/model/tool/MCP items, audit receipts, revocation refs, sandbox posture, consent requirement, and disabled-by-default runtime posture. Runtime execution remains guarded and is not released by this sprint.
- Admin Console: owners/admins/operators see organization go-live readiness, suite facade readiness, identity readiness, provider replacement dry-run evidence, and Weaver projection details; members still do not receive provider setup controls.

## Evidence

Implementation evidence:

- `server/src/main/java/com/massimotter/weave/backend/model/admin/AdminControlPlaneResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/GoLiveReadinessResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/SuiteDomainReadinessResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/WeaverRuntimeProjectionResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/service/AdminControlPlaneService.java`
- `admin-console/src/api.ts`
- `admin-console/src/App.tsx`

Test evidence:

- `server/src/test/java/com/massimotter/weave/backend/service/AdminControlPlaneServiceTest.java` verifies suite/go-live/Weaver projection support-safe boundaries and absence of raw runtime/provider tokens in serialized output.
- `./gradlew serverCi` — passing locally on 2026-06-01.
- `./gradlew adminCi` — passing locally on 2026-06-01.
- `make docs-check` — passing locally on 2026-06-01.
- `./gradlew specContract acceptanceContract portabilityContractCheck releaseEvidenceCheck` — passing locally on 2026-06-01.
- `./gradlew ci --console=plain` — passing locally on 2026-06-01.

## Issue mapping

- #573 — covered by backend/admin organization go-live readiness, identity readiness linkage, SecretRef-safe admin API routes, and member setup-control denial.
- #574 — covered by suite domain readiness rows for Files/Documents, Boards/Tasks, and Calendar with backend-owned facades, canonical objects, portability notes, and safe member states.
- #575 — covered by governed Weaver RuntimeProfile projection preview, disabled-by-default runtime posture, sandbox/consent/audit fields, approval-required tool markers, and redaction boundaries.

## Boundaries and non-claims

- No production provider cutover or live migration apply is claimed.
- No legal compliance, lossless migration, or E2EE history migration claim is made.
- No raw provider setup, provider tokens, credential-bearing URLs, raw downstream bodies, SecretRef payloads, or OpenClaw runtime config is exposed to members.
- Weaver runtime execution remains guarded; this sprint delivers readiness/profile/tool/audit projection foundations only.

## Remaining risks / carryovers

- CI must pass on the PR branch before merge.
- The readiness rows are control-plane evidence and gating surfaces; live provider-specific apply remains blocked until future domain-specific apply evidence is added.
- RuntimeProfile hash is a preview/regeneration contract placeholder until the future signing pipeline produces production hashes.
