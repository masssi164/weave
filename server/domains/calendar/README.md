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
composes it; App excludes its namespace from transitional scanning to prevent duplicate
registration. The future `CalendarMeetingsDomainFacadeService` remains outside release
Calendar with its existing interoperability spine; it is not a reverse dependency.

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
requires configured live credentials. The existing HTTP PR's `CalDavHttpConfig` remains
provider-owned, preserving connection timeouts and specialized REPORT/PROPFIND behavior.
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
./gradlew :server:app:test checkOpenApiContractFresh -Pdomains=all
DOCKER_HOST=unix:///Users/flotterotter/.orbstack/run/docker.sock ./gradlew :server:app:postgresJpaTest -Pdomains=all
```

The owning checks run independent provider/parser/mapper/recurrence/readiness/bootstrap/
security units and genuine published-imports discovery, plus model/persistence and shared
Core prerequisites. The source-sensitive App policy/MVC and actual PostgreSQL suites remain
in their existing integrated harness; all assertions are retained. No owning check compiles
tests against App. Calendar-only imports discovery proves one native provider, alternate
CalDAV construction, actual HTTP mappings, singleton beans, absent other domain classes,
and non-web separation. Full App/API checks remain required for the assembled product.

Calendar's resolver entry now schedules genuine owning tasks rather than the old filtered
App verification bridge. Partial App packaging/API/schema composition remains explicitly
guarded pending actual selected assembly and accepted contracts. An independently buildable
Calendar runtime is not a claim of a qualified Calendar-only serving executable.

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
