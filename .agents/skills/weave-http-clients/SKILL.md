---
name: weave-http-clients
description: Add, refactor, or review Weave outbound HTTP clients, especially Spring HTTP service interfaces, HttpExchange annotations, ImportHttpServices registration, authentication interceptors, timeout configuration, and provider adapter boundaries. Use for Keycloak and other HTTP integrations, not for replacing generated Weave API clients.
---

# Weave HTTP clients

Read [Java/Spring standard](../../../docs/java-spring-standard.md), especially HTTP boundaries and configuration ownership. Inspect the exact Spring Boot version before selecting APIs.

1. Inventory calls, authentication, status/error mapping, retries, DTOs, timeouts, and existing contract tests before refactoring. Preserve generated User/Admin API clients and specialized DAV/JNI protocol boundaries.
2. For an ordinary HTTP boundary, declare a small interface with explicit method annotations such as `@GetExchange` or `@PostExchange`; `@HttpExchange` does not infer an HTTP method from a Java method name.
3. Prefer Boot HTTP service groups and `@ImportHttpServices` on a configuration class. Use group-specific configuration for transport/auth. Do not create global bearer-token customizers or hand-build a proxy factory without a documented requirement.
4. Separate wire contract, typed infrastructure properties, authentication/composition, and provider/domain adaptation. For a selectable provider, resolve the authorized binding/configuration revision at request time; a static HTTP group is not the active-provider authority.
5. Inspect the executable [HTTP reference test](../../../server/src/test/java/com/massimotter/weave/backend/config/reference/DeclarativeHttpClientReferenceTest.java). Extend real boundary tests for verbs/paths, serialization, errors, credentials, timeout policy, and allowed retry behavior. The reference is not production Keycloak security evidence.
6. Run `./gradlew :server:javaAgentReferenceTest` and the affected production adapter/security tests. Review the diff for changes to `private_key_jwt`, exact SecretRefs, allowlists, redaction, or implicit retries.
7. Report the remaining imperative clients and why each is retained. Do not declare all REST clients converted from one example.

Coordinate the production Keycloak change with [#1730](https://github.com/masssi164/weave/issues/1730); do not create a conflicting implementation lane.
