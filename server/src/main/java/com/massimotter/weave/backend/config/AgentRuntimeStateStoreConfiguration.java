package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcEncryptedRuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentRuntimeStateStoreProperties.class)
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'"
                + " && '${weave.agent-runtime.state-store.enabled:false}' == 'true'")
public class AgentRuntimeStateStoreConfiguration {

    @Bean
    FileRuntimeStateKeyWrapper fileRuntimeStateKeyWrapper(
            AgentRuntimeStateStoreProperties properties,
            ObjectMapper objectMapper) {
        return new FileRuntimeStateKeyWrapper(
                properties.requiredWrappingKeyRoot(),
                objectMapper,
                Clock.systemUTC(),
                new SecureRandom());
    }

    @Bean
    JdbcEncryptedRuntimeStateStore jdbcEncryptedRuntimeStateStore(
            JdbcTemplate weaveJdbcTemplate,
            PlatformTransactionManager weaveTransactionManager,
            RuntimeStateKeyWrapper keyWrapper,
            AgentRuntimeStateStoreProperties properties) {
        return new JdbcEncryptedRuntimeStateStore(
                weaveJdbcTemplate,
                weaveTransactionManager,
                keyWrapper,
                new SecureRandom(),
                Clock.systemUTC(),
                properties.requiredChunkBytes(),
                properties.requiredMaximumGenerationBytes());
    }
}
