package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AgentRuntimePolicyProperties.class, AgentRuntimeProfileSigningProperties.class})
@ConditionalOnProperty(name = "weave.agent-runtime.policy.enabled", havingValue = "true")
public class AgentRuntimePolicyConfiguration {

    @Bean
    RuntimePolicyAuthority runtimePolicyAuthority(
            AgentRuntimePolicyProperties policy,
            AgentRuntimeProfileSigningProperties signing,
            ObjectMapper objectMapper) {
        return new FileRuntimePolicyAuthority(
                policy.requiredFile(), objectMapper, signing.maximumProfileTtl());
    }
}
