# Workflow disposition

Status: active transition inventory for issue #1307.

Every GitHub Actions workflow has one current disposition. This document describes whether a workflow proves the data-sovereignty core, supports a temporary transition, is manual release/client work, or must be retired. The classification does not make a historical workflow architecture authority.

## Required core

### `ci.yml`

Runs the focused Java/Server/Data/MCP foundation gates. It no longer installs Flutter, Node, MkDocs, the external specification corpus, marketing tooling, or release-evidence dependencies.

The temporary `Gradle CI` compatibility context succeeds only when the real architecture, canonical data, PostgreSQL persistence, Server regression, MCP foundation, and documentation jobs succeed.

The `Release Notes Label Check` context remains temporarily because current branch protection expects it. It is not part of `coreCheck` and must be removed after branch protection is migrated to the real core contexts.

## Transitional optional core

### `native-persistence-closure.yml`

Retain temporarily as an additional PostgreSQL/native-persistence regression lane while #1320 moves all schema and repository contracts under `postgresPersistenceCi`. Delete or reduce it after parity.

### `native-provider-gate.yml`

Retain temporarily as an additional native-composition regression lane while #1326/#1301/#1302 remove duplicate provider-shaped application implementations. Its name is historical and must not define the target architecture.

### `live-stack-e2e.yml`

Retain temporarily as an extra disposable-stack regression. It is not the final data-sovereignty E2E and cannot close #1412. Replace it with `coreSystemE2e` after the seven-phase scenario exists.

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
```

Future required commands are introduced only when their tests exist:

```text
protocolFacadeCi
providerConnectorCi
mcpFilesCalendarCi
coreSystemE2e
```

Historical root `ci`, release, dogfood, screenshot, TestFlight, human-signoff, Candidate, sprint-evidence, and provider-fixture tasks remain outside `coreCheck`. #1307 removes or rehomes them after the real replacement gates are stable.
