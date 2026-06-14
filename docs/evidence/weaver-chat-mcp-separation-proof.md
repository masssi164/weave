# Weaver chat and MCP separation proof

Status: issue #765 contract/evidence lane. This is separated proof only, not a final release claim.

Current evidence posture:

- This is not final-release proof; it is PR-head evidence until all linked implementation PRs merge and final release gates pass.

- Weaver PR #25 `https://github.com/masssi164/weaver/pull/25` proves the personal `weave-chat` roundtrip on a PR head, not on merged release truth.
- Weaver PR #26 `https://github.com/masssi164/weaver/pull/26` proves RuntimeProfile-driven MCP consumption on a PR head, not on merged release truth.
- Weave PR #769 `https://github.com/masssi164/weave/pull/769` proves the separated Weave MCP projection on a PR head, not on merged release truth.
- Weave PR #767 `https://github.com/masssi164/weave/pull/767` corrects wording so MCP `chat.send_message` is never treated as inbound channel transport.

## Required proof separation

| Proof lane | Proof id | What is proved | Current evidence basis |
| --- | --- | --- | --- |
| Channel only | `WCP-CHAN-001` | One member message enters through Weave chat, reaches Weaver through `weave-chat`, starts one agent turn, and returns through `weave-chat`. no MCP server or tool invocation is required for the inbound or outbound channel path. | Weaver PR #25 plus `WEAVER.md` channel-plugin contract and Weave scenario mapping/test coverage. |
| MCP only | `WCP-MCP-001` | Weaver discovers and invokes `mcp.servers.weave-domain-tools` from a signed RuntimeProfile without any inbound chat turn. Tool visibility, approval receipts, deny paths, and audit refs are proved without any inbound channel message. | Weave PR #769 server tests plus Weaver PR #26 RuntimeProfile MCP-consumer proof. PR #769 includes `deniesUnknownOrOverbroadToolsBeforeProviderAccess`, `containsEntry("channelPlaneRef", "channels.weave-chat")`, and `contains("weave-domain-tools", "streamable-http")` coverage on its PR head. |
| Combined same turn | `WCP-COMB-001` | One inbound `weave-chat` turn may trigger one governed MCP/domain-tool action during the turn, while the user-facing reply or approval hint still returns through `weave-chat`. | Combined synthesis of PR #25, PR #26, PR #769, and wording fix PR #767. |

## Correlation-id boundary

The proof must keep these ids distinct:

- channel tenant id: `tenant:workspace`
- channel conversation id: `conv:weave-chat:<support-safe-ref>`
- inbound/outbound message id: `msg:weave-chat:<support-safe-ref>`
- OpenClaw turn id: `turn:openclaw:<support-safe-ref>`
- approval id: `approval:<support-safe-ref>`
- MCP server id: `weave-domain-tools`
- tool id: for example `files.read` or governed outbound `chat.send_message`
- domain audit id: `audit://weaver-tool/<tool>/<decision>`

In the combined same-turn proof, channel message ids, OpenClaw turn ids, approval ids, MCP server ids, tool ids, and domain audit ids stay distinct.
Channel ids must not be reused as MCP ids, audit ids, or approval ids.

## Plane-separation assertions

1. `weave-chat` is the only inbound user-to-agent transport in this proof.
2. MCP `chat.send_message` remains a governed outbound domain tool, not inbound channel transport.
3. Channel-only proof fails if it requires MCP discovery or tool invocation.
4. MCP-only proof fails if it depends on a `weave-chat` message, conversation, or transcript event.
5. Combined proof fails if the same identifier is used for both channel and MCP/audit planes.

## Failure and isolation matrix

| Case | Channel-only expectation | MCP-only expectation | Combined same-turn expectation |
| --- | --- | --- | --- |
| Duplicate inbound message | One visible reply; duplicate ignored | No tool call needed | No duplicate tool invoke unless explicitly idempotent |
| Outbound retry | Idempotent channel send or clear failure state | No duplicate side effect | Approval hint or failure still returns through `weave-chat` |
| Approval denied | Denial status is returned accessibly through `weave-chat` | `ApprovalReceipt` denied; protected tool not invoked | Same denied receipt, separated ids preserved |
| Approval expired | Expiry status is returned accessibly through `weave-chat` | Expired receipt rejected | Reply path still channel-only; tool path fails closed |
| MCP server down | Channel roundtrip can still report graceful failure | Tool call fails closed | User sees tool failure through `weave-chat`; no raw server payload |
| Weaver offline/model timeout | Channel failure or timeout status shown | No stray tool invoke | No stray invoke; no cross-plane id reuse |
| RuntimeProfile revoked | Channel stops or reloads safely | MCP invocation denied/revoked | Revocation evidence names profile hash/version separately from channel ids |
| Tenant isolation | No cross-tenant transcript leakage | No cross-tenant tool/audit leakage | Same-turn proof keeps tenant refs support-safe only |
| Raw provider data | Not present in proof | Not present in proof | Not present in proof |

## What is and is not proved

Proved now:

- Contract-level separation of channel plane and MCP/tool plane.
- Support-safe proof ids and correlation-id vocabulary for channel-only, MCP-only, and combined same-turn evidence.
- Weave-side MCP projection evidence for discovery, invoke, deny, and approval-required behavior.
- Clear statement that MCP `chat.send_message` is not inbound channel transport.

Not yet proved here:

- Final release-head merged evidence across both repos.
- Manual accessibility and assistive-technology evidence for approval, denial, expiry, and revocation states.
- Customer-ready or broad Weaver availability claims.

## Release blocker honesty

Issue #762 remains a release/customer-ready blocker until manual accessibility/assistive-technology evidence is complete. This proof must stay labeled as pre-release and PR-head evidence until the implementation PRs merge and the manual accessibility blocker is closed.