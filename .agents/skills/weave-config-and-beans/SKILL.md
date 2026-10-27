---
name: weave-config-and-beans
description: Design or review Weave Spring bean lifecycles, configuration properties, profiles, conditional beans, environment variables, provider registration, or runtime provider switching. Use whenever a change affects which bean exists, when credentials are resolved, or how an active provider is selected.
---

# Weave configuration and beans

Read [Java/Spring standard](../../../docs/java-spring-standard.md), especially the bean decision table and runtime provider rules. Inspect the relevant accepted corpus contract before changing routing or data ownership.

1. Classify every setting as deployment infrastructure, secret delivery/reference, or persisted organization/module runtime state. Do not move all settings into environment variables or delete unrelated infrastructure settings.
2. Choose bean ownership from the standard: plain value, application component, explicit composition, typed properties, or genuine library auto-configuration. Prefer constructor injection and explicit bean-method dependencies.
3. Keep the adapter catalog and bean definitions stable. Resolve the active persisted provider binding for each operation; retain the captured revision through in-flight work. Do not switch by environment variable, profile, `@Primary`, conditional creation, dynamic bean registration, or server/context restart.
4. Distinguish missing mandatory infrastructure from an unconfigured inactive provider. Validate the former at startup; the latter must remain inactive and fail closed on readiness/activation, without bringing the server down or reading credentials during construction.
5. Preserve migration gates, staged isolation, old-provider access, stable IDs/permissions, durable checkpoints, and compare-and-swap cutover. Do not implement undecided payload residency as a convenience. Chat's accepted canonical ownership is already in Weave.
6. Inspect [typed properties examples](../../../server/app/src/test/java/com/massimotter/weave/backend/config/reference/ConfigurationPropertiesReferenceTest.java) and [same-instance Files routing tests](../../../server/domains/files/src/test/java/com/massimotter/weave/backend/files/application/FilesProviderResolverTest.java). For runtime extraction, exercise [actual Files imports discovery](../../../server/domains/files/src/test/java/com/massimotter/weave/backend/files/autoconfigure/FilesRuntimeDiscoveryTest.java) without App scanning; manual test imports alone do not prove module registration. Preserve semantic annotations and compiler metadata according to the standard. Keep domain default resources/aliases module-local; selected production/export classpaths must not acquire omitted owners through integration fixtures.
7. Run `./gradlew javaAgentContractCheck :server:app:javaAgentReferenceTest` plus affected persistence/security tests. For real switching require one running process with staged target, source retention, in-flight work, failure/resume/rollback, and no member dual writes; unit routing proof is insufficient.

Current startup selectors are finite, tracked debt, not examples to copy. Remove an entry only with the corresponding safe runtime replacement; never weaken data-integrity gates to satisfy this skill.
