package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimePolicyAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyAuthority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AgentRuntimePolicyProperties.class, AgentRuntimeProfileSigningProperties.class})
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jpa'"
                + " && '${weave.agent-runtime.policy.enabled:false}' == 'true'")
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
