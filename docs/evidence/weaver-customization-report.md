# Weaver customization evidence report

Status: Sprint 25/32 provider-lab fixture evidence, support-safe. Not isolated live E2E evidence.

## Scope

This report covers Sprint 25 issues #635, #636, #637, and #638, plus Sprint 32 governed-foundation issue #711 and governed MCP tool-execution issue #717. It proves bounded Weaver governance only: allowed personal Weaver settings regenerate a signed RuntimeProfile version/hash, forbidden customization attempts are blocked by admin policy with audit reasons, write-like Weaver domain tools require validated ApprovalReceipts or a scoped revokable always-allow grant, rollback restores a previous profile hash, narrow MCP fixture execution can create and read back an in-memory fixture event, and governed-Weaver fixture claims are gated by matching evidence. It does not prove Keycloak group-derived `weaver.enabled` or live/isolated provider calendar mutation; see #719.

## Evidence artifacts

- `release/provider-lab/weaver-runtime/profile-customization-proof.fixture.json` — #635 profile version/hash and rollback proof.
- `release/provider-lab/weaver-runtime/policy-boundary-proof.fixture.json` — #636 forbidden customization block/audit proof.
- `release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json` — #637 read/write/approval/revocation/expiry/consent grant proof.
- `release/provider-lab/weaver-runtime/sprint-25-claim-gate.fixture.json` — #638 accepted scoped claim and rejected overclaim set.
- `release/provider-lab/weaver-runtime/sprint-25-scoreboard.json` — #635-#638 green scoreboard with no release blockers.
- `release/provider-lab/weaver-runtime/sprint-32-governed-foundation.fixture.json` — #711 disabled-by-default policy evaluation, read-only first tool registry, ApprovalReceipt-gated write/external-send cases, and one-way RuntimeProfile/OpenClaw projection proof.
- `release/provider-lab/weaver-runtime/sprint-32-weaver-mcp-tool-execution.fixture.json` — #717 German event-creation prompt, support-safe `qwen3.5-9b` runtime binding, scoped MCP tool discovery, `calendar.create_event` fixture invocation, persistent scoped always-allow behavior in a synthetic signed runtime projection, in-memory fixture state change/readback, final chat audit reference, and fail-closed negative matrix. This is not a Keycloak-token or isolated provider E2E receipt; see #719.

## Evidence boundary

#718/#717 evidence is fixture/contract evidence only. It does not establish that a real Keycloak `weaver-group` exists, that a token minted for that group grants `weaver.enabled`, or that the German prompt creates an event in an isolated live calendar/provider. That missing proof is tracked by #719 and must block future Weaver/MCP live-proof claims.

## Executable gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.WeaverRuntimeServiceTest' --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain`
- `python3 tools/weaver_customization_check.py`

## Claim boundary

This evidence does not claim customer-ready Weaver, production PA availability, broad member availability, raw OpenClaw configuration access, arbitrary MCP/plugin execution, marketplace MCPs, raw memory export, raw secrets, autonomous routine writes outside an explicit scoped approval/always-allow policy, external-send without ApprovalReceipt, or Teams/Slack/provider rollout. All wording remains scoped to provider-lab, governed-foundation, and governed MCP fixture artifacts. Do not cite this report as isolated E2E proof for Keycloak group policy or live calendar event creation.
