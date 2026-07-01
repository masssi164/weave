# Sprint 32 issue #852 delegation plan

Issue: #852 `test(e2e): prove Weave Chat consent UX and MCP approval enforcement integration`

Base: `origin/dev`
Branch: `test/issue-852-weaver-chat-mcp-e2e`

## Acceptance derived from issue/specs

Governing sources: `specs/0009-domain-first-mcp-tools/spec.md`, `docs/architecture/weaver-openclaw-profile.md`, `docs/governed-weaver-runtime-security-contract.md`, `docs/architecture/mcp-domain-tool-action-registry.md`, and `e2e/features/product_e2e_scenario_layer.feature`.

The proof must show:

1. Weave Chat / Weaver owns the user consent UX and accessibility-facing approval prompt/status evidence.
2. Weave MCP owns tool policy, scoped grants, and `ApprovalReceipt` validation.
3. `chat.send_message` is a governed domain tool only after a turn starts, not inbound user-to-agent transport.
4. Deny, approve, expired/revoked, duplicate/retry, and MCP-down paths are covered.
5. Deny/expired/revoked paths have no side effect; approve requires a valid explicit consent receipt for governed tools.
6. Evidence uses support-safe correlation ids and excludes secrets, raw provider ids, raw external payloads, prompts/private memory, tokens, credential URLs, and broad customer-ready claims.

## Delegation lanes

### Channel plane reviewer

Scope: Weave Chat consent UX / accessible prompt and status evidence only.

Allowed files/globs:
- `docs/evidence/weaver-chat-mcp-separation-proof.md`
- `e2e/features/product_e2e_scenario_layer.feature`
- client tests only if changed

Checks:
- Approval prompt/status is representable in plain language, not by color alone.
- Channel ids, conversation ids, message ids, turn ids remain distinct from MCP/tool/audit ids.
- `chat.send_message` is not described or tested as inbound user-to-agent transport.
- Evidence does not claim release/customer-ready accessibility beyond the harnessed proof.

### Tool plane reviewer

Scope: Weave MCP policy / `ApprovalReceipt` enforcement only.

Allowed files/globs:
- `server/src/main/java/com/massimotter/weave/backend/weaver/**`
- `server/src/main/java/com/massimotter/weave/backend/service/WeaverRuntimeService.java`
- `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`
- `docs/evidence/weaver-chat-mcp-separation-proof.md`

Checks:
- Governed write-like tools fail closed without valid receipt.
- Approved path validates actor, action, policy, expiry, and audit ref.
- Expired/revoked paths deny before provider side effects.
- RuntimeProfile signing/revocation is not treated as the sole security boundary.

### Evidence plane reviewer

Scope: integrated deny/approve/expired-or-revoked/duplicate-or-retry/MCP-down proof and redaction.

Allowed files/globs:
- `server/src/test/**`
- `e2e/**`
- `docs/evidence/weaver-chat-mcp-separation-proof.md`

Checks:
- Tests or harnessed evidence cover deny, approve, expired-or-revoked, duplicate-or-retry, and MCP-down flows.
- Support-safe correlation ids are stable and separated by plane.
- No secrets, raw provider payloads, raw provider ids, prompts/private memory, tokens, or credential URLs appear.
- The proof clearly says what is and is not proven.
