# Generated API CI implementation contract

Status: implementation evidence for #1472, #1473 and #1480 under #1470.
Product authority: the pinned corpus `steering/release-2026-10-product-consolidation.md`.
This file defines repository execution and evidence, not a second API specification.

## Owner and environment

The Weave monorepo owns the `Generated API consumers` job in `.github/workflows/ci.yml`
and the `generatedApiCi` Gradle task. It runs on each candidate in an ephemeral Linux
GitHub runner with Java 21, Flutter 3.41.6, Node 24.18.0, the committed dependency locks,
and OpenAPI Generator 7.17.0. The local command uses the same task and dependency locks.
No Home-core, dogfood, production service or deployment credential is involved.

## Required proof

1. Boot the actual server controller/DTO/validation configuration in the isolated
   `openApiContractExport` test context and export separate User/Admin artifacts.
   Compare the deterministic exports with the checked-in documents. Explicit operation
   IDs, partitioning, ordered examples, errors, headers and schema semantics remain checked.
   Files HTTP probes assert support-safe `ApiErrorResponse` failures before service/provider
   access: invalid folder DTOs and missing required upload query parameters return 400;
   an unsupported upload media type returns 415. A raced absent-name folder precondition
   remains a 412. Their generated responses must match these actual server semantics.
2. Regenerate the JVM User and Admin clients twice, compare sources and compile them.
   Run the User binary transport tests and compile/test product E2E against these modules.
   Retain the existing MCP security/transport regression alongside those checks.
3. Regenerate the Dart User HTTP SDK and transport projection and the Admin TypeScript
   HTTP SDK/types from the checked server artifacts; fail on any stale checked-in output.
   Run the existing Flutter and Admin consumer gates, including independent response,
   binary-body, partial-update, authorization-state and error assertions.
   Freshness checks leave checked-in outputs unchanged and fail when generation or
   formatting fails, even if the previous output would otherwise compare equal.
4. Require this real job in the existing protected `Gradle CI` aggregate alongside all
   current foundation jobs. A skipped, failed or cancelled required job must not pass
   the aggregate. No branch-protection check is removed or replaced with unconditional success.

The seven hosted foundation jobs are scheduled in a dependency chain on the same
candidate: architecture, canonical data, PostgreSQL persistence, server
protocol, MCP, documentation, then generated API consumers. This limits one Core CI
foundation job to a hosted runner at a time. GitHub cancelled otherwise healthy
parallel jobs before their first step after repeated runner-acquisition attempts;
those cancellations are not accepted as test evidence. Each job keeps its real
command, timeout and named check. The protected `Gradle CI` aggregate still waits
for and requires all seven successful results, including generated consumers.

## Native protocol boundary

OpenAPI metadata export substitutes only the Matrix protocol collaborator in its test
context and must not invoke Cargo or load the JNI library. Matrix routes are a separate
northbound contract; metadata export is not Matrix execution evidence. Normal Server
startup/build and Matrix protocol tests retain the required Rust/Ruma/JNI library and
fail closed when it is absent. No production switch or Java protocol fallback is added.

## Services, data and cleanup

The export uses the existing isolated Spring test database and test authentication
collaborators; only documentation endpoints are requested. No provider payload or real
account is created. JVM/Flutter/Admin transport fixtures remain local to their test
processes. Build/codegen output is limited to repository build folders or temporary
directories, and the generators clean their temporary directories. Git freshness checks
reject changed generated sources. The runner is discarded after the job; it owns no
persistent Docker volumes or live resources.

## Failure evidence and acceptance limit

The job preserves the raw generated OpenAPI documents and the export test report on
failure. These contain public transport metadata and fixture assertions, not bearer
tokens or private provider data. Tool and dependency versions are in setup/build logs.

Passing this gate proves deterministic code-first generation and the current generated
consumers on one source candidate. MCP Files uses the generated JVM User module and the
current member/context authorization bridge described in `mcp-generated-user-files.md`.
Generated freshness alone does not prove workload authorization or real file retrieval.
Real browser/OIDC, User/Admin/MCP, Matrix interoperability,
Files/Calendar and session recovery journeys remain independently required by #1480.
The disposable Compose lane provides runtime evidence; provider migration remains #1498.

## Full Compose Files collaboration proof

The current-scope product journey creates, reads, updates and re-reads a member-owned
File through the generated JVM User Files client. The update uses the current strong
content ETag, and the separate item revision must advance. A different organization
member without an explicit File grant and an outside-organization member must not
read or write it. The first-pass File remains readable through the same product API
after the isolated Server/PostgreSQL restart. The suite independently compares exact
bytes and revision changes instead of treating generated models as the behavioral
oracle. Its only cleanup requirement is teardown of the disposable Compose namespace
and exact volumes because the User Files API does not yet offer deletion or sharing.
The native Files adapter may advertise identity-bound conditional content update only
when its metadata authority atomically compares the same organization, Space, object
ID, path and strong provider version before activating replacement bytes. A stale or
replaced object fails without changing the active mapping or serving changed content.

The product acceptance evidence must report the generated User Files northbound
surface. Public WebDAV/CalDAV probes may remain in separately named compatibility
regressions, but the current-scope Full Compose product flow must not require them or
report them as its product northbound contract. Matrix remains the explicit protocol
exception and has its own independent-client qualification gate.
Member Home may project a completed User Files write from the support-safe audit
envelope only for its actor while User Files objects have owner-only access. It must
not expose that object's activity to an ungranted member of the same organization.
The activity projection requires a completed result from the User HTTP source; an
attempted, ambiguous or generic intent record is not proof of a completed write.
The Full Compose journey asserts the author's activity and the absence of that
activity from the collaborator and outsider Home views.

## Member diagnostic boundary

Flutter's member session consumes User capabilities even when the member has an owner
or admin role. Provider registry diagnostics belong to the separately authenticated Admin
surface. Retire the historical `/api/providers/status` call, its Riverpod subscription and
provider-stack rendering from Flutter settings; do not substitute an Admin call using the
User bearer. Workspace health retains member-safe capability/readiness and recovery UI.
Existing provider-coverage widget expectations are superseded by this release boundary;
replacement tests assert visible capability states and absence of provider configuration
for both members and administrators using the member app. Server/Admin tests retain
provider redaction and administrative authorization evidence.

The transitional Dart model projection also reads only the User artifact. The combined
documentation export must not reintroduce Admin transport models into Flutter. Remove
unused provider-registry mappers with that projection; preserve domain mappers still
used by current User operations. Generator tests assert each consumer's artifact owner.
