# Implementation plan: Replace with title

**Spec**: `specs/0000-slug/spec.md`  
**Branch**: `type/short-slug`  
**Date**: YYYY-MM-DD

## Summary

One paragraph: technical approach that satisfies the spec without expanding product scope.

## Constitution check

- Repo truth recovered from `main`, docs, GitHub issue/PR state, and CI evidence: yes/no
- Product-first/provider-neutral boundary preserved: yes/no
- Acceptance/evidence path identified before implementation: yes/no
- Accessibility/supportability/auditability/deployability addressed: yes/no
- Provider secrets/raw diagnostics remain admin/operator-only: yes/no
- Weaver/OpenClaw runtime remains governed and disabled-by-default unless explicitly in scope: yes/no

Any `no` requires a blocker or a documented exception before implementation.

## Affected areas

- `client/`:
- `server/`:
- `admin-console/`:
- `infra/`:
- `e2e/`:
- `docs/`:
- `release/`:
- `tools/`:

## Contracts and tests first

1. Product acceptance/Gherkin:
2. Mapping/evidence marker:
3. API/event/schema contracts:
4. Unit/widget/backend/admin tests:
5. CI/evidence artifacts:

## Agent work breakdown

Use specialists only when they reduce risk or parallelize independent files. Each brief must include allowed files, stop conditions, and a required gate.

- Product/spec steward:
- Client/accessibility:
- Server/domain facade:
- Admin/policy:
- Provider/infra:
- QA/evidence:
- Docs/release:
- Security/privacy review:

## Rollout and migration

- Backward compatibility:
- Data migration:
- Feature flag/capability gate:
- Rollback plan:
- Release evidence:

## Risks and mitigations

- Risk:
  - Mitigation:
  - Evidence gate:

## Final gates

- `./gradlew specContract`
- `./gradlew acceptanceContract`
- Smallest area gate(s):
- `./gradlew ci` when cross-stack or release-relevant
