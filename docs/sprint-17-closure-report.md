# Sprint 17 closure report — First RC Shape: Governed Weaver, Workspace Flow & Guided Go-Live

> Historical evidence only. The Python/FastMCP paths and smoke commands recorded here were retired by the Spring AI MCP cutover; see `infra/docs/weave-mcp-tool-contract.md` for the active runtime.

Date: 2026-06-01

## Governing specs

- Product/spec truth remains pinned by `specs/weave-specs.lock.json` at `24c746c674da7d98e5c6abc1f1abac033a8774f2` and checked against `/Users/flotterotter/code/weave-specs/generated/manifest.json`.
- Sprint delivery references the Sprint 17 issue files under `/Users/flotterotter/sprints/sprint_17`; acceptance evidence is linked by paths/gates rather than duplicated into specs.

## Scope delivered

- #584 Governed Weaver/MCP runtime invocation: the Streamable HTTP MCP gateway now requires a Weave-generated `X-Weave-Runtime-Profile-Projection` and derives allowed tools/capabilities from that projection, not caller-supplied grant headers. RuntimeProfile hash, enabled/revoked state, transport, and `weave-domain-tools` binding must match. Discovery/invocation fail closed on missing/invalid profile, revoked profile, missing grants, disallowed tools, missing runtime auth, and missing approval receipts for writes. Denials carry support-safe audit refs.
- #585 Cross-suite workspace flow: existing Space-centered Chat/Calendar/Boards/Files capability/home/readiness flow is preserved behind Weave domain models and provider-neutral member states. Targeted workspace/client tests cover governed channel workspace, workspace roles, orchestration, organization manifest redaction, capability policy, release readiness, home, and Weaver runtime profile boundaries.
- #586 Guided admin RC go-live: Admin Console and backend now expose `releaseClaimControl` under go-live readiness with pinned spec corpus, release notes source, support bundle ref, accessibility evidence ref, unresolved vetoes, and per-gate evidence freshness/blocking state. UI renders this as an RC claim-control surface and tests cover normalization/redaction/fail-closed defaults.

## Implementation evidence

- MCP/runtime: `infra/weave-mcp/src/weave_mcp/schemas/common.py`, `infra/weave-mcp/src/weave_mcp/tools/registry.py`, `infra/weave-mcp/src/weave_mcp/app.py`, `infra/weave-mcp/tests/test_weave_mcp.py`, `infra/weave-mcp/README.md`, `infra/docs/weave-mcp-tool-contract.md`, `infra/weave-workspace/weave-mcp-tool-contract.json`.
- Admin go-live/RC claim control: `server/src/main/java/com/massimotter/weave/backend/model/admin/GoLiveReadinessResponse.java`, `ReleaseClaimControlResponse.java`, `RcEvidenceGateReadinessResponse.java`, `server/src/main/java/com/massimotter/weave/backend/service/AdminControlPlaneService.java`, `admin-console/src/api.ts`, `admin-console/src/App.tsx`, `admin-console/src/api.test.ts`, `admin-console/src/App.test.tsx`, `docs/admin-suite-readiness-setup-contract.md`, `e2e/scenario_mappings.json`.
- Workspace-flow evidence: `server/src/test/java/com/massimotter/weave/backend/controller/WorkspaceControllerTest.java`, `server/src/test/java/com/massimotter/weave/backend/service/WorkspaceCapabilityServiceTest.java`, `server/src/test/java/com/massimotter/weave/backend/service/WorkspaceReleaseReadinessServiceTest.java`, `client/test/features/chat/channel_workspace_test.dart`, `client/test/core/permissions/workspace_role_test.dart`, `client/test/features/app/domain/use_cases/workspace_orchestration_test.dart`.

## Gates run locally

Passing on 2026-06-01:

- `./gradlew doctor specCorpusConformance specContract acceptanceContract portabilityContractCheck releaseEvidenceCheck serverCi adminCi infraStatic docsCheck --console=plain`
- `bash infra/weave-workspace/tests/weave-mcp-server-test.sh`
- `bash infra/weave-workspace/tests/weave-mcp-tool-contract-test.sh`
- `cd client && flutter test test/features/chat/channel_workspace_test.dart test/core/permissions/workspace_role_test.dart test/features/app/domain/use_cases/workspace_orchestration_test.dart --no-pub`
- Targeted re-run after backend RC model addition: `./gradlew serverCi adminCi acceptanceContract releaseEvidenceCheck --console=plain`

## Issue DAG final state

- #584 — closed after PR #587 merge with this report and MCP gate links.
- #585 — closed after PR #587 merge with workspace-flow gate links.
- #586 — closed after PR #587 merge with admin go-live/RC claim-control gate links.

## PR / CI / milestone status

- Branch: `s17-admin-rc-go-live-claim-control`.
- PR: [#587](https://github.com/masssi164/weave/pull/587), merged on 2026-06-01 as `5a6e82aebdbac63814670e0baddccc7d307e6b12` with `release-notes-feature`.
- GitHub CI on PR #587 is green:
  - push CI run [26759705393](https://github.com/masssi164/weave/actions/runs/26759705393), `Gradle CI` success; release-label job skipped as expected for a push.
  - pull-request CI run [26759719577](https://github.com/masssi164/weave/actions/runs/26759719577), `Gradle CI` success and `Release Notes Label Check` success.
- GitHub issue/milestone closure: #584, #585, and #586 are closed with evidence comments; Sprint 17 milestone #17 is closed with 0 open issues / 3 closed issues.

## Boundaries and non-claims

- No production provider cutover, live infra mutation, Terraform/live service change, RC tag, public release publication, or product decision beyond Sprint 17 scope was performed.
- No raw provider secrets, runtime tokens, `openclaw.json`, raw downstream payloads, or credential-bearing URLs are exposed in member/admin support-safe surfaces.
- MCP evidence is local RC-shaped Streamable HTTP discovery/invocation and fail-closed policy proof, not a production Weaver rollout.
- Release claim control intentionally keeps RC tagging/public release blocked until release-owner decision, PR metadata release notes, fresh accessibility evidence, and green CI evidence exist.

## Remaining risks / carryovers

- Production-grade RuntimeProfile projection signing/fetch-by-hash remains future hardening beyond this local RC evidence slice.
- Real production runtime token issuance/signing and live provider CRUD remain future work beyond this RC-shaped evidence slice.
