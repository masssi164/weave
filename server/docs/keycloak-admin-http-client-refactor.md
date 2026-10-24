# Provider-scoped outbound HTTP clients

Status: **local implementation pending hosted compilation and review**. Issue: [#1730](https://github.com/masssi164/weave/issues/1730). This issue covers all outbound provider clients, not only Keycloak administration.

## Source-pinned boundary

- Base: `dev@97ee14b55d127416aea1e3fbe50681f4d79b4da2`; tree `c381f8795348f1ab93d03003cd7aefc8aa44b342`.
- Canonical corpus pin: `f049c0b1879414eb529a3df83a18224407f1a436`.
- Implementation owners: provider-specific `config/{keycloak,openproject,nextcloud,caldav,synapse}/`, their existing domain adapters, and focused tests. Recheck the inventory when `dev` moves.

## Repository inventory and current choices

| Boundary | Transport | Shape and decision |
|---|---|---|
| Keycloak invitation/organization Admin API | Spring `RestClient` | Named endpoint operations in `config/keycloak/KeycloakAdminClient` (`@GetExchange`, `@PostExchange`, `@PutExchange`, `@DeleteExchange`), backed by `KeycloakAuthConfig` and `KeycloakAdminProperties`. `KeycloakIdentityAdminOperationPolicy` owns exact method/path/status admission, enforced by the protected transport before sending or decoding a response body. |
| OpenProject Boards API | Spring `RestClient` | `config/openproject/OpenProjectClient` (`@GetExchange`, `@PatchExchange`), provider-scoped properties and HTTP factory; policy and response-error translation remain in `boards/openproject/`. |
| Nextcloud Files WebDAV | Spring `RestClient` with custom PROPFIND/MKCOL plus binary/streaming bodies | `config/nextcloud/NextcloudHttpConfig` owns the JDK request factory. The adapter retains the explicit WebDAV exchange flow until streaming and 207/ETag semantics are characterized. |
| Nextcloud CalDAV | JDK `HttpClient` with REPORT/PROPFIND and XML parsing | `config/caldav/CalDavHttpConfig` owns connection setup. Request-specific 207, sync and bounded parsing stay in the calendar adapter. |
| Synapse Matrix application service | JDK `HttpClient` with bounded streaming responses | `config/synapse/SynapseHttpConfig` owns connection setup; Matrix-specific status/auth remain in the provider adapter. |
| Keycloak dynamic client registration | Protected Spring `RestClient` in `SpringKeycloakClientRegistrationTransport` | Separate privileged workload-identity flow; **not** conflated with human administration. Candidate for a later provider-local transport without changing redirect rejection, response codes or credential rules. |
| Keycloak offline realm migration CLI | Isolated `RestClient` in `identity/migration` | Keep CLI separate from live app beans and admin tokens; migration has a distinct authorization and safety model. |
| OAuth2/JWT discovery and AWS runtime-state SDK | `RestTemplate` through Spring Security, AWS SDK client | Framework-owned protocol clients, not app REST contracts; preserve dedicated libraries and settings. |

The existing `IdentityInvitationProperties` remains the **single source of truth** for bound Keycloak invitation credentials. `KeycloakAdminProperties.from(...)` is an immutable runtime view, not a second binder or secret store. OpenProject takes its source from the existing Boards runtime properties. `config/` groups outbound HTTP wiring; `identity/`, `boards/`, `service/` and `chat/` continue owning domain behavior.

## Responsibility split

1. **HTTP contract:** Keycloak methods declare specific provider routes, variables, query parameters and body media types. No generic verb-and-URI forwarding remains in that client. The adapter invokes named methods and retains existing JSON normalization; it does not build URLs. OpenProject currently exposes two transport primitives and still needs endpoint-specific declarations before it is a complete declarative migration.
2. **Protected transport:** one Spring-owned binding supplies base URL (including any context path), connect/read timeout, service-owned `private_key_jwt` token acquisition, and 401 token invalidation. A provider-scoped interceptor applies the existing closed operation policy before transport and checks its exact successful status before response-body conversion. Managed fixture methods require separate request-local admission; that marker never becomes an HTTP header. `HttpServiceProxyFactory` reuses the protected `RestClient`; do not introduce a duplicate token cache or a global interceptor.
3. **Provider anti-corruption adapter:** `KeycloakIdentityAdminClient` owns domain translation, bounded inventory/pagination, managed-fixture authority and sequencing, safe errors, and protection against ambiguous provider results. `KeycloakIdentityAdminOperationPolicy` remains the single source for admitted method/path/status combinations.
4. **Domain/application:** Weave authorization and organization rules remain outside provider DTOs/URLs. Do not move Spring classes into framework-free application core.

The current `KeycloakIdentityAdminOperationPolicy` and the exact expected-status check are security boundaries, not boilerplate to remove. A declarative proxy must not accidentally allow a route, status, or body previously rejected.

## Verification and rollout

- Characterize the existing method/path/status matrix through focused tests before replacing calls.
- Run focused HTTP proxy tests including 4xx and **unexpected 2xx** translation without reading their bodies, configured context-path preservation, and encoded path variables; Keycloak's exact expected HTTP status behavior must not be relaxed by proxy defaults.
- Test 401 invalidation, all non-expected statuses (including unexpected success codes), allowlist and fixture-only denials, token/secret containment, and meaningful invitation/member flows.
- Validate existing integration tests across invitations, OpenProject, Nextcloud, CalDAV and Synapse; add a focused proxy characterization where tests did not cover status mapping.
- Run `./gradlew :server:test` and `./gradlew serverCi`, then protected CI and independent security review. Record real results in the PR, not here.

## Out of scope

JPA/DTO rewrites, new starter libraries, runtime architecture changes, provider migration, spec/product behavior changes, Dogfood/Main promotion, or personal iPhone acceptance.

If an operation cannot preserve its policy/status semantics, leave it on the existing protected transport and document the specific blocker rather than weakening checks.
