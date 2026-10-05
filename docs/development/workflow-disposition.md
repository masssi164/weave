# Workflow disposition

Status: current implementation inventory for #1470/#1480; historical #1307 foundation jobs remain where useful.

Every GitHub Actions workflow has one current disposition. This document describes whether a workflow proves the data-sovereignty core, supports a temporary transition, is manual release/client work, or must be retired. The classification does not make a historical workflow architecture authority.

## Required core

### `ci.yml`

Runs the focused Java/Server/Data/MCP foundation gates and the `Generated API consumers`
job. The latter uses pinned Flutter and Node tooling to regenerate the code-first User/Admin
contracts and all JVM, Dart and TypeScript consumers and run their actual consumer gates.
The ordinary server export uses a test-only Matrix protocol collaborator; it does not build
or load JNI. The separate Server protocol foundation job retains the real native runtime.
See [the generated API CI implementation contract](../../specs/generated-api-ci-contract.md)
for inputs, commands, isolation, cleanup and failure evidence.

The `Gradle CI` aggregate succeeds only when the real architecture, canonical data,
PostgreSQL persistence, Server regression, MCP foundation, documentation and generated API
consumer jobs succeed. A cancelled or skipped required job fails the aggregate.

The `Release Notes Label Check` remains required by the current protected workflow.

## Transitional optional core

### `native-persistence-closure.yml`

Retain temporarily as an additional PostgreSQL/native-persistence regression lane while #1320 moves all schema and repository contracts under `postgresPersistenceCi`. Delete or reduce it after parity.

### `native-provider-gate.yml`

Retain temporarily as an additional native-composition regression lane while #1326/#1301/#1302 remove duplicate provider-shaped application implementations. Its name is historical and must not define the target architecture.

### `live-stack-e2e.yml`

Provides isolated real-runtime regression, including generated User Files calls and
browser/OIDC member access. Its historical DAV/native collaboration portions do not prove
all revised #1480 journeys. Single-sign-in Flutter, generated Calendar and independent
Matrix-client interoperability still require their own runtime evidence. Provider migration
and cutover proof belongs to #1498.

## Manual release or client

### `ios-dogfood.yml`

Manual/future client distribution only. It must not run as a prerequisite for Server/Data/MCP changes or mainline convergence. Remove or move to a later release lane after the core workflow transition is complete.

### `dogfood-owner-bootstrap.yml`

Historical/manual dogfood identity preparation. It is not ordinary core CI. The minimum standalone IAM bootstrap is owned by #1304 and #1306.

## Historical retirement

### `main-promotion-gate.yml`

Superseded by protected exact-head checks on PR #1413. It must be disabled and deleted after the current mainline convergence, rather than preserved as a parallel Candidate/dogfood promotion authority.

## Root Gradle task transition

Required current foundation commands:

```text
coreArchitectureCi
canonicalDataCi
postgresPersistenceCi
protocolFacadeFoundationCi
mcpFoundationCi
coreDocsCheck
coreCheck
generatedApiCi
```

Future required commands are introduced only when their tests exist:

```text
protocolFacadeCi
providerConnectorCi
mcpFilesCalendarCi
coreSystemE2e
```

Historical root `ci`, release, dogfood, screenshot, TestFlight, human-signoff, Candidate, sprint-evidence, and provider-fixture tasks remain outside `coreCheck`. #1307 removes or rehomes them after the real replacement gates are stable.
