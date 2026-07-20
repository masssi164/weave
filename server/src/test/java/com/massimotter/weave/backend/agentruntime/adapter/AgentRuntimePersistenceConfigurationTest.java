package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import com.massimotter.weave.backend.config.WeavePersistenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentRuntimePersistenceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WeavePersistenceConfiguration.class))
            .withPropertyValues(
                    "weave.agent-runtime.storage.mode=jdbc",
                    "weave.persistence.jdbc.url=jdbc:h2:mem:arc-config;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "weave.persistence.jdbc.username=sa",
                    "weave.persistence.jdbc.password=");

    @Test
    void jdbcModeMigratesAndPublishesBothRuntimeRepositories() {
        context.run(application -> {
            assertThat(application).hasSingleBean(JdbcRuntimeCellRepository.class);
            assertThat(application).hasSingleBean(JdbcRuntimeCommandRepository.class);
            assertThat(application).hasSingleBean(WeavePersistenceProperties.class);
        });
    }
}
