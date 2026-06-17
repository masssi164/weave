# Weave contract and Java MCP server

Issue #818 introduces a contract-first member/Weaver/MCP boundary.

## Modules

- `weave-contract` is the shared source for member and Weaver-facing MCP DTOs, canonical member/MCP domain metadata, capability names, tool metadata, and generated-schema hints.
- `server` depends on `weave-contract` for governed Weaver domain tool discovery and invocation metadata. Authorization, approval, audit, provider selection, and business logic remain server-side.
- `weave-mcp-server` is a thin Java/Spring Boot MCP JSON-RPC adapter. It exposes MCP `initialize`, `tools/list`, and `tools/call`, uses shared DTO/schema metadata from `weave-contract`, and delegates calls to `weave-server` APIs.

Admin/control-plane DTOs, adapter assignment/provenance records, SecretRef diagnostics, provider-native payloads, and provider IDs remain server-local unless a later explicit architecture review moves them.

## Capability model

Canonical member/MCP capabilities use contract names such as:

- `files.read`
- `calendar.read`
- `calendar.manage_events`
- `boards.read`
- `boards.update_task`

The `weaver.*` dialect must not be used as canonical domain-tool capability truth. If a runtime needs another grant representation, implement it as a projection from `weave-contract` metadata and test the mapping.

Write-like tools are marked in contract metadata and require approval before backend invocation. The backend remains the final policy, authorization, and audit authority; MCP-layer hints are discovery/UX aids, not security enforcement by themselves.

## Weaver/OpenClaw projection

The preferred runtime configuration is a trusted server/namespace allow for `weave-mcp` plus endpoint/auth details. Tools are discovered from MCP `tools/list` only after the adapter has a valid runtime token and RuntimeProfile context; the adapter delegates tool availability to backend-governed discovery. Weaver/OpenClaw should not hand-maintain a duplicate per-tool registry. If a deployment requires per-tool filtering, generate it from `weave-contract` metadata.

Weaver is a separate repository. This repo records the projection contract only; create a Weaver repository task before mutating runtime-profile code there.

## Local development from containerized runtimes

When Weaver or a user runtime runs in a container, `localhost` is the container, not the host. Run `weave-mcp-server` on the host and point the container at the host gateway:

```sh
export WEAVE_SERVER_BASE_URL=http://host.docker.internal:8080
export WEAVE_MCP_BASE_URL=http://host.docker.internal:8765
./gradlew :weave-mcp-server:bootRun --args='--server.port=8765 --weave.server.base-url=http://localhost:8080'
```

Docker Desktop provides `host.docker.internal` automatically. On Linux/native Docker, add the host gateway:

```yaml
services:
  weaver-runtime:
    extra_hosts:
      - "host.docker.internal:host-gateway"
    environment:
      WEAVE_MCP_BASE_URL: http://host.docker.internal:8765
      WEAVE_SERVER_BASE_URL: http://host.docker.internal:8080
```

Smoke test from a container:

```sh
docker run --rm --add-host=host.docker.internal:host-gateway curlimages/curl \
  -sS -H 'content-type: application/json' \
  -H 'authorization: Bearer <runtime-token>' \
  -H 'x-weave-runtime-profile: sha256:<runtime-profile-hash>' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  http://host.docker.internal:8765/mcp
```
