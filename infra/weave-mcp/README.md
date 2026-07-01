# Python Weave MCP gateway

Status: transitional architecture target after ADR-004. Issue #818 temporarily moved member/Weaver-facing MCP work toward `weave-contract` and `weave-mcp-server`; ADR-004 pivots the long-term path back to server-owned OpenAPI as contract authority. This Python gateway is the intended OpenAPI-consuming MCP adapter path, still disabled by default until the migration lands.

# Weave MCP gateway skeleton

Status: Sprint 17 local RC evidence skeleton, disabled by default unless bound by a generated RuntimeProfile projection.

`infra/weave-mcp/` contains the minimal governed MCP server package for Weaver. It is a Streamable HTTP runtime path over Weave-owned backend/domain facade APIs. It is not a second backend and it does not own provider adapters or secrets. The deterministic stdlib entrypoint is `weave-mcp`; the optional FastMCP adapter entrypoint is `weave-mcp-fastmcp` when `weave-mcp[fastmcp]` is installed.

## Runtime boundary

- Primary transport: `streamable-http`.
- Backend authority: `weave-backend` owns domains, policy, readiness, audit, provider registry, and SecretRef/CredentialRef handling.
- Admins bind this server to Weaver through the backend/admin registry contract; members never paste raw MCP endpoints or tokens into member UX.
- The server is disabled/fail-closed unless org policy, a generated support-safe signed RuntimeProfile projection, runtime token auth, and approval receipts permit discovery/invocation.
- Output is support-safe only: no provider internals, raw downstream payloads, raw endpoint secrets, runtime tokens, `openclaw.json`, or SecretRef/CredentialRef values.

## Local development

```sh
cd infra/weave-mcp
PYTHONPATH=src WEAVE_MCP_ENABLED=true python3 -m weave_mcp.app --host 127.0.0.1 --port 8765
```

Discovery requires runtime context headers. Policy comes from the generated RuntimeProfile projection only; caller-supplied capability headers are ignored so the MCP gateway cannot become a second policy source. Local RC evidence verifies an `hmac-sha256:` projection signature using `WEAVE_MCP_RUNTIME_PROFILE_PROJECTION_HMAC_SECRET` (production signing/fetch-by-hash remains outside this skeleton).

- `Authorization: Bearer <runtime token>`
- `X-Weave-Org-Id`
- `X-Weave-User-Ref`
- `X-Weave-Runtime-Profile` — support-safe profile hash
- `X-Weave-Runtime-Profile-Projection` — base64url JSON containing `runtimeProfileHash`, `enabled`, `revoked`, `serverKey`, `transport`, `credentialRef`, `capabilityGrants`, `allowedTools`, `auditRef`, and `projectionSignature` references only

The Sprint 16/Sprint 32 proof tools are exposed through an explicit Python route map validated against the server-owned OpenAPI artifact (`contracts/openapi/weave-openapi.json`). Route exposure is deny-by-default: adding a backend OpenAPI route does not create an MCP tool unless the reviewed `OPENAPI_ROUTE_MAP` lists the tool, method, path, and expected `operationId`. The current proof tools are:

- `admin.get_readiness` (read-only)
- `weaver.get_runtime_profile_projection` (read-only)
- `calendar.search_events` (read-only domain proof)
- `calendar.create_event` (narrow write/action fixture; fails closed without `approvalReceiptRef` unless the signed runtime scope carries a revokable `alwaysAllowGrantRef` for `calendar.create_event`)
- `boards.comment` (write/action stub; fails closed without `approvalReceiptRef`)

Run tests through the infra gate:

```sh
bash infra/weave-workspace/tests/weave-mcp-server-test.sh
```
