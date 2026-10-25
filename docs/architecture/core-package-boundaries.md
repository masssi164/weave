# Canonical core package boundaries

Status: implementation dependency-direction guidance for the current #1470 release. The pinned `steering/release-2026-10-product-consolidation.md` profile remains the product authority. Current physical ownership and extraction status are maintained in `server-domain-ownership.md`.

## Purpose

Separate canonical collaboration semantics from northbound protocols, persistence technologies, provider integrations and boot composition. Package names may evolve; dependency direction remains binding.

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

These are responsibilities, not a requirement for a Gradle project per package. A projection calls application contracts. Application code depends on its model and ports. Persistence/provider/infrastructure adapters implement those ports. Boot composition supplies concrete implementations.

Forbidden:

- Domain models depending on Spring, JPA, Jackson, OpenDAL, iCal4j, Matrix/MCP transports or provider SDKs.
- Domain models depending on application or adapter implementations.
- Application code depending on controllers, projections or concrete persistence/provider implementations.
- WebDAV, CalDAV, Matrix or MCP projections directly accessing JPA repositories or provider adapters.
- Provider credentials, provider-shaped DTOs, JPA entities or raw provider errors crossing canonical/member boundaries.
- Core importing optional feature implementations, or a domain importing another domain's persistence.
- Reimplementing business use cases in `weave-native` instead of composing canonical services and adapters.

## Current physical owners

| Responsibility | Current project |
| --- | --- |
| Canonical Files values and ports | `server/domains/files/model` |
| Files JPA types and transactional adapters | `server/domains/files/persistence` |
| Canonical Calendar values | `server/domains/calendar/model` |
| Calendar JPA mappings and repository interfaces | `server/domains/calendar/persistence` |
| Shared framework-free identity/Spaces/operation/binding contracts | `server/core/model` |
| Shared persistence and domain-owned JPA graphs | `server/core` and owning domain `persistence` libraries |
| Agent Runtime provider/security adapters | `server/domains/agent-runtime/provider`, `server/domains/agent-runtime/security` |
| Serving composition and remaining application-wide platform/admin coordination | `server/app` (shared ownership and selected assembly still transitional) |
| Existing future Boards/Office/DevOps/Agent Runtime/Integrations/Guest/Portability/Decisions/Meetings serving code | Owning `server/domains/<domain>` runtime with module-local imports; incomplete implementations remain outside intended release |

The remaining executable services are not the final Core design. Classify remaining Identity/Security/Spaces/audit and application-wide administration separately from owning optional domains; shared workflows consume narrow contributions rather than concrete feature services. No future feature is deleted merely to make Core appear small.

## Domain integration

Files: generated User API → authorized Files use case → canonical Files persistence/storage ports → Files-owned JPA and storage/provider adapters.

Calendar: generated User API → authorized Calendar use case → persistence/provider ports. CalDAV and iCalendar remain southbound implementation details; temporal and recurrence semantics remain provider-neutral.

Chat: Weave Matrix Client-Server projection → canonical Chat application → `ChatProviderPort`. Matrix room/device/crypto state belongs to Chat. Shared member/session revocation remains available independently of Chat.

## MCP boundary

`weave-mcp-server` uses generated User HTTP contracts for Files/Calendar with its distinct workload identity and current resource/member authorization. It owns no Server implementation, DataSource, JPA repository, Flyway catalog, provider adapter or second business authority. Chat uses Matrix, not an MCP projection. Administrative E2E setup uses the separate generated Admin client.

## Verification and cleanup discipline

`./gradlew coreArchitectureCi` includes the extracted Files and Calendar library checks; `:server:domains:check` schedules all resolved owning domain checks plus existing core/MCP boundaries. The domain tests inspect their compiled implementation; the Server relational inventory also includes nested domain source roots. Full PostgreSQL and actual runtime qualification are separate requirements.

A move removes the old physical copy in the same commit. A removed abstraction needs an identified replacement or an established absence of consumers. Keep removal rationale in the domain README/commit, not commented-out code. Do not weaken a guard to bypass a moved source; update the guard's actual ownership inputs.
