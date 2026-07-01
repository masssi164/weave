# Implementation plan: Weaver governed per-user personal assistant target

**Spec corpus commit/ID**: `ebd342904b4fce4a71efdf1edd3be2635e5ede7c / WEAVE-DOMAIN-WEAVER-GOVERNED-PA`
**Repo conformance spec**: `specs/0011-weaver-governed-pa-target/spec.md`
**Branch**: `docs/northstar-spec-coverage-complete`
**Date**: 2026-06-13

## Summary

Repair the Weaver target into a Spec Kit chain and then implement from acceptance-first slices: group-gated provisioning, admin policy preview, per-user runtime profile/memory isolation, domain-first MCP tools, approval receipts, heartbeat/automation boundaries, and support-safe audit/fallback.

## Constitution check

- Spec corpus truth recovered from `specs/weave-specs.lock.json` and relevant corpus files: yes
- Repo truth recovered from branch status, docs, GitHub issue/PR state, and CI evidence: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence path identified before implementation: yes
- Accessibility/supportability/auditability/deployability addressed: yes
- Provider secrets/raw diagnostics remain admin/operator-only: yes
- Weaver runtime remains governed and disabled-by-default unless explicitly in scope: yes

## Affected areas

- `client/`: Weaver entry/approval/fallback UX and accessibility.
- `server/`: profile generation, policy intersection, memory scope, MCP/tool registry, approvals, audit.
- `admin-console/`: Weaver policy, group eligibility, tool-scope preview, automation settings, audit posture.
- `infra/`: isolated live evidence only after contract readiness.
- `e2e/`: group-gated and governed MCP scenarios.
- `docs/`: product/operator docs with no OpenClaw private runtime leakage.
- `release/`: no release claim from this spec alone.
- `tools/`: conformance checks if forbidden leakage lint is added.

## Contracts and tests first

1. Product acceptance/Gherkin: eligibility, policy preview, per-user memory isolation, approved action, blocked stale approval, heartbeat/automation.
2. Mapping/evidence marker: `weave_spec_0011_*`.
3. API/event/schema contracts: runtime profile, approval receipt, audit event, memory scope/export/delete, MCP tool contract.
4. Unit/widget/backend/admin tests: server profile/tool/approval tests first; admin policy preview; client approval/fallback.
5. CI/evidence artifacts: `specCorpusConformance`, `specContract`, `acceptanceContract`, `serverCi`, plus admin/client gates as slices land.

## Agent work breakdown

- Product/spec steward: keep target spec aligned to corpus and Massimo answers.
- Client/accessibility: approval/fallback UX only after acceptance examples.
- Server/domain facade: runtime profile, policy intersection, memory isolation, tool execution, receipts, audit.
- Admin/policy: Weaver Control settings and preview.
- Provider/infra: no live mutation; add isolated evidence only.
- QA/evidence: E2E mappings and redacted fixtures.
- Docs/release: target docs without release/v0.1 claims.
- Security/privacy review: raw OpenClaw/private runtime leakage, secrets, overbroad tools, stale approvals.

## Rollout and migration

- Backward compatibility: disabled-by-default; existing members unaffected.
- Data migration: memory/export/delete only after storage contract exists.
- Feature flag/capability gate: org policy + `weaver-group` + per-user profile grants.
- Rollback plan: disable Weaver policy, revoke runtime profiles/receipts, stop heartbeats, retain audit.
- Release evidence: live Weaver claim requires isolated Keycloak group + governed MCP proof.

## Risks and mitigations

- Risk: OpenClaw implementation details leak into product specs.
  - Mitigation: product vocabulary uses Weave domain capabilities, CredentialRefs, profiles, receipts, and audit only.
  - Evidence gate: traceability review and grep for forbidden private runtime terms.
- Risk: autonomous actions exceed user/org authority.
  - Mitigation: intersection policy, approvals, stale-version fail-closed, audit.
  - Evidence gate: server and acceptance tests.

## Final gates

- `./gradlew specCorpusConformance`
- `./gradlew specContract`
- `./gradlew acceptanceContract`
- `./gradlew serverCi` for first implementation slice
- `./gradlew ci` when cross-stack/release-relevant
