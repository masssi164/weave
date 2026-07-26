# JVM module, dependency, and bean contract

This document is the implementation inventory for the Java/Spring architecture. The binding
product decisions remain in the pinned Specification Corpus; this inventory prevents Gradle
dependencies, component scanning, and convenience beans from creating a second architecture.

## Module dependency direction

```text
weave-application-core       weave-files-core
          ▲                         ▲
          │                         │
          ├──────────────┐          │
          │              │          │
weave-persistence-jpa    │          │
weave-runtime-provider-adapters     │
weave-runtime-security-adapters     │
          ▲              ▲          ▲
          └──────────────┴──────────┤
                                   server

weave-mcp-server ── HTTP/WebDAV ──> server

weave-product-e2e ── OIDC/WebDAV/MCP ──> running Server + MCP
```

The MCP process has no Java project dependency on Server, Application Core, persistence, or a
provider adapter. Its only integration with Server is a standards-based, OAuth2-protected
northbound projection. For the first vertical slice that projection is WebDAV `SEARCH`/`GET`.
The product E2E module likewise has no project dependency on either implementation; it drives
only their deployed public protocols.

## Dependency and bean ownership

| Module | Permitted dependencies | Bean ownership | Explicitly forbidden |
| --- | --- | --- | --- |
| `weave-application-core` | Java 21 only | none | Spring, Jakarta Persistence, Hibernate, JDBC, HTTP, MCP, Jackson and provider types |
| `weave-files-core` | Java 21 only | none | framework, transport and provider dependencies |
| `weave-persistence-jpa` | Application Core, Jakarta Persistence, Spring Data JPA, MapStruct API/processor | no configuration beans; entity/repository implementations are discovered only by the Server composition root | Web/controller DTOs, MCP, providers, JDBC templates, Hibernate APIs in entity code |
| `weave-runtime-provider-adapters` | Application Core and Jackson for provider payload normalization | no component scanning; Server configuration creates the selected port implementations explicitly | controllers, persistence entities, MCP annotations, provider DTOs crossing a port |
| `weave-runtime-security-adapters` | Application Core, Spring Security JOSE, canonical JSON and Jackson | no security filter chains; Server configuration creates cryptographic/policy port implementations explicitly | HTTP endpoints, JPA, provider administration and MCP transport |
| `server` | Spring Boot WebMVC/RestClient, Security Resource Server and OAuth2 Client, Validation, Data JPA, Flyway, Actuator, OpenAPI plus the adapter modules | the application composition root: security chains, use-case services, transaction/JPA composition, provider selection, one qualified Keycloak admin `RestClient` | Spring AI MCP transport/tools, MCP token-exchange admission, provider-shaped northbound contracts |
| `weave-mcp-server` | Spring Boot RestClient, Security Resource Server and OAuth2 Client, Spring AI MCP WebMVC, Actuator and PEM/JWK support | one MCP security chain, one JWT decoder, one token-exchange boundary, one request-scoped exchanged credential, one WebDAV client, one Files tool/resource projection, framework transport customizers | DataSource, JPA, Hibernate, Flyway, Server entities/use cases and every southbound provider |
| `weave-product-e2e` | Plain Java, Playwright, Jackson, Nimbus JOSE/JWT, JUnit, AssertJ and ArchUnit | no Spring beans; one bounded process drives invitation, browser activation, PKCE, ARC, WebDAV and MCP | Spring, JPA/Hibernate, Server/MCP implementation dependencies, provider adapters, credential/evidence persistence |

Only the two deployable Spring processes apply the Spring Boot plugin. All JVM modules use Java 21 and resolve
Spring/Security/Jackson versions from the repository version catalog and the Spring Boot BOM.
The MCP executable imports the compatible Spring AI BOM; no library module pins an alternative
Spring generation. The verification-only E2E module applies the Java plugin and is never packaged
into a runtime image.

## Server security beans

The normal API is a stateless OAuth2 Resource Server. Its shared JWT decoder validates signature,
issuer, timestamps, exact audience and first-party client binding. Purpose-specific chains are
ordered before the general API chain only where their token profile is materially different:
Agent Runtime administration, RuntimeProfile delivery, isolated Chat proof, Matrix application
service callback, and Files WebDAV workload access. A chain owns one exact path family and cannot
act as a fallback for another.

Server-to-Keycloak invitation administration is the single OAuth2 Client integration for that
boundary:

- Boot owns the `ClientRegistrationRepository`, authorized-client service and
  `OAuth2AuthorizedClientManager`.
- `KeycloakAdminClientConfiguration` owns one qualified
  `keycloakIdentityAdminRestClient`.
- `OAuth2ClientHttpRequestInterceptor` obtains and caches the `client_credentials` token under
  the fixed service principal `weave-server`.
- `KeycloakIdentityAdminClient` owns only Keycloak admin URI/payload normalization. It contains
  no token endpoint call, bearer cache, password grant or human-token relay.

Agent Runtime Control reuses the same Spring Security OAuth2 implementation without adding a
second protocol stack:

- the two explicitly qualified Keycloak administration token ports separate workload-client
  mutation from read-only entitlement observation;
- `SpringSecurityKeycloakAdminAccessTokenProvider` resolves each long-lived client secret only
  through its mounted `SecretRef` and delegates the grant to
  `RestClientClientCredentialsTokenResponseClient`;
- the short-lived bearer cache and rejection invalidation remain inside that one adapter;
- `weave-runtime-provider-adapters` contains Keycloak Admin REST normalization but no token
  endpoint request, Spring bean, OAuth implementation or secret-bearing configuration.

The first-owner bootstrap adds no second identity client:

- `BootstrapOwnerCredential` is created only when
  `weave.identity.invitations.bootstrap-owner.enabled=true`; it reads a read-only, private
  SecretRef on every constant-time comparison and rejects symlinks or permissive POSIX modes.
- `BootstrapOwnerInvitationController` owns exactly
  `POST /api/bootstrap/owner-invitation`; the general API chain permits this path because the
  controller authenticates the one-shot SecretRef itself.
- `MemberInvitationService` still creates the canonical provisioning intent and delegates the
  provider invitation to the same OAuth2-secured `KeycloakIdentityAdminClient`.
- The controller and credential bean do not exist in the normal runtime profile. An empty-realm
  check, exact provider/local correlation, and owner-only projection make retries fail closed.

The JPA starter owns `EntityManagerFactory`, transaction management, repository proxies and
Hibernate. Server is the sole Flyway runner. H2 `dev-h2` uses an isolated in-memory schema built
from entities with `create-drop`; PostgreSQL profiles apply reviewed Flyway migrations and use
`ddl-auto=validate`. `JpaAuditEventPublisher` is the required runtime audit bean; application
services do not silently create an in-memory audit sink. No production-shaped profile falls back
to H2.

## MCP security beans

The MCP application has one workload-only Security filter chain:

- `mcpJwtDecoder` validates issuer, time, access-token type and the exact singleton MCP resource audience;
- `McpRequestAdmissionFilter` validates extension negotiation, cell client, workload role and
  required scopes before tool dispatch;
- `McpBackendTokenExchange` uses Spring Security's RFC 8693
  `TokenExchangeOAuth2AuthorizedClientProvider`,
  `RestClientTokenExchangeTokenResponseClient`, and `private_key_jwt`;
- `McpInvocationCredentials` is request-scoped and exposes only the exchanged backend token;
- `FilesWebDavClient` attaches that exchanged token to the standard Files projection and has no
  route or credential fallback;
- `FilesMcpProjection` alone owns `files.search` and
  `weave://files/{canonicalFileId}`.

The incoming MCP bearer is never put into a backend request. Token exchange cannot widen scopes,
produce a refresh token, outlive the cell token, or target a different backend resource.

## Open-standard reuse

OpenAPI is the server-owned description for REST control-plane routes and generates typed
Flutter/Admin transport models. It is not a universal protocol compiler:

- OIDC/OAuth2 clients use discovery metadata and Spring/Flutter OIDC libraries;
- Files clients and MCP consume WebDAV;
- Calendar clients and future MCP projections consume CalDAV/iCalendar;
- Chat clients and future MCP projections consume the Matrix Client-Server projection;
- MCP discovery, tools, resources, prompts and schemas are generated by the MCP implementation.

This gives Client and MCP the same canonical domain projections without inventing private
tool-specific Server endpoints or pretending that OpenAPI describes WebDAV, Matrix, CalDAV, or
MCP itself.

## Identity and candidate artifacts

Keycloak Desired State is bundled as the separate `weave-identity-ops` OCI artifact. The image
uses digest-pinned Alpine and OpenTofu-minimal bases, mirrors the lock-file-selected Keycloak
provider during the build, runs as a numeric non-root identity, and supports only `validate`,
`plan`, and reviewed-plan `apply`. Its runtime root filesystem is read-only; `/tmp` is `noexec`,
the provider work area is a bounded executable tmpfs, and the state directory is an explicit
private mount.

`.github/workflows/candidate-images.yml` is the only candidate image producer. It runs the JVM,
MCP, and PostgreSQL gates before publishing Server, MCP Server, and Identity Ops. Tags identify a
commit and workflow attempt for navigation, but deployment identity is always the image digest.
Each image carries OCI/Weave labels plus embedded SPDX SBOM and SLSA provenance attestations. A
canonical candidate manifest binds all three image digests, attestation layer digests, the commit,
and the Specification Corpus lock digest. Its dependent `fresh-product-proof` job passes only the
published Server and MCP `@sha256` references into `testApp`, which verifies their revision,
Specification Corpus digest and dependency-platform annotations before starting the isolated
stack.

## Fresh product-flow proof

`./gradlew testApp` owns one run-scoped stack from preparation through exact teardown:

1. A local image build requires a clean Git worktree; candidate CI instead supplies paired,
   published Server and MCP digest references. This prevents an uncommitted source tree from
   inheriting a misleading commit revision label.
2. `prepare-product-flow` creates namespace, ports, ownership evidence, a machine-only Chat proof
   SecretRef and empty human membership inputs. It creates no `credentials.env`.
3. The protected first-owner operation emits a normal Keycloak organization invitation.
4. The Java process reads the matching one-time action from loopback Mailpit, uses real Chromium
   with an exact leaf-SPKI pin, and holds the generated password only in memory.
5. Owner and member use Authorization Code with PKCE S256. The owner invites the member through
   Weave with `agent-runtime.entitled`.
6. ARC provisions the per-cell `private_key_jwt` SecretRef. The workload negotiates MCP, discovers
   `files.search`, and reaches the same `/dav/files` projection as other standards clients.
7. Revocation must deny a second workload invocation. The run then destroys only resources whose
   namespace and immutable ownership evidence match.

The only durable product-flow artifact contains timestamps and hashes plus protocol/result enums.
It contains no email, password, bearer token, action URL, client assertion, client ID or private
key.

## Enforcement

- ArchUnit rejects frameworks from Application Core, delivery/provider dependencies from
  persistence, Spring/OAuth/JPA from provider adapters, delivery/provider HTTP/JPA from security
  adapters, and backend/JPA dependencies from MCP.
- Server H2 repository tests give fast feedback; `postgresJpaTest` is the authoritative
  migration/entity/repository gate.
- OpenAPI and generated Flutter/Admin models have freshness gates.
- `infraStatic` validates the Desired State, ownership labels and lifecycle scripts.
- `testAppContract` compiles and architecture-tests the verification module without starting a
  stack; the candidate `fresh-product-proof` job runs the full disposable stack.
- `identityOpsValidate` validates the exact bundled Desired State/provider combination in the
  hardened container sandbox.
- `serverCi` and `mcpCi` remain separate process/module gates.
