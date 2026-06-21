# Weaver Chat consent and MCP approval enforcement proof

Issue: #852  
Sprint: 32  
Scope: harnessed contract/server proof on `test/issue-852-weaver-chat-mcp-e2e`; not live customer or production-release evidence.

## Governing boundary

Weave keeps two planes separate:

| Plane | Owner | Purpose | Support-safe ids in proof |
| --- | --- | --- | --- |
| Channel plane | Weave Chat / Weaver `weave-chat` channel | Member message, approval prompt, plain-language approval status, and user-facing reply/hint. | `channel:weave-chat-general`, `conversation:weaver-consent-demo`, `message:member-request-001`, `turn:weaver-852-001` |
| Tool/MCP plane | Weave MCP server and domain tool registry | Runtime tool discovery/invocation, scoped grants, policy checks, `ApprovalReceipt` validation, and audit. | `mcp-server:weave-domain-tools`, `tool:chat.send_message`, `approval:chat-send:32:001`, `audit://weaver-tool/chat.send_message/*` |

`chat.send_message` is tested only as a governed post-turn domain tool. It is not an inbound user-to-agent channel transport. The inbound user path remains the `channels.weave-chat` channel projection, and MCP server projection is marked `routingPlaneSeparated=true`.

## Channel-plane UX evidence

The #852 harness models the user-facing consent step as a Weave Chat approval hint/status, not an MCP prompt:

- Plain-language prompt: “Send this message to `channel:weave-chat-general` in `space:control-room`?”
- Status states: `approval required`, `approved`, `denied`, `expired`, `revoked`, `already used`, and `temporarily unavailable`.
- Accessibility constraint: each state is asserted as text/status evidence and must not rely on color alone.
- Correlation constraint: channel `message:*` and `turn:*` ids are distinct from `approval:*`, `mcp-server:*`, `tool:*`, and `audit://*` ids.

This is harnessed evidence only. It does not claim final manual assistive-technology signoff, broad Weaver availability, or release/customer-ready accessibility.

## Tool/MCP enforcement evidence

Executable server tests cover the enforcement point in Weave MCP / domain tool registry:

- `WeaverRuntimeServiceTest.provesWeaverChatConsentAndMcpApprovalReceiptIntegrationFailsClosed`
- `WeaverToolRegistryTest.requiresApprovalReceiptForWriteLikeToolsBeforeInvocation`
- `WeaverToolRegistryTest.mismatchedApprovalReceiptScopeFailsClosedBeforeInvocation`
- `WeaverToolRegistryTest.runtimeDeniedOrTimedOutApprovalFailsClosedWithoutServerDecision`
- `WeaverToolRegistryTest.failsClosedForServerPolicyConsentExpiryAndOverbroadGovernanceButNotRuntimeProfileMarkers`
- `WeaverToolRegistryTest.runtimeProfileMarkersAreCorrelationOnlyAndCannotOverrideServerPolicyApproval`

The approved path validates actor, action, scope refs, policy version, expiry, and `audit://` approval ref. RuntimeProfile hash/signature/revocation markers remain correlation/profile lifecycle evidence; they are not the sole security boundary for governed actions.

## Integrated #852 flow matrix

| Flow | Evidence | Expected side effect posture |
| --- | --- | --- |
| Deny / missing consent | `chat.send_message` without receipt returns `approval_required`; runtime `approval:denied:*` marker returns `approval_denied`. | No provider/domain send; support-safe audit only. |
| Approve | Valid `ApprovalReceipt` for actor `user:*`, action `chat.send_message`, scope `space:control-room`, policy `policy:v32`, future expiry, and `audit://weaver-approval/chat-send/001` returns `ok`. | Domain tool may proceed through Weave facade; raw provider payload redacted. |
| Expired, revoked, or policy mismatch | Expired receipt returns `approval_timeout`; revoked marker returns `approval_revoked`; policy mismatch returns `approval_receipt_invalid`; expired runtime token returns `runtime_token_expired`. | No side effect before provider access. |
| Duplicate or retry | Reusing the same valid approval receipt returns `duplicate_approval_receipt`. | Replay blocked before provider access. |
| MCP down / unavailable profile | Unknown/unissued runtime profile hash returns `runtime_profile_fetch_denied`. | Fail closed with support-safe result only. |

## Redaction and support-safety

Evidence may include support-safe refs such as `space:control-room`, `channel:weave-chat-general`, `approval:chat-send:32:001`, and `audit://weaver-tool/chat.send_message/invoked`.

Evidence must not include secrets, raw provider ids, raw external payloads, prompts/private memory, tokens, credential-bearing URLs, `openclaw.json`, or provider endpoint details. Tests assert redacted results and audit payloads avoid those strings.

## Proven vs not proven

Proven:

- Channel and MCP/tool planes are separated in profile/projection and evidence ids.
- `chat.send_message` is governed as a post-turn domain tool, not inbound channel transport.
- Weave MCP enforces deny, approve, expired/revoked, policy mismatch, duplicate/retry, and unavailable-profile/MCP-down fail-closed paths.
- Approval receipt validation includes actor, action, `space:`/`channel:`/domain scope refs, policy, expiry, and audit ref.
- Evidence is support-safe and avoids raw provider/secret/private-memory material.

Not proven:

- Live OpenClaw/Weaver channel plugin execution against a real provider.
- Manual assistive-technology signoff or broad customer-ready accessibility.
- Production cutover, broad Weaver availability, or public GA readiness.
