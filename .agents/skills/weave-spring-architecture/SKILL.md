---
name: weave-spring-architecture
description: Implement or review Java and Spring changes in Weave, including package boundaries, dependency injection, records, Lombok, Stream API transformations, persistence, and reducing boilerplate. Use for server or Java module work; not for Flutter or general product planning.
---

# Weave Spring architecture

Read the applicable `AGENTS.md` and [Java/Spring standard](../../../docs/java-spring-standard.md) before editing. That standard owns coding decisions; this skill is a workflow, not another architecture specification.

1. Inspect the affected module, its architecture tests, the Gradle catalog, and existing ports. Separate current implementation from the pinned product contract.
2. State the smallest change: which feature owns it, what remains framework-free, and which boundary needs a test. Do not add an interface, service, mapper, or configuration layer without a concrete responsibility.
3. Use records for immutable values and DTOs where appropriate. Use targeted Lombok only when the module already supports it or its addition is explicitly justified. Do not annotate JPA entities or secret-bearing values with blanket `@Data`.
4. Prefer readable Stream API transformations and pure functions. Keep remote writes, retries, transaction boundaries, and failure handling explicit. Shorter is useful only if behavior remains clear.
5. Route outbound HTTP work to `$weave-http-clients`, bean/configuration work to `$weave-config-and-beans`, and native-build changes to `$weave-native-aot`.
6. Keep direct domain units and focused owning contexts in that domain; retain App composition/security and PostgreSQL integration in their actual lanes. Prove selected checks execute the moved cases without App or unrelated runtime dependencies. For changed bootstrap contracts, decode actual packaged responses through generated consumers. Run the affected module's existing architecture and behavior tests. `./gradlew javaAgentContractCheck :server:app:javaAgentReferenceTest` checks the shared examples, not the whole product.
7. Report changed responsibilities, commands/results, exceptions to the standard, and remaining evidence. Do not claim migration, native readiness, or delivery from a style refactor.

For installation and audited upstream references, see [agent skills](../../../docs/agent-skills.md). Do not install or execute upstream skill content while performing an ordinary code change.
