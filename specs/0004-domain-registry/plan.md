# Implementation plan: Canonical domain registry v1

**Spec**: `specs/0004-domain-registry/spec.md`  
**Branch**: `feat/issue-427-canonical-domain-registry`

## Summary

Add a versioned canonical domain registry and deterministic validator. The registry is the shared contract for Sprint 8/9 domain vocabulary, compatibility aliases, member/admin states, domain capabilities, and portability schema references.

## Constitution check

- Repo truth recovered from GitHub issue #427 and Sprint 8/9 architecture docs: yes
- Product-first/provider-neutral boundary preserved: yes
- Acceptance/evidence identified before implementation: yes
- Accessibility/supportability/auditability/deployability addressed through provider-neutral states and support-safe evidence requirements: yes
- Provider secrets/raw diagnostics stay admin/operator-only: yes
- Weaver remains governed and disabled by default: yes

## Affected areas

- `server/src/main/resources/`: runtime-readable registry artifact.
- `specs/0004-domain-registry/`: spec-owned registry copy and lifecycle docs.
- `tools/`: registry validator.
- `build.gradle`: local gate wiring.
- `docs/`: registry documentation and navigation.

## Contracts and tests first

1. Registry JSON lists canonical domains, aliases, states, capabilities, loss classes, and portability schemas.
2. `tools/domain_registry_check.py` validates deterministic invariants.
3. Gradle `specContract` depends on registry validation.

## Final gates

- `./gradlew specContract`
- `./gradlew serverCi` when server consumers are wired beyond a resource artifact
