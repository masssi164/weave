package com.massimotter.weave.mcp;

import java.util.Arrays;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class McpAuthorizationConfiguration {

    @Bean
    McpExchangedTokenPolicy mcpExchangedTokenPolicy(McpWorkloadProperties properties) {
        return new McpExchangedTokenPolicy(
                properties.backendResourceUri().toString(),
                properties.exchangeClientId());
    }

    @Bean
    @ConditionalOnMissingBean(McpBackendTokenExchange.class)
    McpBackendTokenExchange mcpBackendTokenExchange(
            McpWorkloadProperties properties,
            JsonMapper mapper) {
        return new HttpMcpBackendTokenExchange(properties, mapper);
    }

    @Bean
    McpExchangedJwtDecoder mcpExchangedJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties) {
        String issuer = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException("The MCP exchanged-token issuer is required");
        }
        String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
        NimbusJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).validateType(false).build()
                : NimbusJwtDecoder.withIssuerLocation(issuer).validateType(false).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                new McpAccessTokenTypeValidator()));
        return decoder::decode;
    }

    @Bean
    McpExchangedTokenAuthenticator mcpExchangedTokenAuthenticator(
            McpExchangedJwtDecoder decoder,
            McpExchangedTokenPolicy policy) {
        return new McpExchangedTokenAuthenticator(decoder, policy);
    }

    @Bean("mcpWorkloadBoundaryHealthIndicator")
    HealthIndicator mcpWorkloadBoundaryHealthIndicator(
            McpWorkloadProperties properties,
            JsonMapper mapper) {
        return () -> {
            byte[] credential = null;
            try {
                credential = HttpMcpBackendTokenExchange.readCredential(properties.exchangeClientKeyFile());
                PrivateKeyJwtClientAssertion.validate(properties, mapper, credential);
                return Health.up()
                        .withDetail("authorizationPosture", "guarded-fixed-resource")
                        .withDetail("tokenExchange", "configured")
                        .withDetail("credential", "mounted-private-jwk-secretref")
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
