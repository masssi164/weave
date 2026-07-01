# Weaver customization evidence report

Status: Sprint 25/32 provider-lab fixture evidence plus #719 isolated support-safe governed MCP receipt. Not customer-ready or live provider-mutation evidence.

## Scope

This report covers Sprint 25 issues #635, #636, #637, and #638, plus Sprint 32 governed-foundation issue #711, governed MCP tool-execution issue #717, and isolated Keycloak-derived governed MCP proof issue #719. It proves bounded Weaver governance only: allowed personal Weaver settings regenerate a signed RuntimeProfile version/hash, forbidden customization attempts are blocked by admin policy with audit reasons, write-like Weaver domain tools require validated ApprovalReceipts or a scoped revokable always-allow grant, rollback restores a previous profile hash, narrow MCP fixture execution can create and read back an in-memory fixture event, and #719 adds an isolated support-safe receipt schema for Keycloak group-derived `weaver.enabled` plus governed `calendar.create_event` exposure. It does not claim customer-ready Weaver or raw provider mutation by MCP.

## Evidence artifacts

- `release/provider-lab/weaver-runtime/profile-customization-proof.fixture.json` — #635 profile version/hash and rollback proof.
- `release/provider-lab/weaver-runtime/policy-boundary-proof.fixture.json` — #636 forbidden customization block/audit proof.
- `release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json` — #637 read/write/approval/revocation/expiry/consent grant proof.
- `release/provider-lab/weaver-runtime/sprint-25-claim-gate.fixture.json` — #638 accepted scoped claim and rejected overclaim set.
- `release/provider-lab/weaver-runtime/sprint-25-scoreboard.json` — #635-#638 green scoreboard with no release blockers.
- `release/provider-lab/weaver-runtime/sprint-32-governed-foundation.fixture.json` — #711 disabled-by-default policy evaluation, read-only first tool registry, ApprovalReceipt-gated write/external-send cases, and one-way RuntimeProfile/OpenClaw projection proof.
- `release/provider-lab/weaver-runtime/sprint-32-weaver-mcp-tool-execution.fixture.json` — #717 German event-creation prompt, support-safe `qwen3.5-9b` runtime binding, scoped MCP tool discovery, `calendar.create_event` fixture invocation, persistent scoped always-allow behavior in a synthetic signed runtime projection, in-memory fixture state change/readback, final chat audit reference, and fail-closed negative matrix.
- `release/provider-lab/weaver-runtime/issue-719-isolated-e2e-receipt.fixture.json` — #719 support-safe isolated receipt schema for Keycloak `weaver-group`/`weave-weaver-runtime` membership, derived `weaver.enabled`, calendar MCP tool exposure through `X-Weave-Runtime-Profile-Projection`, approval-gated event creation, readback, negative cases, and claim boundaries.

## Evidence boundary

#718/#717 evidence is fixture/contract evidence only. #719 adds the missing checked-in Keycloak group/test-user mapping and executable support-safe receipt checks for the isolated governed MCP path. The boundary remains explicit: the receipt does not claim customer-ready Weaver, raw provider payload access, broad calendar write grants, or `providerMutationPerformedByMcp=true`.

## Executable gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.WeaverRuntimeServiceTest' --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain`
- `python3 tools/weaver_customization_check.py`

## Claim boundary

This evidence does not claim customer-ready Weaver, production PA availability, broad member availability, raw OpenClaw configuration access, arbitrary MCP/plugin execution, marketplace MCPs, raw memory export, raw secrets, autonomous routine writes outside an explicit scoped approval/always-allow policy, external-send without ApprovalReceipt, direct provider mutation by MCP, or Teams/Slack/provider rollout. All wording remains scoped to provider-lab, governed-foundation, governed MCP fixture artifacts, and the #719 isolated support-safe receipt.
