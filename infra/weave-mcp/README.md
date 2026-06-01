# Weave MCP gateway skeleton

Status: Sprint 16 reachable skeleton, disabled by default.

`infra/weave-mcp/` contains the minimal governed MCP server package for Weaver. It is a Streamable HTTP runtime path over Weave-owned backend/domain facade APIs. It is not a second backend and it does not own provider adapters or secrets. The deterministic stdlib entrypoint is `weave-mcp`; the optional FastMCP adapter entrypoint is `weave-mcp-fastmcp` when `weave-mcp[fastmcp]` is installed.

## Runtime boundary

- Primary transport: `streamable-http`.
- Backend authority: `weave-backend` owns domains, policy, readiness, audit, provider registry, and SecretRef/CredentialRef handling.
- Admins bind this server to Weaver through the backend/admin registry contract; members never paste raw MCP endpoints or tokens into member UX.
- The server is disabled/fail-closed unless org policy, runtime profile grants, runtime token auth, and approval receipts permit discovery/invocation.
- Output is support-safe only: no provider internals, raw downstream payloads, raw endpoint secrets, runtime tokens, `openclaw.json`, or SecretRef/CredentialRef values.

## Local development

```sh
cd infra/weave-mcp
PYTHONPATH=src WEAVE_MCP_ENABLED=true python3 -m weave_mcp.app --host 127.0.0.1 --port 8765
```

Discovery requires runtime context headers and grants:

- `Authorization: Bearer <runtime token>`
- `X-Weave-Org-Id`
- `X-Weave-User-Ref`
- `X-Weave-Runtime-Profile`
- `X-Weave-Capabilities`

The Sprint 16 proof tools are:

- `admin.get_readiness` (read-only)
- `weaver.get_runtime_profile_projection` (read-only)
- `calendar.search_events` (read-only domain proof)
- `boards.comment` (write/action stub; fails closed without `approvalReceiptRef`)

Run tests through the infra gate:

```sh
bash infra/weave-workspace/tests/weave-mcp-server-test.sh
```
