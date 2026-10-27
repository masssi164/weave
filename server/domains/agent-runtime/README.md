# Agent Runtime

Agent Runtime is a future product domain. Its existing serving implementation is now owned by
this Gradle runtime, independently of App scanning. This extraction preserves the implemented
control plane; it does not make future runtime execution production-ready or include it in the
automatic Files/Calendar/Chat release. The [Server ownership record](../../../docs/architecture/server-domain-ownership.md)
tracks the remaining composition work.

The root runtime owns the administrative and profile HTTP APIs, closed error envelopes,
control-plane orchestration, reconciliation, capability and local-readiness contributions,
typed settings, specialized Keycloak registration transport, encrypted S3 state adapter and
key-management operator entry points. Packages describe those responsibilities under
`com.massimotter.weave.backend.agentruntime`.

The useful compiled boundaries remain:

| Project | Responsibility |
| --- | --- |
| `model` | Framework-free cells, profiles, workload identities, policies, ports and pure lifecycle/profile use cases. |
| `persistence` | Normalized JPA mappings, repositories, transactions and adapters in `agentruntime.persistence`. |
| `provider` | File policy/credential authorities and Keycloak workload/person adapters in `agentruntime.provider.identity`. The Spring registration transport belongs to the root runtime. |
| `security` | Profile signatures/trust, exact workload-token policy, key wrapping and guarded key-store operations in `agentruntime.security`. |

The runtime depends on Core contracts and these owning libraries. None depends on App or another
optional implementation. Core retains shared member/session revocation, workload identity and
entitlement contracts, release MCP authorization, protected Keycloak administration tokens and
bounded JWT verification helpers. The realm-migration token provider and operators remain with
Core Identity; future runtime adapters are not prerequisites of shared identity recovery.

`AgentRuntimeAutoConfiguration` is published through the root module's
`AutoConfiguration.imports`. It explicitly installs the servlet graph after the persistence
module's imports-discovered mappings and repositories. Non-web schema contexts construct no
serving controllers, security chains, state providers or secret stores. Existing activation
conditions remain: future workload administration requires the enabled workload branch and an
empty release-binding-file setting; the complete administrative graph also requires policy,
signing and state settings. Disabled signing retains the existing unavailable public trust
behavior. It never grants a workload access to an unsigned or unavailable profile.

Provider construction is offline. Read-only signing/wrapping mounts are checked by ordered
serving-startup readiness and again on use. Read paths validate permissions without changing
them. Only explicit writable operator operations initialize or rotate key material. State
credentials are read from the existing guarded files at serving readiness or first use and
cached, with no default credential chain. Issuer-only JWT discovery uses Spring Security's
cached `SupplierJwtDecoder` on first verification; exact issuer/audience/authorized-party/scope
and `at+jwt` policies and bounded HTTP timeouts remain enforced.

Local readiness belongs to the domain. `RuntimeStateReadiness` and `RuntimeWorkloadReadiness`
produce the existing support-safe diagnostic observations only when their actual services are
installed. App projects them into the unchanged platform response; construction does not probe
S3, Keycloak or reconciliation state.

From the repository root:

```sh
./gradlew :server:domains:agent-runtime:check -Pdomains=all
./gradlew :server:domains:agent-runtime:test -Pdomains=all
./gradlew :server:domains:agent-runtime:contextTest -Pdomains=all
./gradlew :server:domains:check -Pdomains=agent-runtime
```

`check` includes the root's genuine unit/context suites and the four libraries' checks. Context
tests discover the published imports without App, prove handler/bean uniqueness and non-web
isolation, and construct the complete enabled graph with absent mounts and offline providers.
Pure profile delivery/issuance tests run in `model`; provider/security tests run with their
implementations. App retains the existing HTTP/security and database harnesses that require its
shared fixtures. The existing PostgreSQL lane executes `JpaRuntimeCellRepositoryTest`,
`JpaRuntimeCommandRepositoryTest`, `JpaRuntimeGovernanceRepositoryTest` and
`JpaRuntimeProfileRepositoryTest` against real PostgreSQL. No assertions or database cases are
replaced by the construction tests.

Selection is resolved by the [shared build workflow](../../../docs/development/domain-verification.md).
Owning checks do not establish reduced App packaging or API/schema qualification; those entry
points retain the existing guard until selected runtime composition and the required contract
changes are accepted. Existing key-management commands still enter through the sole App
executable and retain their explicit operator safeguards.
