# Tasks: Canonical domain registry v1

**Spec**: `specs/0004-domain-registry/spec.md`  
**Plan**: `specs/0004-domain-registry/plan.md`

## Phase 0: Truth recovery

- [x] T001 Read Sprint 8/9 issue acceptance for #427/#428/#429/#438/#439/#442.
- [x] T002 Read governing product-line and spec-development docs.

## Phase 1: Registry contract

- [x] T010 Add canonical domain registry JSON with required domains.
- [x] T011 Add compatibility aliases for historical category names.
- [x] T012 Add member/admin states and domain capabilities.
- [x] T013 Add portability schema names, loss classes, and migration lifecycle states.

## Phase 2: Validation and docs

- [x] T020 Add deterministic registry validation tool.
- [x] T021 Wire registry validation into Gradle spec contract.
- [x] T022 Add docs page and navigation link.

## Phase 3: Validation

- [ ] T030 Run `./gradlew specContract`.
- [ ] T031 Run downstream server/admin/client consumers in follow-up PRs.
