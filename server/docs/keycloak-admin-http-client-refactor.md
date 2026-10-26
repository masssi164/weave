# Provider-scoped outbound HTTP clients

Status: **Keycloak implementation active in PR #1732; exact-head protected CI passed, independent review follow-up in progress.** Issue: [#1730](https://github.com/masssi164/weave/issues/1730).

## Design rule

Use the smallest framework-supported boundary that preserves the provider's actual protocol and security invariants.

- Ordinary Spring HTTP service interfaces use `@ImportHttpServices` with a named group.
- Group-local transport/authentication belongs in `RestClientHttpServiceGroupConfigurer`; do not hand-build a production `HttpServiceProxyFactory`.
- Keep explicit imperative clients for protocols that need custom DAV methods, streaming, bounded XML parsing, JNI, or other semantics that a generic HTTP interface would obscure.
- Do not add a config/helper class merely to move one constructor line. A configuration owner must own reusable policy such as authentication, timeout/SSL behavior, typed settings, lifecycle, or bean registration.
- Never place raw secrets in records or other values with generated `toString`/equality unless the type explicitly redacts them.

The canonical Java/Spring rules live in [`docs/java-spring-standard.md`](../../docs/java-spring-standard.md) and [the HTTP-client Skill](../../.agents/skills/weave-http-clients/SKILL.md). This page records the concrete provider decisions and must not redefine those standards.

## Current provider decisions

| Boundary | Shape | Decision |
|---|---|---|
| Keycloak invitation/organization Admin API | Spring HTTP service interface | `KeycloakAdminClient` declares named operations. `KeycloakAuthConfig` registers it as the `keycloak-admin` HTTP-service group. Group-scoped interceptors preserve the closed method/path/status policy, service-owned `private_key_jwt`, exact SecretRef access, timeouts and 401 invalidation. |
| OpenProject Boards API | Existing imperative Spring `RestClient` | Keep the existing endpoint/URI logic until an endpoint-oriented interface can remove real code. Do not wrap generic `get(URI)` / `patch(URI)` primitives in a manual proxy factory and call that migration complete. |
| Nextcloud Files WebDAV | Existing Spring `RestClient` with explicit WebDAV flow | PROPFIND/MKCOL, binary streaming, 207 and ETag semantics stay explicit. |
| Nextcloud CalDAV | Existing JDK `HttpClient` | REPORT/PROPFIND, sync semantics and bounded XML parsing remain protocol-specific. |
| Synapse Matrix application service | Existing JDK `HttpClient` | Streaming/status/auth semantics remain with the Matrix adapter. |
| Keycloak dynamic client registration | Protected Spring `RestClient` in its workload-identity owner | Separate privileged flow; do not conflate it with human administration. |
| Keycloak offline realm migration CLI | Isolated migration client | Keep live-app beans/tokens and offline migration authority separate. |

## Keycloak responsibility split

1. **HTTP contract:** `KeycloakAdminClient` declares provider routes, query/form parameters and media types. Spring owns query/form encoding and Jackson message conversion; the anti-corruption layer intentionally keeps `JsonNode` so strict provider field-type validation remains fail-closed rather than relying on coercing DTOs.
2. **HTTP-service group:** `KeycloakAuthConfig` owns group registration, base URI/context-path handling, connect/read timeout, service bearer acquisition and 401 invalidation.
3. **Security policy:** `KeycloakIdentityAdminOperationPolicy` is the closed method/path/status authority. Exact expected success codes—including rejection of unexpected 2xx—are intentional security behavior, not boilerplate.
4. **Provider anti-corruption layer:** `KeycloakIdentityAdminClient` owns bounded inventories, domain normalization, fail-closed provider-shape validation, fixture sequencing and support-safe errors.
5. **Application/domain:** Weave authorization, organization rules and member lifecycle remain outside provider transport types.

Managed-fixture methods require separate request-local admission. That marker is not an HTTP header. Member credentials are never relayed to the provider-admin client, and provider writes are never implicitly retried.

## Verification

For Keycloak changes, preserve tests for:

- Boot HTTP-service registration without credential access or provider contact during startup;
- configured context paths, encoded path variables, exact query ordering and framework form encoding;
- closed route/method admission and fixture-only denials before transport;
- exact success status mapping, including unexpected 2xx rejection before body conversion;
- service-owned `private_key_jwt`, exact SecretRef containment and 401 invalidation;
- invitation/member lifecycle behavior and support-safe provider failures.

Run focused tests first, then `./gradlew :server:test` / `serverCi` and protected CI. Deterministic CI is not live Keycloak, Dogfood, native-device or human-acceptance evidence.

## Remaining declarative work

Only convert another provider when the change removes meaningful transport code while preserving its protocol. Endpoint-specific OpenProject declarations are a reasonable future candidate. DAV, Matrix and other specialized boundaries stay imperative until a concrete, tested simplification exists.
