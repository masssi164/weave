# Weave Server Agent Rules

The server owns Weave product facades, authorization, audit, provider boundaries, and support-safe errors.

- Keep provider access behind server-side facades. The client must never call raw providers for release behavior.
- Before adding a provider write, add permission checks, audit events, failure semantics, and contract tests.
- User writes for v0.1 are allowed only when they are explicit, attributable, authorized, and auditable.
- Agent/team writes remain out of v0.1 until the sandboxing/tool-whitelist ADR is accepted.
- Do not leak provider secrets, service tokens, raw URLs with credentials, cookies, raw downstream errors, room IDs, event IDs, filenames, usernames, or display names in support-safe responses.
- Context/Space authorization is the gate for workspace-scoped provider data.
- Prefer Cucumber/JUnit and facade contract tests for product behavior; map release-grade behavior back to `../e2e/scenario_mappings.json`.

## Java/Spring workflow

Read `docs/java-spring-standard.md` from the repository root and use the applicable `.agents/skills/weave-*` workflow. The shared standard owns declarative HTTP interfaces, concise functional/Stream API style, bean lifetimes, runtime provider state and native/AOT evidence. Do not duplicate its policy here or treat existing violations as templates.

## Module ownership during extraction

Read `docs/architecture/server-domain-ownership.md` and the affected domain's README/AGENTS. Files model and complete transactional JPA adapter implementation are under `server/domains/files`; Calendar model and JPA mappings are under `server/domains/calendar`. The sole executable is `server/app`; its remaining controllers/providers and runtime Core/domain extraction are still transitional. Do not recreate old root copies or claim a complete reduced-domain runtime.

Shared Java build mechanics live in `server/gradle/scripts/domain-library.gradle`; each library owns its dependencies. Core cannot import optional domain implementation. Shared Security/Identity/Spaces persistence stays with its actual responsibility; no generic dumping ground for domain entities or providers.

Delete obsolete code rather than commenting it out. Record reason, replacement and compatibility impact in the commit and owning domain README. Retain agreed future functionality, not obsolete implementation layers. Update Gradle dependencies, artifact input closure, source guards, domain tests and documentation with each source move.

## Entity-first and ORM-first persistence

Follow `docs/architecture/entity-first-persistence.md`, reconciled with the corpus in weave-specs#170. Entities own ORM-expressible schema, and ordinary data access uses JPA/Spring Data instead of duplicate handwritten SQL. Generate the PostgreSQL baseline from compiled mappings; generated SQL is an artifact, not a second model.

The current initializer remains transitional until generated-baseline integrity and recovery checks pass. No released-user compatibility requirement forces retention of every development migration. This does not authorize resetting owner data, rewriting an applied persistent schema or removing unmapped integrity rules. Domain-owned controllers/services and schema cutover are separate completion boundaries.

Validation from the repository root:

- `./gradlew javaAgentContractCheck :server:app:javaAgentReferenceTest` for bounded standard/reference checks, not migration/native qualification.
- `./gradlew :server:domains:files:check :server:domains:calendar:check` for domain libraries.
- `./gradlew :server:app:entitySchemaCheck` for offline generation.
- `./gradlew :server:app:postgresJpaTest --tests '*EntityFirstBaselinePostgresTest'` for generated-baseline PostgreSQL contracts.
- `./gradlew coreArchitectureCi` and `./gradlew serverCi` for the applicable combined gates; existing protocol/API checks remain required.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
