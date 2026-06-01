# Sprint 16 closure report — Organization Setup Control Plane & Suite Facade Expansion

Date: 2026-06-01

## Scope delivered

Sprint 16 promotes the admin control plane from provider-category status into a pre-member-go-live readiness surface while keeping provider setup and secrets out of member flows.

- Admin setup/readiness control plane: `AdminControlPlaneResponse` now includes support-safe `goLiveReadiness`, identity readiness, suite domain readiness, SecretRef inventory, provider selections, policy preview, and Weaver runtime projection in one backend-owned response.
- Suite facades: Files/Documents, Boards/Tasks, and Calendar readiness rows expose canonical object kinds, capability states, portability/loss notes, selected adapter posture, audit refs, and next actions without raw provider config or credential-bearing URLs.
- Governed Weaver runtime projection: Admin Console and backend expose a signed-profile-preview shape with projected chat/model/tool/MCP items, audit receipts, revocation refs, sandbox posture, consent requirement, and disabled-by-default runtime posture. Runtime execution remains guarded and is not released by this sprint.
- MCP design foundation: Sprint 16 records a fail-closed, support-safe MCP tool contract and FastMCP placement decision for governed tool projection over Weave APIs.
- Reachable MCP/admin binding skeleton: `infra/weave-mcp/` now provides a minimal disabled-by-default Streamable HTTP MCP gateway package with optional FastMCP entrypoint, tool discovery, fail-closed invocation, support-safe redaction, and the proof tools `admin.get_readiness`, `weaver.get_runtime_profile_projection`, `calendar.search_events`, and approval-gated `boards.comment`. Backend/admin and Admin Console now expose an admin-controlled MCP server binding path using server keys, `transport="streamable-http"`, endpoint refs, CredentialRef auth refs, allowed tools/capabilities, disabled state, readiness, audit refs, and redaction flags.
- Admin Console: owners/admins/operators see organization go-live readiness, suite facade readiness, identity readiness, provider replacement dry-run evidence, Weaver projection details, and the admin-bound MCP server registry; members still do not receive provider setup or raw MCP endpoint controls.

## Evidence

Implementation evidence:

- `server/src/main/java/com/massimotter/weave/backend/model/admin/AdminControlPlaneResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/GoLiveReadinessResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/SuiteDomainReadinessResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/WeaverRuntimeProjectionResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/model/admin/McpServerBindingResponse.java`
- `server/src/main/java/com/massimotter/weave/backend/service/AdminControlPlaneService.java`
- `server/src/main/java/com/massimotter/weave/backend/service/WeaverRuntimeService.java`
- `admin-console/src/api.ts`
- `admin-console/src/App.tsx`
- `infra/docs/weave-mcp-tool-contract.md`
- `infra/weave-workspace/weave-mcp-tool-contract.json`
- `infra/weave-workspace/tests/weave-mcp-tool-contract-test.sh`
- `infra/weave-workspace/tests/weave-mcp-server-test.sh`
- `infra/weave-mcp/pyproject.toml`
- `infra/weave-mcp/src/weave_mcp/app.py`
- `infra/weave-mcp/src/weave_mcp/fastmcp_app.py`
- `infra/weave-mcp/src/weave_mcp/tools/registry.py`

Merged PR evidence:

- #576 — Sprint 16 setup control plane and suite facades: `4ef947dd1909dfec83c897cda03181d38efbd0bc`.
- #577 — Sprint 16 MCP tool contract design foundation: `8277c69f864157908a4afd4e526f8ab4ef5710a2`.
- #578 — FastMCP placement alignment for future `infra/weave-mcp/` server package: `7e2ce3e5e1aabf9032537eff9dc1edb06c283bd6`.
- Reachable Streamable HTTP MCP/admin binding implementation PR — pending for this branch.

Test evidence:

- `server/src/test/java/com/massimotter/weave/backend/service/AdminControlPlaneServiceTest.java` verifies suite/go-live/Weaver projection support-safe boundaries and absence of raw runtime/provider tokens in serialized output.
- `./gradlew serverCi` — passing locally on 2026-06-01.
- `./gradlew adminCi` — passing locally on 2026-06-01.
- `make docs-check` — passing locally on 2026-06-01.
- `./gradlew specContract acceptanceContract portabilityContractCheck releaseEvidenceCheck` — passing locally on 2026-06-01.
- `./gradlew ci --console=plain` — passing locally on 2026-06-01.
- `bash infra/weave-workspace/tests/weave-mcp-tool-contract-test.sh` — passing locally on 2026-06-01 for the MCP contract.
- `bash infra/weave-workspace/tests/weave-mcp-server-test.sh` — passing locally on 2026-06-01 for Streamable HTTP config, discovery, fail-closed invocation, approval gating, and support-safe redaction.
- `./gradlew serverCi adminCi --no-daemon` — passing locally on 2026-06-01 after backend/admin MCP binding updates.
- `./gradlew infraStatic --no-daemon` — passing locally on 2026-06-01 after the reachable MCP gateway skeleton and contract tests.
- GitHub main CI passed after #577 and #578; implementation PR CI remains the closure gate.

## Issue mapping

- #573 — covered by backend/admin organization go-live readiness, identity readiness linkage, SecretRef-safe admin API routes, and member setup-control denial.
- #574 — covered by suite domain readiness rows for Files/Documents, Boards/Tasks, and Calendar with backend-owned facades, canonical objects, portability notes, and safe member states.
- #575 — covered by governed Weaver RuntimeProfile projection preview, disabled-by-default runtime posture, sandbox/consent/audit fields, approval-required tool markers, redaction boundaries, MCP contract foundation, and the minimal reachable Streamable HTTP MCP/admin binding skeleton.
- #579 — covered by the disabled-by-default `infra/weave-mcp/` gateway skeleton with stdlib and optional FastMCP entrypoints.
- #580 — covered by backend/admin MCP server binding response and Admin Console operator registry surface.
- #581 — covered by RuntimeProfile support-safe MCP bindings/tool grants plus server-side fail-closed discovery/invocation tests.

## Boundaries and non-claims

- No production provider cutover or live migration apply is claimed.
- No legal compliance, lossless migration, or E2EE history migration claim is made.
- No raw provider setup, provider tokens, credential-bearing URLs, raw downstream bodies, SecretRef payloads, or OpenClaw runtime config is exposed to members.
- Weaver runtime execution remains guarded; this sprint delivers readiness/profile/tool/audit projection foundations and a minimal disabled-by-default MCP gateway/binding skeleton only.
- MCP is reachable in local/infra test mode through Streamable HTTP, but it is not a production Weaver runtime launch. Invocation remains fail-closed unless org policy, runtime profile grants, runtime auth, and approval receipts permit it. Admins bind MCP servers via backend/admin policy; members do not wire raw endpoints.

## Remaining risks / carryovers

- The readiness rows are control-plane evidence and gating surfaces; live provider-specific apply remains blocked until future domain-specific apply evidence is added.
- RuntimeProfile hash is a preview/regeneration contract placeholder until the future signing pipeline produces production hashes.
- Future work remains to turn the disabled gateway skeleton into production operations: signed/runtime-issued tokens, full backend adapters, arbitrary admin-managed MCP server CRUD, richer policy simulation, and live Weaver runtime invocation evidence.
