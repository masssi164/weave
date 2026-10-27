package com.massimotter.weave.backend.files.config;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.util.StringUtils;
import java.util.Set;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.configuredRfc9068Decoder;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.rfc9068AccessTokenTypeValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.exactAudienceValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.requiredAuthorizedPartyValidator;
import static com.massimotter.weave.backend.security.jwt.JwtDecoderSupport.exactScopesValidator;

import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;

import com.massimotter.weave.backend.security.device.DeviceCredentialAuthenticationFilter;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/**
 * Closed Files read boundary for direct members and exchanged Weaver workloads.
 *
 * <p>Each token is fully decoded and validated against one exact profile. The general API decoder
 * remains member-only and never accepts an MCP edge token.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
public class FilesWebDavSecurityConfiguration {
    private static final WebExpressionAuthorizationManager FILES_ACCESS =
            new WebExpressionAuthorizationManager(
                    "hasAuthority('SCOPE_weave:workspace') or hasAuthority('SCOPE_files.read')");

    @Bean("filesMcpWorkloadJwtDecoder")
    JwtDecoder filesMcpWorkloadJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return configuredRfc9068Decoder(
                    resourceServerProperties,
                    jwt -> OAuth2TokenValidatorResult.success());
        }
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuerUri),
                rfc9068AccessTokenTypeValidator(),
                exactAudienceValidator(Set.of(weaveSecurityProperties.requiredAudience())),
                requiredAuthorizedPartyValidator("weave-mcp-server"),
                exactScopesValidator(Set.of("files.read")));
        return configuredRfc9068Decoder(resourceServerProperties, validator);
    }

    @Bean("filesWebDavJwtDecoder")
    JwtDecoder filesWebDavJwtDecoder(
            @Qualifier("jwtDecoder") JwtDecoder memberDecoder,
            @Qualifier("filesMcpWorkloadJwtDecoder") JwtDecoder workloadDecoder,
            DeploymentOrganizationAdmission organizationAdmission) {
        return token -> {
            Jwt member;
            try {
                member = memberDecoder.decode(token);
            } catch (JwtException memberRejected) {
                try {
                    return workloadDecoder.decode(token);
                } catch (JwtException workloadRejected) {
                    workloadRejected.addSuppressed(memberRejected);
                    throw workloadRejected;
                }
            }
            // A decoded human identity cannot become a workload after admission denial.
            if (!organizationAdmission.allows(member)) {
                throw new BadJwtException("The member token does not belong to this deployment organization.");
            }
            return member;
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain filesWebDavSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("filesWebDavJwtDecoder") JwtDecoder decoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<DeviceCredentialAuthenticationFilter> deviceCredentials) throws Exception {
        http
                .securityMatcher("/dav/files", "/dav/files/**", "/api/files/items", "/api/files/items/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/files/items", "/api/files/items/**")
                                .access(FILES_ACCESS)
                        .requestMatchers("/api/files/items", "/api/files/items/**")
                                .hasAuthority("SCOPE_weave:workspace")
                        .anyRequest().access(FILES_ACCESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(decoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        DeviceCredentialAuthenticationFilter deviceFilter = deviceCredentials.getIfAvailable();
        if (deviceFilter != null) {
            http.addFilterBefore(deviceFilter, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
