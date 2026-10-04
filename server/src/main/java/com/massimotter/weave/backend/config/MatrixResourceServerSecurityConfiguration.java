package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.WorkspaceAccessAuthorizationManager;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;

/** Separate Matrix bearer admission; the User and Admin API decoders never serve this route. */
@Configuration(proxyBeanMethods = false)
public class MatrixResourceServerSecurityConfiguration {

    @Bean
    MatrixOAuthAdmissionPolicy matrixOAuthAdmissionPolicy(
            @Value("${weave.matrix.oidc.required-audience:}") String requiredAudience,
            @Value("${weave.matrix.oidc.allowed-client-ids:}") String allowedClientIds,
            @Value("${weave.matrix.facade.base-url:}") String facadeBaseUrl,
            @Value("${weave.security.required-audience:https://api.weave.test/api}") String userApiAudience,
            @Value("${weave.security.client-id:weave-app}") String userApiClientId) {
        return new MatrixOAuthAdmissionPolicy(
                requiredAudience, allowedClientIds, facadeBaseUrl,
                userApiAudience, userApiClientId);
    }

    @Bean("matrixJwtDecoder")
    JwtDecoder matrixJwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            MatrixOAuthAdmissionPolicy policy) {
        String issuer = resourceServerProperties.getJwt().getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            return JwtDecoderConfig.configuredDecoder(resourceServerProperties, policy);
        }
        return JwtDecoderConfig.configuredDecoder(resourceServerProperties,
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer), policy));
    }

    @Bean
    @Order(1)
    SecurityFilterChain matrixClientServerSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("matrixJwtDecoder") JwtDecoder decoder,
            @Qualifier("jwtAuthenticationConverter") Converter<Jwt, ? extends AbstractAuthenticationToken> converter,
            MatrixOAuthAdmissionPolicy policy,
            WorkspaceAccessAuthorizationManager workspaceAccess) throws Exception {
        var authenticationEntryPoint =
                (org.springframework.security.web.AuthenticationEntryPoint) (request, response, error) ->
                        matrixError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "M_UNKNOWN_TOKEN", "A valid Matrix access token is required.");
        var accessDeniedHandler =
                (org.springframework.security.web.access.AccessDeniedHandler) (request, response, error) ->
                        matrixError(response, HttpServletResponse.SC_FORBIDDEN,
                                "M_FORBIDDEN", "Current organization access is required.");
        return http
                .securityMatcher("/_matrix/client/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/_matrix/client/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/_matrix/client/versions",
                                "/_matrix/client/v3/versions",
                                "/_matrix/client/v1/auth_metadata").permitAll()
                        .anyRequest().access((authenticationSupplier, context) -> {
                            var authentication = authenticationSupplier.get();
                            if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
                                    || policy.validate(jwt.getToken()).hasErrors()) {
                                return new AuthorizationDecision(false);
                            }
                            return workspaceAccess.authorize(authenticationSupplier, context);
                        }))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    private static void matrixError(
            HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"errcode\":\"" + code + "\",\"error\":\"" + message + "\"}");
    }
}
