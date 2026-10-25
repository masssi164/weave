# Portability libraries

See [Server ownership](../../../docs/architecture/server-domain-ownership.md) for the living
extraction plan and actual runtime limitations.

The former root application-core and generic JPA/provider/security projects are split by
actual ownership. Models remain framework-free; persistence owns mappings and repositories.
Existing behavior and architecture tests move with their owners. No legacy source-set paths
or duplicate classes are retained. Automatic release isolation remains open until runtime
services/controllers and selected App assembly are completed.
