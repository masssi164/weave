# Core platform ownership

Core is a reusable library, never an executable. It owns capabilities that remain necessary
when Files, Calendar or Chat is absent: organization-bound human identity, shared authorization
policy, durable member/session revocation, foundational Spaces, audit and operation/binding
persistence. Domain business entities, controllers, providers and SDKs belong to their domains.

`model` contains framework-free identity values, Space authorization contracts/rules and
shared operation/binding ports. The runtime library owns responsibility-scoped JPA mappings,
Identity resolution, Security settings/session services and the shared provider-health cache.
Its `api.error` exception is the common HTTP error contract; health's safe response lives with
that responsibility. Pure models do not depend on either transport contract.

Each actual configuration is registered in this module's Boot `AutoConfiguration.imports`.
Persistence is available to serving, schema and operator contexts. Security/Identity serving
services and provider health are servlet-only. Their explicit beans consume required shared
contracts; health consumes deferred domain probes and does not contact providers during
construction. No domain component scan or optional feature implementation is required.

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
