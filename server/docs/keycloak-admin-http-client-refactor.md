# Proposed Keycloak Admin HTTP client refactor

Status: **implementation plan only**. Nothing in this note changes runtime behavior. Issue: [#1730](https://github.com/masssi164/weave/issues/1730).

## Source-pinned boundary

- Base: `dev@97ee14b55d127416aea1e3fbe50681f4d79b4da2`; tree `c381f8795348f1ab93d03003cd7aefc8aa44b342`.
- Canonical corpus pin: `f049c0b1879414eb529a3df83a18224407f1a436`.
- Implementation owners: `config/KeycloakAdminClientConfiguration.java`, `identity/invitation/KeycloakIdentityAdminClient.java`, `identity/invitation/KeycloakIdentityAdminOperationPolicy.java`, and their focused tests. Check for relevant deltas when `dev` moves.

## Responsibility split

1. **HTTP contract:** a small `@HttpExchange` interface exposes *specific* Keycloak Admin operations, their method/path/query/body and return type. Do not create an untyped generic `execute(method, uri)` interface or duplicate the Keycloak REST schema in Weave's public API.
2. **Protected transport:** one Spring-owned binding supplies base URL, connect/read timeout, service-owned `private_key_jwt` token acquisition, and 401 token invalidation. Spring Boot 4 named HTTP service groups/`@ImportHttpServices` are preferred if a single scoped group customizer preserves every invariant; otherwise use `HttpServiceProxyFactory` with the existing protected `RestClient`.
3. **Provider anti-corruption adapter:** `KeycloakIdentityAdminClient` owns domain translation, bounded inventory/pagination, fixture restrictions, allowlisted operations, *exact expected response statuses*, safe errors, and protection against ambiguous provider results.
4. **Domain/application:** Weave authorization and organization rules remain outside provider DTOs/URLs. Do not move Spring classes into framework-free application core.

The current `KeycloakIdentityAdminOperationPolicy` and the exact expected-status check are security boundaries, not boilerplate to remove. A declarative proxy must not accidentally allow a route, status, or body previously rejected.

## Minimal implementation order

- Characterize the existing method/path/status matrix through focused tests before replacing calls.
- Migrate one read-only call as a proof, then dependent operations only if equivalent, with one transport path per operation.
- Test 401 invalidation, all non-expected statuses (including unexpected success codes), allowlist and fixture-only denials, token/secret containment, and meaningful invitation/member flows.
- Compare the diff for fewer incidental branches and less ad-hoc URI construction; avoid mass package moves or introducing generic wrappers.
- Run `./gradlew :server:test` and `./gradlew serverCi`, then protected CI and independent security review. Record real results in the PR, not here.

## Out of scope

Global `config/` reorganization, all-provider REST conversion, JPA/DTO rewrites, new starter libraries, runtime architecture changes, provider migration, spec/product behavior changes, Dogfood/Main promotion, or personal iPhone acceptance.

If an operation cannot preserve its policy/status semantics, leave it on the existing protected transport and document the specific blocker rather than weakening checks.
