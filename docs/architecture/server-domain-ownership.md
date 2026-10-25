# Server ownership and domain consolidation

Implementation owner: #1745; integration parent: #1741; PR: #1746. This is the current implementation guide, not specification-corpus authority or release qualification.

## Current state versus target

**Implemented:** Files model, entities/repositories and transactional JPA adapters live under `server/domains/files`. Calendar model and JPA mappings live under `server/domains/calendar`. Each model/persistence library is a real Gradle project with its own compile boundary and tests. The retired root Files model has no second production copy.

**Still transitional:** `server` remains the executable application. Files/Calendar authenticated services, controllers, provider adapters and runtime wiring, Chat extraction, the small shared Core and final `server/app` composition are not complete. The generic root JPA and runtime-adapter projects remain only until their remaining owners are extracted. Do not claim that reduced `bootJar` variants, module-local runtime auto-configuration, ARM64/native execution or owner HIL are qualified by these library moves.

The initial release product scope is **Files, Calendar and Chat**. Boards, Office, DevOps, Agent Runtime, portability/transfer, guest-facing and integration features remain planned domains. Move their implementations out of Core rather than deleting agreed product scope.

## Ownership rules

| Owner | Responsibility |
| --- | --- |
| App | Executable entry point, composition of selected domains, OpenAPI export and the complete historical Flyway catalog |
| Core | Genuinely shared identity, organization admission, Security, Spaces, audit and narrow cross-domain contracts |
| Domain model | Provider-neutral values and invariants; no Spring, JPA, HTTP, Jackson or provider SDK |
| Domain persistence | That domain's entities, IDs, repositories, transaction adapters and persistence-specific dependencies |
| Domain application/API | Authenticated use cases, orchestration, controllers and wire DTOs |
| Domain provider integration | Provider adapters, their configuration, transport/serialization and SDK dependencies |

There is no general requirement for catch-all `core/adapters` or `core/persistence` projects. Shared Security/Spaces/Identity may need persistence, but it belongs with that actual platform responsibility. Files or Calendar entities never become Core merely because they use the same database.

Core must not depend on an optional domain implementation. Domains must not depend on the executable App or another domain's repositories. A source move that needs a reverse dependency is not complete; extract a small honest contract instead of hiding a cycle.

**Security exception:** the historically Matrix-named durable member/session revocation storage is shared authentication state. It must remain available without Chat, preserve its existing table/hash format and fail closed. Room/device/crypto state belongs to Chat.

## Current leaf projects

- `:server:domains:files:model` and `:server:domains:files:persistence`: see `server/domains/files/README.md` for ownership and removal rationale.
- `:server:domains:calendar:model` and `:server:domains:calendar:persistence`: see `server/domains/calendar/README.md`.
- Shared Java/toolchain/JUnit mechanics: `server/gradle/scripts/domain-library.gradle`. Dependencies remain in each library. Domain-qualified groups and archives prevent sibling libraries named `model` or `persistence` from claiming the same Gradle component identity.

The canonical package-direction rules remain in `docs/architecture/core-package-boundaries.md`. Keep model/provider/persistence/API boundaries explicit; do not generate a Gradle project for every package or maintain empty placeholder domains.

## Domain runtime composition

Each completed runtime domain registers its own `@AutoConfiguration` class through:

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Use one fully qualified class name per line, explicit imports and no broad domain component scan. App does not maintain a second handwritten list of every domain bean. Pure Java model libraries need no fake Spring registration. Serving beans must not accidentally load in schema-init or Dogfood operator CLI contexts.

Optional shared workflows consume domain-owned contributions through small contracts, not concrete Files/Calendar/Chat implementations. Domain inclusion is a build decision; provider binding is a separate runtime decision.

## Build selection and OpenAPI

The agreed selection rule is explicit pipeline `domains` / `-Pdomains`, then a matching branch's `pinned_domains`, then `all`. `all` means registered domains, not every future feature. Reuse the shared resolver from #1744 when integrating production composition; do not invent another resolver or silently package excluded code. Branch pins are resolved to exact source commits. Domain set, platform, toolchain/flavor, source and spec identity must participate in artifact identity and digest consumption.

The selected controllers and their Spring handler mappings must exist **before** code-first OpenAPI export. Use actual Gradle prerequisite dependencies, not ordering hints alone. Generated User/Admin clients follow the completed export. Keep the accepted OrgManifest/API contract intact; reduced/no-Chat discovery requires its explicit accepted contract change.

Automatic delivery target remains the agreed Linux ARM64 container for Apple Silicon. Other platform variants are manual. Native Image/AOT, JNI and actual consumption on the Mac mini require separate execution evidence; moving Java classes is not that evidence.

## Cleanup and code style

Delete verified duplicates and obsolete build plumbing; do not leave commented-out old implementations. Every removal records the old location, replacement (or why none is needed), compatibility consequence and reason in the commit and the owning domain README.

Do not delete planned features, retained database state or historical migrations because they are outside the first release. Preserve behavior while moving, then simplify deliberate code paths in separately reviewable changes.

Prefer immutable records, constructor injection, typed configuration, declarative HTTP interfaces and explicit Spring auto-configuration. Use Lombok only for real boilerplate; do not replace invariants or add implicit credential `toString()` / JPA equality semantics. Use streams where clearer, not to disguise stateful transaction or lock ordering.

## Verification and maintenance

From the repository root:

```sh
./gradlew :server:domains:files:check :server:domains:calendar:check
./gradlew coreArchitectureCi
```

These are library checks, not a claim that the production domain selector or new App assembly is finished. Existing complete Server, PostgreSQL, API and protocol checks remain required. The source-level relational inventory includes nested domain projects, not only the old root locations; domain architecture tests inspect their compiled classes. Image source-input closure includes new module sources/build files and shared build mechanics.

Preserve one complete historical Flyway catalog. JPA mappings remain domain-owned and production uses the migrated schema. No source move rewrites historical SQL.

## Six-phase completion boundary

1. Ownership planning: established; refine the actual incoming/outgoing dependencies while extracting.
2. Domain shape: model/persistence pattern implemented for Files and Calendar; runtime/provider/API composition still open.
3. Extraction/consolidation: in progress; no global completion claim.
4. Core finalization: open until all optional feature implementations and dependencies leave Core.
5. Domain finalization: open until runtime auto-configuration, handler/API composition, selected classpaths and actual functional tests pass.
6. Final presentation: provide before/after ownership, real removals, dependency changes, measured resource outcomes and remaining limits after integration.

Update code, source guards, build inputs, scoped `AGENTS.md`, domain READMEs and this guide in the same change. Historical intermediate-tree diagnoses are retained in Git/PR history, not repeated as current implementation instructions. Keep PR #1746 draft until its actual scope has fresh exact-head CI and independent review. No live deployment or human acceptance is implied.
