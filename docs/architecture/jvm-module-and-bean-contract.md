# JVM module, dependency and bean contract

The [Server ownership guide](server-domain-ownership.md) records implemented ownership and the
living extraction plan. The [Java/Spring standard](../java-spring-standard.md) owns coding and
configuration policy. The pinned corpus owns product and delivery contracts. This document
explains the current dependency direction without duplicating those rules.

## Compiled library ownership

Core model contains independently useful identity references, Spaces contracts, operation
intents and provider-binding values/ports. It is framework-free. Agent Runtime and Portability
have separate framework-free models; they are not platform prerequisites merely because the
old application-core project contained them.

Core persistence is organized under actual identity, security, audit, Spaces, operation,
provider-binding, readiness and schema responsibilities. Domain entities/repositories live in
Files, Calendar, Chat, Agent Runtime or Portability persistence. Each persistence project
uses the aligned JPA platform and depends inward on the relevant pure model. No persistence
project depends on the executable or on another domain's private entities.

Agent Runtime's existing provider and security adapter libraries retain separate compiled
boundaries: provider normalization cannot acquire persistence or delivery ownership, and
cryptographic/security adapters cannot acquire HTTP administration or JPA dependencies. Their
single identical signer implementation is owned by Security. Its behavior tests moved with it.

The retired root projects have no source-set aliases or second production copies. Their
replacements are `server/core/model`, `server/core`, `server/domains/agent-runtime` libraries,
`server/domains/portability` libraries and `server/domains/chat/persistence`.

## Runtime wiring and current limitation

Persistence libraries register module-local `@AutoConfiguration` through their own
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. They scope
entity/repository discovery to their actual responsibility packages and do not instantiate
servlet controllers or provider clients. Real discovery tests use Boot's import discovery,
not a manual import of the configuration under test. H2 proves this wiring only; PostgreSQL
remains authoritative for persistence semantics.

Actual Files, Calendar, Chat and existing future runtime services/controllers/providers now
belong to their domain projects. App composes only the resolved runtime libraries; broad integration
fixtures stay on the test classpath. Core Home, Platform, discovery and Admin consume narrow installed
owner declarations. Module-local imports and explicit wiring retain separate non-web tooling graphs.

The former blanket partial-packaging refusal is replaced by resolved classpath and executable JAR
checks. Isolated API export uses selected production output/resources, boots real handlers and writes
separate User/Admin documents. Schema export sees only selected compiled mappings. Outputs/reports
carry selection identity; actual packaged PostgreSQL startup is a separate lane. These checks do not
remove the accepted closed workspace/Matrix wire constraints, remaining App Manuals declaration,
schema cutover or coordinated client/platform qualification described in the living plan.

Shared health uses narrow domain contributions; the scheduler owns caching, backoff, metrics
and redaction while each domain owns its actual probe and provider lookup. Authentication
revocation state belongs to Security independently of Chat identity/device/crypto state.

## Independent processes and consumers

MCP is a separate executable that depends on the generated User client, never Server/Core
implementations, JPA, provider adapters or schema initialization. Product E2E is a plain Java
public-protocol consumer using distinct member and workload identities. Generated User/Admin
clients remain standalone and generated. Only executable applications apply the Boot plugin.

Follow the ownership guide for actual commands, pending extraction and exact-source evidence.
Historical DAV-only, private Runner and SQL-first implementation instructions are superseded;
they must not be reintroduced as active rules.
