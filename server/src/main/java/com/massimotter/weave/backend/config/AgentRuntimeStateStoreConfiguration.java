package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.adapter.JpaEncryptedRuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateDeletionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateGenerationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeStateHeadJpaRepository;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentRuntimeStateStoreProperties.class)
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jpa'"
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
    JpaEncryptedRuntimeStateStore jpaEncryptedRuntimeStateStore(
            RuntimeStateHeadJpaRepository heads,
            RuntimeStateGenerationJpaRepository generations,
            RuntimeStateDeletionJpaRepository deletions,
            PlatformTransactionManager weaveTransactionManager,
            RuntimeStateKeyWrapper keyWrapper,
            AgentRuntimeStateStoreProperties properties) {
        return new JpaEncryptedRuntimeStateStore(
                heads,
                generations,
                deletions,
                weaveTransactionManager,
                keyWrapper,
                new SecureRandom(),
                Clock.systemUTC(),
                properties.requiredChunkBytes(),
                properties.requiredMaximumGenerationBytes());
    }
}
