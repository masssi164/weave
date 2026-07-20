package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeControlService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import com.massimotter.weave.backend.config.AgentRuntimeProfileConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeWorkloadIdentityConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeWorkloadIdentityProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.ProviderHealthProperties;
import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import com.massimotter.weave.backend.config.WeavePersistenceProperties;
import java.nio.file.Path;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentRuntimePersistenceConfigurationTest {
    @TempDir
    Path temporary;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WeavePersistenceConfiguration.class))
            .withUserConfiguration(
                    AgentRuntimeProfileConfiguration.class,
                    AgentRuntimeWorkloadIdentityConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PlatformContractProperties.class,
                    () -> new PlatformContractProperties(null, null, null, null, null, null, null, null))
            .withBean(ProviderHealthProperties.class,
                    () -> new ProviderHealthProperties(null, null, null, null))
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                    "weave.agent-runtime.storage.mode=jdbc",
                    "weave.persistence.jdbc.url=jdbc:h2:mem:arc-config;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "weave.persistence.jdbc.username=sa",
                    "weave.persistence.jdbc.password=");

    @Test
    void jdbcModeMigratesAndPublishesRuntimeControlAndFailClosedProfileServices() {
        context.run(application -> {
            assertThat(application).hasSingleBean(JdbcRuntimeCellRepository.class);
            assertThat(application).hasSingleBean(JdbcRuntimeCommandRepository.class);
            assertThat(application).hasSingleBean(JdbcRuntimeProfileRepository.class);
            assertThat(application).hasSingleBean(RuntimeProfileVerifier.class);
            assertThat(application).hasSingleBean(RuntimeProfileTrustBundlePublisher.class);
            assertThat(application).hasSingleBean(RuntimeProfileDeliveryService.class);
            assertThat(application).hasSingleBean(AgentRuntimeWorkloadTokenPolicy.class);
            assertThat(application).doesNotHaveBean(RuntimeProfileTrustKeyProvider.class);
            assertThat(application).doesNotHaveBean(RuntimeWorkloadCredentialStore.class);
            assertThat(application).doesNotHaveBean(RuntimeWorkloadIdentityAdmin.class);
            assertThat(application).hasSingleBean(WeavePersistenceProperties.class);
        });
    }

    @Test
    void explicitWorkloadIdentityEnablementBuildsTheCompleteSecretSafeControlGraph() {
        context.withPropertyValues(
                        "weave.agent-runtime.workload-identity.enabled=true",
                        "weave.agent-runtime.workload-identity.keycloak-admin-base-url=http://127.0.0.1:8180",
                        "weave.agent-runtime.workload-identity.issuer=https://auth.weave.test/realms/weave",
                        "weave.agent-runtime.workload-identity.admin-credential-ref="
                                + "credentialref://weave/agent-runtime/admin/keycloak",
                        "weave.agent-runtime.workload-identity.secret-root=" + temporary)
                .run(application -> {
                    assertThat(application).hasSingleBean(AgentRuntimeWorkloadIdentityProperties.class);
                    assertThat(application).hasSingleBean(FileRuntimeWorkloadCredentialStore.class);
                    assertThat(application).hasSingleBean(RuntimeWorkloadCredentialStore.class);
                    assertThat(application).hasSingleBean(SecretRefAccess.class);
                    assertThat(application).hasSingleBean(KeycloakAdminAccessTokenProvider.class);
                    assertThat(application).hasSingleBean(RuntimeWorkloadIdentityAdmin.class);
                    assertThat(application).hasSingleBean(RuntimeWorkloadIdentityInventory.class);
                    assertThat(application).hasSingleBean(AgentRuntimeControlService.class);
                    assertThat(application).hasSingleBean(AgentRuntimeWorkloadReconciliationService.class);
                });
    }
}
