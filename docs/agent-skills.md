# Repository-local Java agent skills

The [Java/Spring standard](java-spring-standard.md) is the single implementation coding policy. The four original Weave workflows in `.agents/skills/` apply it to bounded tasks; they do not replace the pinned product corpus, executable checks or code review.

| Skill | Use when |
| --- | --- |
| `weave-spring-architecture` | Editing/reviewing Java architecture, boundaries, Stream API style or boilerplate |
| `weave-http-clients` | Adding/refactoring HTTP interfaces, registration, authentication or transport policy |
| `weave-config-and-beans` | Choosing bean ownership/lifetime, settings ownership or runtime provider routing |
| `weave-native-aot` | Working on Spring AOT, GraalVM, native hints/testing or resource-efficient images |

## Use in a checkout

Use a checkout containing these files and start a coding session from the repository root. Compatible agents discover `.agents/skills/<name>/SKILL.md`; check the running agent's skill list rather than assuming an old session loaded new files. The [official Codex skill documentation](https://learn.chatgpt.com/docs/build-skills) describes automatic discovery and `/skills`; restart if changes do not appear. Codex CLI/IDE can explicitly invoke, for example:

```text
$weave-http-clients Refactor this bounded HTTP integration. Preserve its security contract and run the matching tests.
```

For Copilot or another client, use its installed version's skill selection mechanism. Root/scoped `AGENTS.md` and `.github/copilot-instructions.md` also point to the standard, so the contract remains readable when automatic skill discovery is unavailable. In that case explicitly ask the agent to read the named local `SKILL.md` and its standard before editing. A GitHub plugin connection alone does not install skills into another running coding agent.

No global installer, remote execution, symlink farm or duplicate `.github/skills` copy is needed. Existing `.agent/skills/` Flutter/Riverpod content remains untouched. Do not put personal assistant settings, machine paths, model routing or credentials in these skills.

## Maintenance and evidence

- Keep each `SKILL.md` short, with a specific description and links to shared policy and executable examples. Its `agents/openai.yaml` contains display/invocation metadata only.
- Run `./gradlew javaAgentContractCheck :server:app:javaAgentReferenceTest` after modifying the contract or examples. The static guard validates skill structure/local links and rejects additions to the finite legacy startup-selector inventory. Its negative tests prove rejection; it is not a semantic proof that all provider logic is dynamic.
- Existing module ArchUnit tests enforce framework-free core and adapter dependencies. New HTTP/configuration examples are JVM tests. The same-instance Files resolver test uses a repository double and proves routing behavior only, not populated-data migration.
- Forward-test skill behavior on a realistic bounded task and inspect the proposed changes; never confuse wording validation with reliable model compliance.
- Remove legacy selector entries only when their runtime replacement is proven. Do not regenerate a baseline to approve new violations. Follow the linked migration and native lanes without claiming they are complete.

## Audited upstream references

The workflows are Weave-authored. Upstream skills were reviewed as advisory sources, not installed or executed. The immutable revisions below make that review reproducible; they are not dependency pins for Spring or Java. Review upstream changes deliberately instead of following `main` automatically. If future work copies source text or templates, retain the applicable MIT notice and support files.

`rrezartprebreza/spring-boot-skills` at `42bd5f1240be2379012a0491b0162dc430980313` (MIT; [license](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/LICENSE)):

- [HTTP interface clients](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/skills/spring-boot-4/http-interface-clients/SKILL.md): useful Boot 4 registration/group guidance; static groups do not own runtime provider selection.
- [Configuration properties](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/skills/spring-boot-4/configuration-properties/SKILL.md): useful binding/validation guidance; do not require an inactive provider's credentials at server startup.
- [Hexagonal architecture](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/skills/spring-boot-4/hexagonal-architecture/SKILL.md): Weave's core remains stricter and framework-free; local transactions do not undo remote effects.
- [Spring Data JPA](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/skills/spring-boot-4/spring-data-jpa/SKILL.md): entity/DTO discipline, not authorization to replace schema ownership or add blanket Lombok.
- [Container/native deployment](https://github.com/rrezartprebreza/spring-boot-skills/blob/42bd5f1240be2379012a0491b0162dc430980313/skills/spring-boot-4/container-native-deployment/SKILL.md): its example Dockerfile is a JVM/Maven layout, not Weave's native Gradle delivery template.

`github/awesome-copilot` at `d85f762d6e521abf5fe16703946e9b38adc42da7` (MIT; [license](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/LICENSE)):

- [GraalVM skill](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/skills/java-add-graalvm-native-image-support/SKILL.md), [Spring-specific reference](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/skills/java-add-graalvm-native-image-support/references/spring-boot.md), [build tools](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/skills/java-add-graalvm-native-image-support/references/build-tools.md), [reachability metadata](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/skills/java-add-graalvm-native-image-support/references/reachability-metadata.md), and [troubleshooting](https://github.com/github/awesome-copilot/blob/d85f762d6e521abf5fe16703946e9b38adc42da7/skills/java-add-graalvm-native-image-support/references/troubleshooting.md).
- Adapt to the exact Boot release, module-qualified Gradle tasks, JNI platforms and Weave evidence. Do not copy generic Java baseline advice, assume a Mockito mock-maker makes native tests work, or bring unrelated framework skills into this repository.

## Delivery boundaries

[#1735](https://github.com/masssi164/weave/issues/1735) owns these skills and guardrails; [#1736](https://github.com/masssi164/weave/issues/1736) tracks safe removal of the remaining startup selectors. Production Keycloak conversion remains [#1730](https://github.com/masssi164/weave/issues/1730) / [PR #1732](https://github.com/masssi164/weave/pull/1732); GHCR artifact transport remains [#1731](https://github.com/masssi164/weave/issues/1731) / [PR #1733](https://github.com/masssi164/weave/pull/1733). Neither a skill nor an open planning PR proves that implementation, native qualification, or live provider replacement has shipped.
