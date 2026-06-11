# Weaver customization evidence report

Status: Sprint 25 provider-lab evidence, support-safe.

## Scope

This report covers Sprint 25 issues #635, #636, #637, and #638, plus Sprint 32 governed-foundation issue #711 and governed MCP tool-execution issue #717. It proves bounded Weaver governance only: allowed personal Weaver settings regenerate a signed RuntimeProfile version/hash, forbidden customization attempts are blocked by admin policy with audit reasons, write-like Weaver domain tools require validated ApprovalReceipts or a scoped revokable always-allow grant, rollback restores a previous profile hash, narrow MCP tool execution can create and read back a fixture event, and governed-Weaver claims are gated by matching evidence.

## Evidence artifacts

- `release/provider-lab/weaver-runtime/profile-customization-proof.fixture.json` — #635 profile version/hash and rollback proof.
- `release/provider-lab/weaver-runtime/policy-boundary-proof.fixture.json` — #636 forbidden customization block/audit proof.
- `release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json` — #637 read/write/approval/revocation/expiry/consent grant proof.
- `release/provider-lab/weaver-runtime/sprint-25-claim-gate.fixture.json` — #638 accepted scoped claim and rejected overclaim set.
- `release/provider-lab/weaver-runtime/sprint-25-scoreboard.json` — #635-#638 green scoreboard with no release blockers.
- `release/provider-lab/weaver-runtime/sprint-32-governed-foundation.fixture.json` — #711 disabled-by-default policy evaluation, read-only first tool registry, ApprovalReceipt-gated write/external-send cases, and one-way RuntimeProfile/OpenClaw projection proof.
- `release/provider-lab/weaver-runtime/sprint-32-weaver-mcp-tool-execution.fixture.json` — #717 German event-creation prompt, support-safe `qwen3.5-9b` runtime binding, scoped MCP tool discovery, `calendar.create_event` invocation, persistent scoped always-allow behavior, fixture state change/readback, final chat audit reference, and fail-closed negative matrix.

## Executable gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.WeaverRuntimeServiceTest' --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain`
- `python3 tools/weaver_customization_check.py`

## Claim boundary

This evidence does not claim customer-ready Weaver, production PA availability, broad member availability, raw OpenClaw configuration access, arbitrary MCP/plugin execution, marketplace MCPs, raw memory export, raw secrets, autonomous routine writes outside an explicit scoped approval/always-allow policy, external-send without ApprovalReceipt, or Teams/Slack/provider rollout. All wording remains scoped to provider-lab, governed-foundation, and governed MCP fixture artifacts.
