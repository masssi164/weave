# Weaver customization evidence report

Status: Sprint 25 provider-lab evidence, support-safe.

## Scope

This report covers Sprint 25 issues #635, #636, #637, and #638. It proves a bounded customization slice only: allowed personal Weaver settings regenerate a signed RuntimeProfile version/hash, forbidden customization attempts are blocked by admin policy with audit reasons, write-like Weaver domain tools require validated ApprovalReceipts, rollback restores a previous profile hash, and customized-Weaver claims are gated by matching scoreboard evidence.

## Evidence artifacts

- `release/provider-lab/weaver-runtime/profile-customization-proof.fixture.json` — #635 profile version/hash and rollback proof.
- `release/provider-lab/weaver-runtime/policy-boundary-proof.fixture.json` — #636 forbidden customization block/audit proof.
- `release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json` — #637 read/write/approval/revocation/expiry/consent grant proof.
- `release/provider-lab/weaver-runtime/sprint-25-claim-gate.fixture.json` — #638 accepted scoped claim and rejected overclaim set.
- `release/provider-lab/weaver-runtime/sprint-25-scoreboard.json` — #635-#638 green scoreboard with no release blockers.

## Executable gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.WeaverRuntimeServiceTest' --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain`
- `python3 tools/weaver_customization_check.py`

## Claim boundary

This evidence does not claim customer-ready Weaver, production PA availability, raw OpenClaw configuration access, arbitrary MCP/plugin execution, raw memory export, raw secrets, or Teams/Slack/provider rollout. All wording remains scoped to provider-lab Sprint 25 artifacts.
