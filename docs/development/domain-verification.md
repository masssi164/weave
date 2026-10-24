# Selected domain JVM verification

This is the first verification-only slice of [#1743](https://github.com/masssi164/weave/issues/1743)
under [#1741](https://github.com/masssi164/weave/issues/1741).
It gives the existing Files, Chat and Calendar JVM unit inventories a common
Gradle entry point while production source extraction proceeds separately.
It does not make the current Server JAR a selectable-domain deployment.

## Commands

From the repository root:

```sh
./gradlew :server:domains:check
./gradlew :server:domains:check -Pdomains=all
./gradlew :server:domains:check -Pdomains=files,chat
./gradlew :server:domains:test -Pdomains=calendar
```

The aggregate depends on the selected domain projects' tasks; it does not run
a shell loop or start a new Gradle build for each domain. The Files checks also
run the existing framework-free Files core tests. Standard Gradle options such
as `--parallel` and `--rerun-tasks` remain available.

Use the fully qualified task path. An unqualified root `./gradlew check` selects
matching tasks across the multiproject build, including the complete Server
check; it is not a selected-domain-only command. A shell helper can preserve
arguments without splitting them:

```sh
check_domains() {
  ./gradlew :server:domains:check "$@"
}
check_domains -Pdomains=files,chat --rerun-tasks
```

## Selection

The shared resolver is `tools/domain_selection_resolver.py`.

1. An explicit `-Pdomains` / workflow `domains` selection wins, including `all`.
2. Otherwise a valid pin for the actual source branch supplies the domains.
3. Without a matching pin, the default is `all`.

`all` expands to the concrete catalog; ordering and duplicates are normalized.
Unknown names, malformed selections and an invalid matching pin fail clearly.
The workflow input has no literal `all` default: an absent input must remain
distinguishable from explicitly overriding the pin with `all`.

The local resolver reads the checked-out symbolic branch. For detached CI
checkouts, the workflow supplies the branch from its source ref together with
the exact checked-out commit. A PR target branch is not the source branch.
The resulting concrete list is forwarded to Gradle; workflow YAML does not
reimplement domain-selection rules.

## GitHub CI

Use [Selected domain JVM checks](https://github.com/masssi164/weave/blob/dev/.github/workflows/domain-unit-checks.yml)
with an optional `domains` input, for example `files,calendar`. It runs on a
GitHub-hosted Linux ARM64 runner and can also be called as a reusable workflow.
PRs changing the domain-selection/test wiring run this focused check automatically.
It has no image-publication or deployment permissions. Manual dispatch becomes
available after the workflow is registered on the repository's default branch.

JUnit XML and HTML reports are uploaded for the selected Server tasks and Files
core when applicable. Existing required Core CI, PostgreSQL, JNI and product
acceptance gates remain authoritative and unchanged.

## Current boundary

The domain projects are currently verification entry points. Existing production
and test source sets are still compiled in the Server and current core projects.
Selection changes which bounded JVM inventories execute, not which production
classes compile or which domains are packaged in an image.

The inventory deliberately excludes suites requiring live PostgreSQL, Docker,
Matrix JNI or Cucumber product journeys; some existing tests do not yet have
consistent tags. It is not a claim that every test of a domain has been classified.
The existing complete gates retain those checks. Each selected unit task must
match real tests; an empty match is not a successful domain check.
Some JVM units use the existing platform-matched OpenDAL native dependency;
this lane does not build Rust Matrix JNI or produce a GraalVM native executable.

Until production extraction is implemented, partial-domain requests are rejected
by a prerequisite of Server executable packaging. Selecting tests must not
silently produce a full Server artifact advertised as a reduced variant.

Production module extraction will replace these references to the old source
layout. It must add real per-domain auto-configuration resources, prove omitted
domain startup/navigation, and align JVM/native artifact identities before a
partial production build can be claimed. No placeholder auto-configuration or
successful unit result substitutes for that work.
