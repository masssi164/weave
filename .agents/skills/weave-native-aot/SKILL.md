---
name: weave-native-aot
description: Assess, implement, or review Weave Spring AOT, GraalVM native-image builds, runtime hints, native tests, JNI/OpenDAL compatibility, memory/startup optimization, or native container delivery. Use for Java native builds and resource claims, not Flutter native builds or the existing Rust JNI build alone.
---

# Weave native and AOT

Read [Java/Spring standard](../../../docs/java-spring-standard.md), especially native acceptance. Inspect catalog, module plugins, runtime libraries, platform classifiers, and delivery consumers before changing the build.

1. Record exact Boot, Java source target, Gradle, GraalVM/native-build-tools versions and target OS/architecture/libc. Check primary documentation for that release. Java 21 source compatibility does not imply a GraalVM 21 builder; Boot 4 requires a compatible GraalVM 25+ builder.
2. Establish the affected JVM baseline first. Do not upgrade dependencies merely to fit an upstream template or treat the existing Rust/JNI task as Spring AOT.
3. Apply the Boot-compatible AOT/native plugins in the intended module and pin new versions centrally. Verify actual task discovery: `processAot`, `nativeCompile`, and native test tasks are not assumed to exist.
4. Keep all supported provider adapter definitions available at AOT processing. Provider selection and credentials remain runtime state, not build inputs. Do not embed secrets or require reachable external services while creating bean definitions.
5. Prefer Spring-supported registrations; add the smallest justified runtime hints for reflection, resources, serialization, proxies and JNI. Inspect generated AOT output. Do not blindly commit tracing-agent output or blanket reflection access.
6. Test the actual native executable: startup/shutdown, auth/HTTP interfaces, JSON, persistence/schema lifecycle, and used Rust/OpenDAL/JNI paths. Use native-safe fakes or real bounded dependencies; a Mockito mock-maker change is not proof of native compatibility.
7. Verify same-process provider routing and preserve cutover blockers. A native binary cannot gain new adapter classes without a rebuild, but can switch runtime bindings among compiled supported adapters.
8. Measure RSS/startup/throughput under a named workload on the target platform. Qualify the exact immutable image before distribution; coordinate [#1731](https://github.com/masssi164/weave/issues/1731). Consume verified images without rebuilding on Dogfood.
9. Return separate JVM, AOT, native-build, native-runtime, and platform results. Mark unexecuted gates honestly; do not label a JVM image or native compilation alone as native-qualified.

Upstream reference pins and known template mismatches are in [agent skills](../../../docs/agent-skills.md). Do not run a generic installer or copy a Maven/JVM Dockerfile into this Gradle/native path.
