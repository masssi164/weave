package com.massimotter.weave.mcp;

import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class McpAuthorizationConfiguration {

    @Bean
    @ConditionalOnMissingBean(McpBackendTokenExchange.class)
    McpBackendTokenExchange mcpBackendTokenExchange(
            McpWorkloadProperties properties,
            JsonMapper mapper) {
        return new HttpMcpBackendTokenExchange(properties, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(McpBackendContextResolver.class)
    McpBackendContextResolver mcpBackendContextResolver(
            McpWorkloadProperties properties,
            JsonMapper mapper) {
        return new HttpMcpBackendContextResolver(properties, mapper);
    }

    @Bean("mcpWorkloadBoundaryHealthIndicator")
    HealthIndicator mcpWorkloadBoundaryHealthIndicator(McpWorkloadProperties properties) {
        return () -> {
            byte[] credential = null;
            try {
                credential = HttpMcpBackendTokenExchange.readCredential(properties.exchangeClientSecretFile());
                return Health.up()
                        .withDetail("authorizationPosture", "guarded-fixed-resource")
                        .withDetail("tokenExchange", "configured")
                        .withDetail("credential", "mounted-secretref")
                        .build();
            } catch (McpAdmissionException unavailable) {
                return Health.down()
                        .withDetail("authorizationPosture", "blocked")
                        .withDetail("tokenExchange", "credential-unavailable")
                        .build();
            } finally {
                if (credential != null) {
                    Arrays.fill(credential, (byte) 0);
                }
            }
        };
    }
}
