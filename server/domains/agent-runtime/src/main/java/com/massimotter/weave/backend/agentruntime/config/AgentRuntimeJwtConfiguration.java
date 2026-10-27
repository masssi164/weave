package com.massimotter.weave.backend.agentruntime.config;

import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.configuredDecoder;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.configuredRfc9068Decoder;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.exactAudienceValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredAudienceValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredAuthorizedPartyValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredHumanSubjectValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.rfc9068AccessTokenTypeValidator;

import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import java.util.Set;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

/** Exact future control-plane bearer decoders, isolated from release MCP authorization. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        PlatformContractProperties.class,
        WeaveSecurityProperties.class,
        OAuth2ResourceServerProperties.class
})
public class AgentRuntimeJwtConfiguration {

    @Bean("agentRuntimeAdminJwtDecoder")
    @ConditionalOnExpression(
            "'${weave.agent-runtime.workload-identity.enabled:false}' == 'true'"
                    + " && '${weave.agent-runtime.policy.enabled:false}' == 'true'"
                    + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'"
                    + " && '${weave.agent-runtime.state-store.enabled:false}' == 'true'")
    JwtDecoder agentRuntimeAdminJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return configuredDecoder(resourceServerProperties, jwt -> OAuth2TokenValidatorResult.success());
        }
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                requiredHumanSubjectValidator(),
                exactAudienceValidator(Set.of(weaveSecurityProperties.requiredAudience())),
                requiredAuthorizedPartyValidator(AgentRuntimeAdminSecurityConfiguration.CLIENT_ID));
        return configuredDecoder(resourceServerProperties, validator);
    }

    @Bean("agentRuntimeProfileJwtDecoder")
    JwtDecoder agentRuntimeProfileJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            PlatformContractProperties platform) {
        String issuer = resourceServerProperties.getJwt().getIssuerUri();
        OAuth2TokenValidator<Jwt> validator = StringUtils.hasText(issuer)
                ? new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtIssuerValidator(issuer),
                        rfc9068AccessTokenTypeValidator(),
                        requiredAudienceValidator(platform.agentRuntimeControlResource()))
                : jwt -> OAuth2TokenValidatorResult.success();
        return configuredRfc9068Decoder(resourceServerProperties, validator);
    }

}
