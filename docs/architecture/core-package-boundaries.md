# Canonical core package boundaries

Status: dependency-direction guidance for the current #1470 release. The pinned
`steering/release-2026-10-product-consolidation.md` profile supersedes the
historical #1024/#1299 transport and delivery targets in this document.

## Purpose

Weave separates canonical collaboration semantics from northbound protocols, persistence technologies, provider integrations and boot composition. Package names may evolve, but dependency direction is binding and is enforced incrementally by executable architecture tests.

## Target shape per collaboration domain

```text
<domain>/domain
<domain>/application
<domain>/port/inbound
<domain>/port/persistence
<domain>/port/provider
<domain>/projection/<standard>
<domain>/adapter/persistence/jpa
<domain>/adapter/provider/<provider>
<domain>/adapter/infrastructure/<technology>
<domain>/boot
```

Linear equivalent: a northbound projection calls an inbound/application contract. Application code owns orchestration and depends on the domain plus abstract ports. Persistence, provider and infrastructure adapters implement those ports. Boot code is the only place that composes concrete implementations.

## Binding dependency direction

Allowed direction:

```text
projection -> inbound/application -> application -> domain
                                      |           |
                                      +-> persistence port
                                      +-> provider port

persistence adapter -> persistence port + canonical values
provider adapter    -> provider port + canonical values
infrastructure      -> narrow technical port
boot                -> concrete implementations for composition only
```

The following are forbidden:

- domain code depending on Spring, Jakarta Persistence, Jackson, OpenDAL, iCal4j, Matrix/MCP transports, controllers, projections, persistence implementations or provider SDKs;
- domain code depending on application or port packages;
- application code depending on controller, projection, persistence or adapter implementations;
- WebDAV, CalDAV, Matrix or MCP code calling JPA repositories or provider adapters directly;
- JPA entities, provider DTOs, provider IDs, provider URLs, credentials or raw provider errors entering canonical or northbound contracts;
- `weave-native` implementing a second set of business use cases instead of composing canonical application services with persistence adapters.

## Current transition map

The repository is intentionally being migrated in vertical slices rather than through a repository-wide package rename.

### Canonical modules already present

- `weave-application-core`: framework-free domain/application/port code. Calendar canonical domain currently lives here. Historical Agent Runtime code remains present but is not part of the Files/Calendar/Chat target core.
- `weave-files-core`: framework-free canonical Files domain.
- `weave-persistence-jpa`: JPA/Flyway persistence implementations.
- `weave-runtime-provider-adapters`: southbound provider implementations.
- `weave-runtime-security-adapters`: security/IAM infrastructure adapters.
- `weave-mcp-server`: separate MCP process; it must not depend on persistence or provider modules.
- `server`: protocol projections, composition and transitional implementations while vertical slices are extracted.

### Transitional server packages

Packages such as `server/.../controller`, `domainfacade`, broad provider packages
and domain-local mixed adapter packages are transitional. New canonical behavior
must not be added there when the target domain/application/port boundary exists.
Reconcile removal or decomposition against current #1470/#1498 stories rather
than treating the older #1326/#1301/#1302/#1019 delivery order as authority.

## Reference vertical: Files

For an optional native Files provider, the reference path is:

```text
Generated Files User API operations
  -> Files application service
    -> canonical Files repository port
    -> canonical BlobStore port
      -> JPA Files persistence adapter
      -> OpenDAL blob infrastructure adapter
```

A provider source/target connector is a sibling southbound adapter. It is never selected or called by WebDAV directly.

## Calendar and Chat

After the Files reference slice is green, Calendar and Chat follow the same dependency shape:

- Calendar: generated User API operations -> canonical Calendar application -> persistence/provider ports. CalDAV/iCalendar may be used southbound.
- Chat: Matrix Client-Server projection -> canonical Chat application -> `ChatProviderPort` -> the active southbound provider. The facade is not a second Chat ledger.

Chat has no MCP projection. Weaver/OpenClaw uses Matrix for conversational traffic.

## MCP boundary

`weave-mcp-server` is a curated semantic tool surface for Files and Calendar. It
must use the same generated JVM User HTTP client and transport models as member
product E2E flows to reach Weave Server, with distinct workload identity and
current member and resource authorization. Administrative E2E setup uses the
separate generated Admin client. It owns no DataSource, JPA repository, Flyway
migration, provider adapter, BlobStore or canonical business authority.

## Enforcement

`./gradlew coreArchitectureCi` is the stable root architecture entry point. The gate begins with the existing canonical application, Files and MCP modules and expands as Calendar, Chat and protocol projections are moved behind explicit boundaries.

A migration PR may temporarily retain a compatibility package only when the owning issue states the replacement and removal condition. A new reverse dependency is never accepted as migration scaffolding.
