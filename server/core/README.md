# Core platform ownership

Core is a reusable library, never an executable. It owns capabilities that remain necessary
when Files, Calendar or Chat is absent: organization-bound human identity, shared authorization
policy, durable member/session revocation, foundational Spaces, audit and operation/binding
persistence. Domain business entities, controllers, providers and SDKs belong to their domains.

`model` contains framework-free identity values, Space authorization contracts/rules and
shared operation/binding ports. The runtime library owns responsibility-scoped JPA mappings,
Identity resolution, Security settings/session services and the shared provider-health cache.
Core Discovery owns generic provider status contracts, instance category/canonical catalogs and their
HTTP adapters. Domain-owned metadata is installed through each runtime’s imports; duplicate/conflicting
owner, module or alias claims fail explicitly. Joint document categories merge Office and Integrations
fragments without a required domain dependency. Core never recreates absent feature declarations.
Core Provider Binding owns pure
conformance/readiness values. Redaction and HTTP retry parsing have their own responsibilities.
Its `api.error` exception is the common HTTP error contract; health's safe response lives with
that responsibility. Pure models do not depend on either transport contract.

Each actual configuration is registered in this module's Boot `AutoConfiguration.imports`.
Persistence is available to serving, schema and operator contexts. Security/Identity serving
services and provider health are servlet-only. Their explicit beans consume required shared
contracts; health consumes deferred domain probes and does not contact providers during
construction. No domain component scan or optional feature implementation is required.

Core Identity owns member lifecycle administration under `identity.member`: opaque references,
organization-bound listing/role changes, last-owner protection, session revocation and offboarding.
Its protected Weaver/MCP entitlement assignment is shared access policy used independently of
Agent Runtime execution. It reuses the accepted Keycloak organization authority; no separate
permission system or Agent Runtime SDK/runtime is required. Wire values/HTTP adapters belong to
`identity.member.api`; durable side-effect claims live with Identity persistence. Remote Keycloak
writes are never treated as rolled back by a local transaction. Claim/completion transactions
commit independently; a losing unique insert finishes before reading a concurrent winner.

Core Identity owns organization bootstrap under `identity.organization`: framework-free authority
values/port, versioned JPA persistence, the authorization-aware recovery-administrator use case and
its Admin HTTP/wire adapters. Non-web imports expose the real persistence adapter; servlet imports
expose the use case/handler. It needs no Portability evidence store or optional provider runtime.
The retired unused file adapter was a test fixture, never a supported authority fallback.

Core Identity also owns invitation values/ports, transactional adapters and use cases, current
member display-name queries and the shared identity/profile/invitation HTTP adapters. Human
Keycloak administration uses the named HTTP interface registered through Boot
`@ImportHttpServices` and the `keycloak-admin` service group. Its group-scoped configurer
retains the operation allowlist and invitation settings. The protected transport preserves
private-key JWT, exact SecretRefs, timeouts, URL context paths, expected statuses and 401
invalidation. Module-local servlet imports register it once; construction needs no mounted
secret or live provider. Non-web operator tooling imports its required transport explicitly.
Owning Core tests exercise real imports discovery and protected stub transports. Workload
client registration remains a separate narrowly authorized flow. Plain `identity.migration`
operator helpers and their explicit recovery token provider also belong here. App only dispatches
the CLI; module discovery never registers or runs a migration. Backup proof, operation allowlist,
exact SecretRef and receipt guards are unchanged. The recovery provider has no serving fallback
registration; normal serving administration retains private-key JWT authentication.

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

Identity's product profile owns its values/ports, JPA adapter, use cases and transport DTOs
under `identity.profile`. Configured identity resolution is mandatory in production wiring.
The generic HTTP error envelope belongs to `api.error`. Device credential values, hashing,
expiry, revocation and storage belong to Security; installed domains declare the permitted
credential scopes. No installed scope means no issuance. Credential string output is redacted.
The file-profile and in-memory credential fixtures are test-only artifacts.

Foundational Spaces owns its access/membership/provisioning JPA adapters, authorization-aware
administration and shared User/Admin/relationship HTTP adapters under `spaces`. Explicit
exceptional default-Space setup also belongs here: its typed settings admit only reviewed
Dogfood/E2E environments, and module-local imports discover the credential/handler only when
explicitly enabled. Domain preparation remains a list of narrow installed contributions;
Space administration does not import domain implementations. Serving imports construct these actual adapters; non-web Dogfood
preparation imports its own adapter explicitly. Domains implement resource contribution
contracts. Request correlation and shared HTTP security/error adapters also register locally;
Chat owns the Matrix denial envelope behind the narrow protocol error contract. Shared
handlers operate without any installed protocol or optional domain. `api.error.ApiExceptionHandler`
owns validation, support-safe failures and retired-route errors; the strong revocation ETag
exception remains with Spaces.

Shared revocation retains the existing durable table and hashed key format, permanent member
cutoffs, session expiry retention, transaction/retry boundaries and fail-closed behavior.
The Keycloak organization claim parser and tenant precedence have one owner; product domains
consume the resolved identity and shared policy, not a new permission authority.

`src/testFixtures` owns the common Keycloak human-token fixture used by Core and App tests.
It is test-only and is absent from the production artifact. Module tests cover actual Boot
imports discovery, singleton beans and non-web isolation; policy/session/identity unit tests
run without the complete application or optional domains.

`test` runs direct unit cases; `contextTest` runs the tagged application-context cases
with separate reports. Owning `check` requires both through the shared library mechanics.

From the repository root:

```sh
./gradlew :server:core:check :server:core:model:check
./gradlew coreArchitectureCi serverCi
```

PostgreSQL behavior uses the existing isolated App PostgreSQL lane until the owning integration
harnesses have been extracted. Current remaining runtime extraction and its single execution
plan are documented in `docs/architecture/server-domain-ownership.md`. No reduced-runtime
or complete schema cutover qualification is implied by these library checks.

Workspace capability authorization lives in `capability`, with shared wire values in
`capability.api` and shared shell/admin settings in `capability.config`. Domain owners supply
small typed declarations and readiness projections; Core owns Keycloak grants and denial
policy. Domain configuration keys/defaults, capability lists and feature readiness copy stay
with their owners. Missing owners produce no installed state or grants; disabled installed
owners retain explicit disabled states. Duplicate ownership or undeclared grants is rejected.
Actual serving policy/readiness beans register through Core's imports resource and remain
absent from non-web schema/operator discovery. The accepted full twelve-field API projection
is preserved while reduced-runtime publication awaits the reviewed contract/composition work.


Shared Admin policy lives in `admin.application` and its HTTP/wire contracts in `admin.api`.
Installed domains declare their existing simulation grants, profile capabilities and grouped suite
readiness metadata; Core validates ownership and assembles them without optional implementations.
Category selection policy is required in each owning provider-category fragment. Files retains its
unverified-activation refusal, and current binding conflicts remain protected for the scoped domains.
Missing bindings cannot be hidden by a default identity or nullable repository constructor.

The old App `discovery.admin.SuiteDomainDefinition` global feature constants moved to actual Files,
Office, Calendar, Meetings and Boards declarations. The disabled future MCP preview belongs to Agent
Runtime and disappears when that runtime is omitted. The unsupported App Notifications declaration
and matching registry fixture entry were retired: no handler, use case, repository or accepted
backend bounded context implemented it. Future notification product work remains separate scope;
no replacement backend or provider implementation is claimed. Manuals still has an accepted required
workspace wire slot and remains an explicit composition blocker until coordinated contract changes.
