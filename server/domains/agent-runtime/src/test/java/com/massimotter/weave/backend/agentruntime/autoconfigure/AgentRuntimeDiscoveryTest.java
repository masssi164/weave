package com.massimotter.weave.backend.agentruntime.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeAdminController;
import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeErrorResponseWriter;
import com.massimotter.weave.backend.agentruntime.api.AgentRuntimeProfileController;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.persistence.JpaRuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.persistence.RuntimeStateJpaAuthority;
import com.massimotter.weave.backend.agentruntime.persistence.S3EncryptedRuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.security.FileRuntimeProfileSigningKeyStore;
import com.massimotter.weave.backend.agentruntime.security.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.readiness.port.LocalDependencyContribution;
import com.massimotter.weave.backend.security.workload.application.ReleaseMcpWorkloadAuthorizationService;
import com.massimotter.weave.backend.security.workload.port.WorkloadEntitlementAuthority;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Discovers the published runtime imports with no App scan or mounted provider secrets. */
@Tag("context")
class AgentRuntimeDiscoveryTest {
    @TempDir
    Path temporary;

    @Test
    void actualServingGraphOwnsProfileHandlersSecurityAndSingletonPersistenceAdapters() {
        web().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AgentRuntimeAutoConfiguration.class)
                    .hasSingleBean(AgentRuntimeProfileController.class)
                    .hasSingleBean(AgentRuntimeErrorResponseWriter.class)
                    .hasSingleBean(RuntimeProfileDeliveryService.class)
                    .hasSingleBean(JpaRuntimeCellRepository.class)
                    .hasSingleBean(JpaRuntimeProfileRepository.class)
                    .hasSingleBean(RuntimeStateJpaAuthority.class)
                    .hasBean("agentRuntimeProfileJwtDecoder")
                    .hasBean("agentRuntimeProfileSecurityFilterChain")
                    .doesNotHaveBean(AgentRuntimeAdminController.class)
                    .doesNotHaveBean(S3EncryptedRuntimeStateStore.class)
                    .doesNotHaveBean(LocalDependencyContribution.class);
            assertThat(context.getBean(WorkspaceCapabilityService.class).installedDefinitions())
                    .extracting(value -> value.domainId()).containsExactly("agent-runtime");
            var paths = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                    .getHandlerMethods().keySet().stream()
                    .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
            assertThat(paths).contains("/api/v1/agent-runtime/trust/jwks.json",
                    "/api/v1/agent-runtime/runtime-profiles/{profileHash}");
            assertThat(paths).noneMatch(path -> path.startsWith("/api/admin/agent-runtimes"));
            assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.WeaveBackendApplication",
                    context.getClassLoader())).isFalse();
            assertThat(ClassUtils.isPresent("com.massimotter.weave.backend.matrix.NativeMatrixCore",
                    context.getClassLoader())).isFalse();
            assertThat(ClassUtils.isPresent("org.apache.opendal.Operator", context.getClassLoader())).isFalse();
        });
    }

    @Test
    void nonWebSchemaContextInstallsMappingsWithoutServingProvidersOrSecurityChains() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasBean("runtimeCellJpaRepository")
                        .doesNotHaveBean(AgentRuntimeAutoConfiguration.class)
                        .doesNotHaveBean(AgentRuntimeProfileController.class)
                        .doesNotHaveBean(JpaRuntimeCellRepository.class)
                        .doesNotHaveBean(S3EncryptedRuntimeStateStore.class)
                        .doesNotHaveBean("agentRuntimeProfileJwtDecoder"));
    }

    @Test
    void enabledStateAndSigningGraphIsOfflineButServingReadinessRejectsMissingMounts() {
        Path signing = temporary.resolve("signing");
        Path wrapping = temporary.resolve("wrapping");
        web().withPropertyValues(
                        "weave.agent-runtime.profile-signing.enabled=true",
                        "weave.agent-runtime.profile-signing.secret-root=" + signing,
                        "weave.agent-runtime.state-store.enabled=true",
                        "weave.agent-runtime.state-store.wrapping-key-root=" + wrapping,
                        "weave.agent-runtime.state-store.endpoint=http://127.0.0.1:1",
                        "weave.agent-runtime.state-store.region=us-east-1",
                        "weave.agent-runtime.state-store.bucket=offline-state",
                        "weave.agent-runtime.state-store.credential-ref=secretref:runtime-state/test",
                        "weave.agent-runtime.state-store.access-key-file=" + temporary.resolve("missing-access"),
                        "weave.agent-runtime.state-store.secret-key-file=" + temporary.resolve("missing-secret"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(S3EncryptedRuntimeStateStore.class)
                            .hasSingleBean(FileRuntimeProfileSigningKeyStore.class)
                            .hasSingleBean(FileRuntimeStateKeyWrapper.class);
                    assertThat(context.getBeansOfType(LocalDependencyContribution.class).values())
                            .extracting(LocalDependencyContribution::id).containsExactly("agent-runtime-state");
                    assertThat(Files.exists(signing)).isFalse();
                    assertThat(Files.exists(wrapping)).isFalse();
                    var arguments = new DefaultApplicationArguments(new String[0]);
                    assertThatThrownBy(() -> context.getBean("runtimeProfileSigningSecretReadiness",
                            ApplicationRunner.class).run(arguments)).isInstanceOf(RuntimeException.class);
                    assertThatThrownBy(() -> context.getBean("runtimeStateSecretReadiness",
                            ApplicationRunner.class).run(arguments)).isInstanceOf(RuntimeException.class);
                    assertThat(Files.exists(signing)).isFalse();
                    assertThat(Files.exists(wrapping)).isFalse();
                });
    }

    @Test
    void completeEnabledControlPlaneIsDiscoveredOfflineWithOneSharedEntitlementAuthority() {
        Path secretRoot = temporary.resolve("unmounted-workload");
        web().withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
                        "weave.agent-runtime.workload-identity.enabled=true",
                        "weave.agent-runtime.workload-identity.secret-root=" + secretRoot,
                        "weave.agent-runtime.workload-identity.admin-credential-ref=credentialref://weave/agent-runtime/admin",
                        "weave.agent-runtime.workload-identity.issuer=https://auth.weave.test/realms/weave",
                        "weave.agent-runtime.workload-identity.keycloak-admin-base-url=http://127.0.0.1:1",
                        "weave.agent-runtime.workload-identity.keycloak-organization-id=organization-test",
                        "weave.agent-runtime.policy.enabled=true",
                        "weave.agent-runtime.policy.file=" + temporary.resolve("unmounted-policy.json"),
                        "weave.agent-runtime.profile-signing.enabled=true",
                        "weave.agent-runtime.profile-signing.secret-root=" + temporary.resolve("unmounted-signing"),
                        "weave.agent-runtime.state-store.enabled=true",
                        "weave.agent-runtime.state-store.wrapping-key-root=" + temporary.resolve("unmounted-wrapping"),
                        "weave.agent-runtime.state-store.endpoint=http://127.0.0.1:1",
                        "weave.agent-runtime.state-store.region=us-east-1",
                        "weave.agent-runtime.state-store.bucket=offline-state",
                        "weave.agent-runtime.state-store.credential-ref=secretref:runtime-state/test",
                        "weave.agent-runtime.state-store.access-key-file=" + temporary.resolve("missing-access"),
                        "weave.agent-runtime.state-store.secret-key-file=" + temporary.resolve("missing-secret"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AgentRuntimeAdminController.class)
                            .hasSingleBean(AgentRuntimeAdminService.class)
                            .hasSingleBean(AgentRuntimeControlService.class)
                            .hasSingleBean(RuntimePersonDirectory.class)
                            .hasSingleBean(RuntimeWorkloadIdentityAdmin.class)
                            .hasSingleBean(WorkloadEntitlementAuthority.class)
                            .hasBean("agentRuntimeAdminSecurityFilterChain")
                            .hasBean("agentRuntimeAdminJwtDecoder")
                            .doesNotHaveBean(ReleaseMcpWorkloadAuthorizationService.class);
                    assertThat(context.getBeansOfType(LocalDependencyContribution.class).values())
                            .extracting(LocalDependencyContribution::id)
                            .containsExactlyInAnyOrder("agent-runtime-state", "agent-runtime-workload-identities");
                    assertThat(Files.exists(secretRoot)).isFalse();
                });
    }

    private WebApplicationContextRunner web() {
        return new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
