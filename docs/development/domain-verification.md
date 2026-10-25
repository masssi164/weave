# Selected domain JVM verification

The canonical domain catalog and `tools/domain_selection_resolver.py` supply one selection model
for owning domain checks and CI scheduling. Files, Calendar and Chat run their actual libraries;
implemented future Decisions and Meetings have separate owners. This check does not make App
packaging domain-selectable or qualify live product behavior.

## Commands

From the repository root:

```sh
./gradlew :server:domains:check
./gradlew :server:domains:check -Pdomains=all
./gradlew :server:domains:check -Pdomains=files,chat
./gradlew :server:domains:test -Pdomains=calendar
./gradlew :server:domains:check -Pdomains=chat,decisions
```

The aggregate depends on the selected owning libraries' checks and shared prerequisites. Each real
library check runs its unit and separately reported context lane. The unit alias runs selected unit
tasks. Model libraries remain framework-free. PostgreSQL, complete App/API, live provider and product
acceptance lanes remain separate. Chat units exercise the pinned Rust/Ruma JNI bridge; selecting
Files or Calendar alone does not schedule that Chat build.

Use fully qualified task paths. Root `./gradlew check` selects matching tasks across the
multiproject build and is not a selected-domain-only command. Preserve argument boundaries in helpers:

```sh
check_domains() {
  ./gradlew :server:domains:check "$@"
}
check_domains -Pdomains=files,chat --rerun-tasks
```

## Selection and required dependencies

1. An explicit `-Pdomains` or workflow `domains` input wins, including `all`.
2. Otherwise the actual configured source branch supplies its valid pin.
3. Without a matching pin, the default is `all`.

`all` expands the canonical selectable catalog. Ordering and duplicates are normalized. Unknown
names, malformed selections, invalid matching pins and cyclic catalog dependencies fail clearly.
Workflow `domains` has no literal `all` default, so omitted input remains distinct from an override.

Required dependencies are explicit. Decisions and Meetings require Chat; a request for only
`decisions` fails instead of silently adding Chat. Select `chat,decisions` for a valid future-domain
check. Build selection does not choose an organization's runtime provider.

The resolver reads the checked-out symbolic branch locally. Detached CI checkouts supply a trusted
source branch only for a `refs/heads/` source ref, paired with the exact checked-out commit. A PR
merge ref or its `dev` target does not inherit the source-branch pin. Matrix planning uses this same
resolver once; workflow YAML does not reimplement selection or dependency closure.

## GitHub CI

[Selected domain JVM checks](https://github.com/masssi164/weave/blob/dev/.github/workflows/domain-unit-checks.yml)
accepts optional `domains` and `iteration` inputs through dispatch and reusable calls:

- `iteration=selected` is the default and runs one aggregate job for the resolved selection.
- `iteration=individual` creates one job per selected domain. Each row explicitly includes that
  domain's transitive required dependencies, all of which must already belong to the valid selection.
  For `files,chat,decisions`, rows are `files`, `chat` and `chat,decisions`.

The CLI exposes the same matrix for inspection:

```sh
python3 tools/domain_selection_resolver.py --domains files,chat,decisions --format matrix --iteration individual
```

Every row invokes the real root `:server:domains:check` with one quoted Gradle property. Reports
include actual owning unit and context XML/HTML paths, with separate artifact names identifying
the row, its domain set, source commit, run and attempt. Independent rows continue if another fails.

This read-only workflow uses the existing Linux ARM64 runner and Java 21/Python setup. Rows containing
Chat install the pinned Rust 1.95.0 toolchain for their actual JNI checks. Cargo manifests, the lockfile,
Rust toolchain declaration and Matrix protocol sources trigger the workflow alongside Server inputs.
There is no publication or deployment permission and no claim of a hosted run from local shell tests.

## Current runtime boundary

Owning domain checks no longer use an App-filtered unit bridge. App packaging and API/schema exports
still reject partial selections until actual selected runtime composition and required contract
approval are complete. A green selected check does not prove reduced executable contents, omitted
runtime endpoints, cryptographic acceptance, GraalVM native qualification or live E2E readiness.
