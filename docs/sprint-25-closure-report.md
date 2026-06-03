# Sprint 25 closure report — Weaver Customization

Status: implementation/evidence closure candidate.

## Governing scope

Sprint 25 covers #635, #636, #637, and #638 under the governed Weaver runtime contract. The sprint follows the product-line boundary: Weave remains provider-neutral first, Admin Console/control-plane policy owns setup and policy, and Weaver customization is optional, governed, and disabled unless organization policy enables it.

## Issue DAG final state

1. #635 profile versioning and rollback proof.
2. #636 admin policy boundary enforcement depends on the RuntimeProfile customization seam from #635.
3. #637 tool approval gates are independent server/tool-registry proof, but their result feeds #638.
4. #638 customized Weaver claim gate depends on #635, #636, and #637 evidence.

## Evidence artifacts

- `release/provider-lab/weaver-runtime/profile-customization-proof.fixture.json` — #635.
- `release/provider-lab/weaver-runtime/policy-boundary-proof.fixture.json` — #636.
- `release/provider-lab/weaver-runtime/tool-approval-gate-proof.fixture.json` — #637.
- `release/provider-lab/weaver-runtime/sprint-25-claim-gate.fixture.json` — #638.
- `release/provider-lab/weaver-runtime/sprint-25-scoreboard.json` — #635, #636, #637, #638 scoreboard.
- `docs/evidence/weaver-customization-report.md` — support-safe evidence summary for #635, #636, #637, and #638.

## Local gates

- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.WeaverRuntimeServiceTest' --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain`
- `python3 tools/weaver_customization_check.py`
- `./gradlew releaseEvidenceCheck` after the Sprint 25 validator is wired into release evidence.

## Claim boundary

Sprint 25 may claim only provider-lab, support-safe Weaver customization proof. It must not claim customer-ready Weaver, production PA availability, raw OpenClaw config access, arbitrary MCP/plugin execution, raw secrets, raw memory, public release readiness, or Teams/Slack implementation.

## Next sprint seam

The setup-flow/Forgejo proof belongs in Sprint 26/#659 after Sprint 25 closure. Sprint 25 does not require local Forgejo runner mutation; it only establishes RuntimeProfile, policy, approval, and claim gates that the Admin Console CI/CD-backed setup proof must preserve.
