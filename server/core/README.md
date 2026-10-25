# Core platform ownership

Core is a reusable library, never an executable. It owns capabilities that remain necessary
when Files, Calendar or Chat is absent: organization-bound human identity, shared authorization
policy, durable member/session revocation, foundational Spaces, audit and operation/binding
persistence. Domain business entities, controllers, providers and SDKs belong to their domains.

`model` contains framework-free identity values, Space authorization contracts/rules and
shared operation/binding ports. The runtime library owns responsibility-scoped JPA mappings,
Identity resolution, Security settings/session services and the shared provider-health cache.
Core Discovery owns generic provider status contracts; Core Provider Binding owns pure
conformance/readiness values. Redaction and HTTP retry parsing have their own responsibilities.
Its `api.error` exception is the common HTTP error contract; health's safe response lives with
that responsibility. Pure models do not depend on either transport contract.

Each actual configuration is registered in this module's Boot `AutoConfiguration.imports`.
Persistence is available to serving, schema and operator contexts. Security/Identity serving
services and provider health are servlet-only. Their explicit beans consume required shared
contracts; health consumes deferred domain probes and does not contact providers during
construction. No domain component scan or optional feature implementation is required.

Release MCP authorization depends on protected member bindings, exact exchanged-token shape
and current Keycloak entitlement. Its narrow authorization contract is shared; future runtime
cells/profiles/governance remain outside Core. Existing configuration keys/defaults retain
their reviewed values, including historical `weave.agent-runtime` prefixes. The release
binding-file setting selects the existing release authorization path; it does not choose an
organization's provider. Construction defers binding/secret/provider access until required.

Shared Audit owns the cross-domain envelope/redaction/publication contracts and durable JPA
sink. Connector and Consent event construction belongs to future Integrations. Serving sink
wiring is module-local; non-web preparation composes its explicit audit sink.

Core also owns durable operation intents/outbox and provider-binding/selection storage.
Serving wiring discovers the actual adapters through module-local imports. Domain-owned
transition policies preserve each feature's identity/reconciliation requirements; Core
rejects unknown or duplicate policy ownership rather than choosing a provider for a domain.
The pure selection contract is in `providerbinding.selection`, with JPA infrastructure in
`providerbinding.persistence`. Domain bootstrap and migration workflows remain outside Core.

Shared revocation retains the existing durable table and hashed key format, permanent member
cutoffs, session expiry retention, transaction/retry boundaries and fail-closed behavior.
The Keycloak organization claim parser and tenant precedence have one owner; product domains
consume the resolved identity and shared policy, not a new permission authority.

`src/testFixtures` owns the common Keycloak human-token fixture used by Core and App tests.
It is test-only and is absent from the production artifact. Module tests cover actual Boot
imports discovery, singleton beans and non-web isolation; policy/session/identity unit tests
run without the complete application or optional domains.

From the repository root:

```sh
./gradlew :server:core:check :server:core:model:check
./gradlew coreArchitectureCi serverCi
```

PostgreSQL behavior uses the existing isolated App PostgreSQL lane until the owning integration
harnesses have been extracted. Current remaining runtime extraction and its single execution
plan are documented in `docs/architecture/server-domain-ownership.md`. No reduced-runtime
or complete schema cutover qualification is implied by these library checks.
