# Calendar domain

Calendar owns authenticated event/calendar operations, bounded recurrence projection, native
JPA persistence, CalDAV integration, generated User API handlers and the existing Calendar
setup/DAV compatibility surfaces. It has no production or test dependency on App, Files,
Chat or future integration implementations.

## Ownership and dependency direction

- `:server:domains:calendar:model` owns provider-neutral values and exact DATE, FLOATING,
  UTC and ZONED semantics. It remains Java-only, without Core, Spring, JPA or iCal4j.
- `:server:domains:calendar:persistence` owns native/normalized entity mappings, scoped IDs,
  repositories, bounded change-journal queries and `NativeCalendarRelationalStore`.
- `:server:domains:calendar` owns the actual runtime. `calendar.api` contains wire records
  and thin controllers; `api.dav` owns the existing northbound DAV parser/transport values.
  `application` owns authorization-aware use cases, binding readiness, preview leases and
  initial binding orchestration. `domain` owns recurrence projection and neutral errors/
  values; `port` contains the provider/codec contracts. `provider.caldav` and
  `provider.icalendar` own remote DAV and pinned iCal4j integration. The native JPA provider
  adapter belongs to `calendar.persistence`, alongside its mapping package. `config`,
  `capability` and `spaces` contain typed wiring and owning shared-policy contributions.

The runtime depends on Core policy/security/identity/Spaces/audit/binding contracts and its
own model/persistence libraries. Native adapter and mapped types share the owning business
package across these projects, retaining non-public mapping APIs without exposing entities
to another domain. No domain depends on the executable. App depends on this runtime and
composes it; App scans only its own composition/entry-point packages, leaving Calendar
registration to this module. Meeting Capsule behavior belongs to the future Meetings runtime,
outside release Calendar; Calendar does not depend on that implementation.

The legacy event facade now delegates client setup/device credentials/profile metadata to
`CalendarClientSetupService` and scope-bound DAV change synchronization to `CalendarDavSyncService`.
`CalendarFacadeAuthorization` supplies the same organization, actor, capability and Space checks
for these use cases. Dependencies are constructor-injected; an installed runtime requires its real
provider. The event facade retains event orchestration, required audit-before-write ordering,
provider ETag handling and support-safe error translation. DAV serialization finishes before a
wrapped sync token is issued, preserving the previous failure/state boundary.

## Registration and configuration

The module-local `AutoConfiguration.imports` registers actual servlet-only
`CalendarRuntimeAutoConfiguration`, after the owning persistence registration. Explicit
imports install the real handlers, services, normalized store, native/CalDAV adapters and
existing health, capability, binding, device-scope and Space contributions. Non-web schema
and operator contexts register the mapped graph without constructing serving Calendar
providers, services or handlers.

The existing `weave.calendar.provider` native/CalDAV construction selector is retained as
finite tracked startup-selection debt; this move does not claim a new provider-migration
protocol. Organization routing and captured revisions remain in the persisted binding
and existing readiness authority. CalDAV construction neither contacts a provider nor
requires configured live credentials. The adapter directly constructs its JDK HTTP client
with the existing connection timeout; the one-constructor `CalDavHttpConfig` wrapper was
removed by #1732. Specialized REPORT/PROPFIND behavior remains provider-owned.
All configuration keys/defaults remain unchanged, and diagnostic properties output redacts
provider addresses and credentials. Bootstrap business validation lives in the owning
application runner; its configuration only binds settings and constructs the runner.

## Persistence and temporal behavior

JPA mappings own ORM-expressible integrity. Native payload CRUD and candidate selection
use JPA with scoped identities, and ranged queries select database candidates before
loading/enriching events. Snapshot change reads retain their database predicates/order.
The move changes no mapping/table/column, uniqueness, version, lock, null, transaction,
idempotency or recurrence semantics. Remote writes retain their existing local/remote
failure boundaries. No migration history, owner data or persistent schema was reset.

DATE candidates use the ceiling calendar day of an exclusive query end (an exact midnight remains
exclusive). FLOATING and ZONED candidates use a conservative local-clock envelope derived from
`ZoneOffset.MIN`/`MAX`: any resolved event offset is within ±18 hours, so an instant overlap cannot
fall outside that envelope. This also handles query wall clocks reversing across a DST overlap.
The database still filters calendar, scope, deletion and bounded temporal candidates; the occurrence
engine then applies each canonical temporal kind and its explicit evaluation zone exactly. The
superset may select a nearby non-overlapping event, which the final query/free-busy projection removes.

Selected event rows and their normalized payloads load in batches of 200 scoped IDs, with no result
truncation. Chronological start/ID ordering is retained across batches. The owning PostgreSQL test
loads 205 matching events plus an unrelated event, checks all results and exact loaded entities,
and bounds query/free-busy statement counts; this replaces the previous per-event enrichment reads.

Pure model/library checks, isolated persistence checks, actual imports discovery and
PostgreSQL integration prove different things. H2 graph discovery is not PostgreSQL
integrity qualification. The legacy initializer remains transitional pending the agreed
Entity-first cutover; schema export remains a separate non-serving task.

## Verification

`test` runs direct unit cases; `contextTest` runs the tagged application-context cases
with separate reports. Owning `check` requires both through the shared library mechanics.

From repository root:

```sh
./gradlew :server:domains:calendar:check
./gradlew :server:domains:check -Pdomains=calendar
DOCKER_HOST=<observed-disposable-engine> ./gradlew :server:domains:calendar:postgresTest -Pdomains=calendar
./gradlew :server:app:test checkOpenApiContractFresh -Pdomains=all
DOCKER_HOST=<observed-disposable-engine> ./gradlew :server:app:postgresJpaTest -Pdomains=all
```

The owning checks run independent provider/parser/mapper/recurrence/readiness/bootstrap/
security units and genuine published-imports discovery, plus model/persistence and shared
Core prerequisites. The source-sensitive App policy/MVC and actual PostgreSQL suites remain
in their existing integrated harness; all assertions are retained. No owning check compiles
tests against App. Calendar-only imports discovery proves one native provider, alternate
CalDAV construction, actual HTTP mappings, singleton beans, absent other domain classes,
and non-web separation. Full App/API checks remain required for the assembled product.

Calendar's resolver entry now schedules genuine owning tasks rather than the old filtered
App verification bridge. App now composes selected production dependencies and isolated API/schema
exports, with actual library/handler inspection. `selectedRuntimePostgresTest -Pdomains=calendar`
starts the packaged executable on disposable PostgreSQL. See the living ownership plan for final
results and the remaining specification/schema/platform qualification boundary. Selected
bootstrap/navigation uses the generated-consumer packaged PostgreSQL lane; #172 acceptance
remains separate. Client-profile URL binding uses `CalendarClientSetupProperties`, distinct
from southbound CalDAV credentials under the existing configuration prefix.

## Cleanup record

Production `server/app/.../service/calendar`, Calendar controllers/wire records, Calendar
settings/configuration and existing `backend.calendar` runtime sources moved into this
project's business packages. Old App copies were deleted and direct consumers updated;
there are no forwarding controllers, duplicate classes or source-set aliases. The native
and normalized mapping package changed from `calendar.adapter` to `calendar.persistence`;
repository/entity names and database mappings are unchanged. The snapshot repository now
belongs physically to persistence. Independent units moved with their owners; App-coupled
fixtures retain their integration harness at the owning Java package paths. The pinned
iCal4j dependency moved from App to its Calendar consumer. Existing inspection/evidence,
selector-ratchet paths and artifact inputs follow the actual new sources without removing
assertions. The living overall execution plan remains
`docs/architecture/server-domain-ownership.md`.

The plain `CalendarFacadeServiceTest` and `CalendarUserApiServiceTest`, plus the two database-candidate
provider unit cases, moved from App into this runtime with their assertions retained. Their policy
fixture installs the real Calendar declaration and Core authorization only. MVC tests importing App's
assembled security configuration and whole-schema PostgreSQL fixtures remain integration tests in
App; no all-domain test fixture is added to Calendar. The unused implicit-UTC candidate overload was
retired after checking callers; every candidate query now supplies its evaluation zone explicitly.
