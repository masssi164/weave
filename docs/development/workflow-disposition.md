# Workflow disposition

Status: historical #1307 workflow inventory. Current required checks and protected
lane rules are defined by [Gitflow PR workflow](../gitflow-pr-workflow.md),
[Core development workflow](core-workflow.md), and the actual workflow files.
The old classifications below do not waive #1480's generated-client, Flutter,
Matrix, user/admin/MCP, and integrated E2E evidence. Physical-device preparation
is an active #1475/#1480 validation path, not a retired client concern; provider
migration and rollback are #1498 evidence.

Except for the dated retirement records below, this document preserves the historical #1307 classifications and their provenance. They are not current instructions to select checks, remove workflows, or promote branches. Use [Current workflow ownership](current-workflow-ownership.md), the pinned delivery specification and actual protected-check configuration for current decisions. Dated retirement records document completed replacement evidence; they do not create another delivery authority.

## Historical required-core classification

### `ci.yml` (historical classification)

The following describes the former combined consumer chain and unconditional foundation aggregate. Current Core CI separates export freshness from independently selected consumers and validates its complete impact plan; this historical description does not require the old combined job or forbid valid unselected skips.

Runs the focused Java/Server/Data/MCP foundation gates and the `Generated API consumers`
job. The latter uses pinned Flutter and Node tooling to regenerate the code-first User/Admin
contracts and all JVM, Dart and TypeScript consumers and run their actual consumer gates.
The ordinary server export uses a test-only Matrix protocol collaborator; it does not build
or load JNI. The separate Server protocol foundation job retains the real native runtime.
See [the generated API CI implementation contract](https://github.com/masssi164/weave/blob/dev/specs/generated-api-ci-contract.md)
for inputs, commands, isolation, cleanup and failure evidence.

The `Gradle CI` aggregate succeeds only when the real architecture, canonical data,
PostgreSQL persistence, Server regression, MCP foundation, documentation and generated API
consumer jobs succeed. A cancelled or skipped required job fails the aggregate.

The `Release Notes Label Check` remains required and is owned solely by `release-notes-label.yml`. Core CI does not subscribe to label events and always emits the constant `Gradle CI` aggregate. This repairs a verified Dogfood promotion failure: PR #1568 had a successful `Gradle CI` in check suite `103341680838` on commit `93e2bbd7fbd7d13838ca4f979df38b18bfe78182`, but a later label-only Core suite `103341681909` emitted a literal unevaluated dynamic aggregate name and omitted `Gradle CI`. The protected REST merge returned HTTP 405 with “Required status check Gradle CI expected” despite the completed full run. Separating event ownership preserves the existing validator and required-check identities without protection changes or a successful substitute for skipped verification. The label workflow retains the previous PR-only validation condition for merge-group events.

## Dated retirement records and historical optional-core classification

### `native-persistence-closure.yml`

Retired after the strengthened Core documentation source guard, Server protocol foundation and PostgreSQL persistence checks passed in [Core CI run 38129656146](https://github.com/masssi164/weave/actions/runs/38129656146) for exact commit `c0c6a95dd13bdfb222e16a49412021e5e736ab9c`. Its selected server and PostgreSQL tests are covered by `protocolFacadeFoundationCi` and `postgresPersistenceCi`. Migration source rules execute fail closed in Core documentation: historical migrations must exist, SQL/schema authority must remain scoped, unsafe Flyway/production DDL settings are rejected, and snapshot removal and Hibernate validation are positive requirements. This corrects intermediate shell negation and ignored positive-check semantics in the former supplemental lane. The retired workflow was absent from actual Dev, Dogfood and Main required-check lists; branch protection was not modified. Historical evidence links retain their provenance.

### `native-provider-gate.yml`

Retired after the replacement checks passed in [Core CI run 38128850305](https://github.com/masssi164/weave/actions/runs/38128850305) for exact commit `1a9da0b38a8db8be67f34c9b82da613b653ad13e`: Server protocol foundation runs the former focused server tests, Rust module verification runs both former Cargo suites plus vendor provenance, and Core documentation runs the inherited provider boundary invariants on every ordinary PR. The former workflow was supplemental and absent from the actual Dev, Dogfood and Main required-check lists; branch protection was not modified. Historical evidence links remain valid. Its earlier #1326/#1301/#1302 transition purpose does not define the target architecture.

### `live-stack-e2e.yml` (historical classification)

This records the former coverage assessment, not the current mandatory Dogfood qualification catalog.

Provides isolated real-runtime regression, including generated User Files calls and
browser/OIDC member access. Its historical DAV/native collaboration portions do not prove
all revised #1480 journeys. Single-sign-in Flutter, generated Calendar and Weave-owned
Flutter/Weaver Matrix clients still require their own runtime evidence. Independent
third-party Matrix-client interoperability is deferred by the current support profile. Provider migration
and cutover proof belongs to #1498.

## Historical manual-release/client classification

### `ios-dogfood.yml` (historical classification)

The removal recommendation below is superseded. Physical preparation is an active exact-candidate HIL prerequisite in the pinned delivery contract.

Manual/future client distribution only. It must not run as a prerequisite for Server/Data/MCP changes or mainline convergence. Remove or move to a later release lane after the core workflow transition is complete.

### `dogfood-owner-bootstrap.yml` (historical classification)

Historical/manual dogfood identity preparation. It is not ordinary core CI. The minimum standalone IAM bootstrap is owned by #1304 and #1306.

## Historical retirement

### `main-promotion-gate.yml` (historical classification)

The deletion recommendation below is superseded. The active Main gate verifies existing exact-candidate automated, deployment, preparation and human evidence.

Superseded by protected exact-head checks on PR #1413. It must be disabled and deleted after the current mainline convergence, rather than preserved as a parallel Candidate/dogfood promotion authority.

## Historical root Gradle task transition

The former task plan below is provenance only; supported current gates are documented in the developer handbook and active Core workflow.

Former required foundation commands:

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

The historical plan proposed these future commands only when their tests existed:

```text
protocolFacadeCi
providerConnectorCi
mcpFilesCalendarCi
coreSystemE2e
```

Historical root `ci`, release, dogfood, screenshot, TestFlight, human-signoff, Candidate, sprint-evidence, and provider-fixture tasks remain outside `coreCheck`. #1307 removes or rehomes them after the real replacement gates are stable.
