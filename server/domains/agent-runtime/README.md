# Agent Runtime libraries

See [Server ownership](../../../docs/architecture/server-domain-ownership.md) for the living
extraction plan and actual runtime limitations.

The former root application-core and generic JPA/provider/security projects are split by
actual ownership. Models remain framework-free; persistence owns mappings and repositories.
Existing behavior and architecture tests move with their owners. No legacy source-set paths
or duplicate classes are retained. Automatic release isolation remains open until runtime
services/controllers and selected App assembly are completed.

The client-secret Keycloak admin token provider was a misplaced Identity operator dependency:
its only production consumer is the guarded realm-migration CLI. It and its direct tests now
belong to Core Identity migration, preserving explicit recovery behavior while removing a
future-domain prerequisite from shared identity recovery. Agent Runtime retains its actual
workload/provider implementations; no migration runs through auto-configuration.
