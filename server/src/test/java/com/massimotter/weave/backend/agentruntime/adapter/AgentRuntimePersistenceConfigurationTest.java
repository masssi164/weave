package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.config.AgentRuntimeProfileConfiguration;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import com.massimotter.weave.backend.config.WeavePersistenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentRuntimePersistenceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WeavePersistenceConfiguration.class))
            .withUserConfiguration(AgentRuntimeProfileConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(PlatformContractProperties.class,
                    () -> new PlatformContractProperties(null, null, null, null, null, null, null, null))
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
            assertThat(application).hasSingleBean(WeavePersistenceProperties.class);
        });
    }
}
