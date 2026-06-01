# Weave MCP tool contract

Status: Sprint 17 local RC evidence contract, disabled by default unless bound by a generated RuntimeProfile projection.

This document records the MCP refinement for Sprint 16 without turning MCP into the product API. The Java/Kotlin backend remains the product and control-plane authority. A future MCP gateway under `infra/weave-mcp` may expose governed Weave domain tools to approved Weaver runtimes, but only as infra glue over backend-owned contracts.

## Placement

Keep the Sprint 16 machine-readable contract under `infra/weave-workspace` because that operator/runtime area already owns Weaver lifecycle evidence and internal network boundaries. Use `infra/weave-mcp/` for any future Python FastMCP server package so the runnable MCP gateway stays clearly separated from workspace contracts and local lifecycle fixtures.

FastMCP with Python `@tool` remains an implementation candidate only. Its architecture principle is `MCP = governed tool projection over Weave APIs`: validate typed input, derive org/user/runtime context from a Weave-generated RuntimeProfile projection, call backend facade APIs, redact output, and emit audit evidence. Caller-supplied capability headers are not policy input.

## Authority boundary

- Weave backend is authoritative for product domains, provider choices, policy decisions, readiness, audit, and SecretRef/CredentialRef handling.
- MCP exposes governed actions for approved runtimes; it does not replace backend APIs.
- Runtime containers must not call provider APIs directly.
- Normal members must not configure providers, secrets, endpoint URLs, or raw OpenClaw/MCP server config through MCP.
- All tool calls are deny-by-default, audited, and filtered by the generated RuntimeProfile projection, which carries the Weave capability-policy intersection as support-safe grants and allowed tool names.

## Canonical MCP domains

The canonical domain sketch is captured in `../weave-workspace/weave-mcp-tool-contract.json`:

- `calendar`: event CRUD, calendar CRUD where appropriate, free/busy, invite/RSVP, recurrence, provider source mapping.
- `files_documents`: file/folder CRUD, search, metadata, version/content reads, permissions/share links, document actions.
- `boards_tasks`: board/list/card/task CRUD, assignments, labels, statuses, due dates, comments/activity.
- `chat_comms`: channels/rooms/messages, membership, send/read/search where allowed, support-safe provider abstraction.
- `people_identity_org`: users, groups, roles, org units, effective rights/policy, readiness.
- `admin_setup_providers`: provider registry, category mapping, readiness checks, dry-runs, capability mapping, SecretRef references only.
- `audit_policy`: audit events, redaction/support-safe views, policy simulation, permission scopes.
- `weaver_runtime_governance`: org tool allowlists, capability bundles, consent policy, sandbox/package projection, runtime audit handoff.

## Support-safe rules

Every future `@tool` must return Weave domain objects or support-safe refs only. It must not return:

- raw provider internals, raw downstream request/response bodies, or raw provider errors;
- bearer tokens, cookies, OAuth access/refresh tokens, private keys, or SecretRef values;
- credential-bearing URLs, direct provider admin URLs, room IDs/event IDs, or filenames in support-safe bundles;
- `openclaw.json`, raw MCP server config, runtime tokens, sandbox bypass controls, or exec policy bypasses.

Write/delete/external-send/provider-switch actions require approval receipts. Routine reads may be grant-based when the user has normal Weave rights and the organization has granted that tool class.

## Sprint 16 slice

Do not build every provider adapter in Sprint 16. The safe proof slice is:

1. keep backend-owned Admin Console/readiness and suite facade contracts as the source of truth;
2. record the MCP contract and test its fail-closed, support-safe domain shape;
3. project Weaver RuntimeProfile/tool governance from those domains while disabled by default;
4. if a runnable proof is added later, place it under `infra/weave-mcp/` with read-only tools such as `admin.get_readiness`, `weaver.get_runtime_profile_projection`, and one suite-domain search, plus an approval-required write stub that fails closed without an approval receipt.

This is a scope adjustment to the Weaver/runtime and suite-facade design foundation, not a runtime launch or production MCP gateway claim.
