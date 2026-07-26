package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.Ed25519JcsRuntimeProfileSigner;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeProfileSigningKeyStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigner;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentRuntimeProfileSigningProperties.class)
@ConditionalOnExpression(
        "'${weave.agent-runtime.storage.mode:disabled}' == 'jpa'"
                + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'")
public class AgentRuntimeProfileSigningConfiguration {

    @Bean
    FileRuntimeProfileSigningKeyStore fileRuntimeProfileSigningKeyStore(
            AgentRuntimeProfileSigningProperties properties,
            ObjectMapper objectMapper) {
        return new FileRuntimeProfileSigningKeyStore(
                properties.requiredSecretRoot(),
                objectMapper,
                Clock.systemUTC(),
                new SecureRandom(),
                properties.keyLifetime(),
                properties.trustOverlap(),
                properties.maximumProfileTtl());
    }

    @Bean
    RuntimeProfileSigner runtimeProfileSigner(
            ObjectMapper objectMapper,
            FileRuntimeProfileSigningKeyStore keys) {
        return new Ed25519JcsRuntimeProfileSigner(objectMapper, keys);
    }
}
