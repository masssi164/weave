package com.massimotter.weave.backend.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return configuredDecoder(resourceServerProperties, jwt -> OAuth2TokenValidatorResult.success());
        }
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUri);
        if (weaveSecurityProperties.hasRequiredAudience()) {
            validator = new DelegatingOAuth2TokenValidator<>(
                    validator,
                    requiredAudienceValidator(weaveSecurityProperties.requiredAudience()));
        }
        if (weaveSecurityProperties.hasRequiredAuthorizedParty()) {
            validator = new DelegatingOAuth2TokenValidator<>(
                    validator,
                    requiredAuthorizedPartyValidator(weaveSecurityProperties.requiredAuthorizedParty()));
        }
        return configuredDecoder(resourceServerProperties, validator);
    }

    @Bean("filesMcpWorkloadJwtDecoder")
    @ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
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

    static JwtDecoder configuredDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            OAuth2TokenValidator<Jwt> validator) {
        return configuredDecoder(resourceServerProperties, validator, null);
    }

    static JwtDecoder configuredRfc9068Decoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            OAuth2TokenValidator<Jwt> validator) {
        return configuredDecoder(resourceServerProperties, validator, new JOSEObjectType("at+jwt"));
    }

    private static JwtDecoder configuredDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            OAuth2TokenValidator<Jwt> validator,
            JOSEObjectType requiredType) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return token -> {
                throw new BadJwtException("The backend JWT issuer is not configured.");
            };
        }
        String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
        var builder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                : NimbusJwtDecoder.withIssuerLocation(issuerUri);
        if (requiredType != null) {
            builder.jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                    new DefaultJOSEObjectTypeVerifier<>(requiredType)));
        }
        NimbusJwtDecoder jwtDecoder = builder.build();
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    static OAuth2TokenValidator<Jwt> rfc9068AccessTokenTypeValidator() {
        return jwt -> {
            Object type = jwt == null ? null : jwt.getHeaders().get("typ");
            if (type instanceof String value && "at+jwt".equalsIgnoreCase(value)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token", "The workload resource accepts only RFC 9068 at+jwt access tokens."));
        };
    }

    static OAuth2TokenValidator<Jwt> requiredAudienceValidator(String requiredAudience) {
        String normalizedRequiredAudience = requiredAudience.trim();
        return jwt -> hasRequiredAudience(jwt, normalizedRequiredAudience);
    }

    static OAuth2TokenValidator<Jwt> exactAudienceValidator(Set<String> requiredAudiences) {
        Set<String> normalized = requiredAudiences.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one exact audience is required");
        }
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            if (audiences != null
                    && audiences.size() == normalized.size()
                    && Set.copyOf(audiences).equals(normalized)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token", "The token audience set is not exact."));
        };
    }

    static OAuth2TokenValidator<Jwt> requiredAuthorizedPartyValidator(String requiredAuthorizedParty) {
        String normalizedRequiredAuthorizedParty = requiredAuthorizedParty.trim();
        return jwt -> hasRequiredAuthorizedParty(jwt, normalizedRequiredAuthorizedParty);
    }

    static OAuth2TokenValidator<Jwt> exactScopesValidator(Set<String> requiredScopes) {
        Set<String> expected = Set.copyOf(requiredScopes);
        return jwt -> {
            String claim = jwt.getClaimAsString("scope");
            String[] values = claim == null || claim.isBlank()
                    ? new String[0]
                    : claim.trim().split("\\s+");
            Set<String> actual = new java.util.LinkedHashSet<>(List.of(values));
            if (actual.size() == values.length && actual.equals(expected)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token", "The token scope set is not exact."));
        };
    }

    static OAuth2TokenValidator<Jwt> allowedAuthorizedPartiesValidator(List<String> allowedAuthorizedParties) {
        List<String> normalized = allowedAuthorizedParties.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return jwt -> hasAllowedAuthorizedParty(jwt, normalized);
    }

    private static OAuth2TokenValidatorResult hasRequiredAudience(Jwt jwt, String requiredAudience) {
        List<String> audiences = jwt.getAudience();
        if (audiences != null && audiences.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(
                error("invalid_token",
                        "The token is missing the required audience '" + requiredAudience + "'."));
    }

    private static OAuth2TokenValidatorResult hasRequiredAuthorizedParty(Jwt jwt, String requiredAuthorizedParty) {
        return hasAllowedAuthorizedParty(jwt, List.of(requiredAuthorizedParty));
    }

    private static OAuth2TokenValidatorResult hasAllowedAuthorizedParty(Jwt jwt, List<String> allowedAuthorizedParties) {
        List<String> authorizedPartyClaims = Stream.of(
                        jwt.getClaimAsString("azp"),
                        jwt.getClaimAsString("client_id"))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        if (authorizedPartyClaims.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token",
                            "The token is missing a required authorized party/client ID."));
        }

        boolean allClaimsMatch = authorizedPartyClaims.stream()
                .allMatch(allowedAuthorizedParties::contains);
        boolean claimsAgree = authorizedPartyClaims.stream().distinct().count() == 1;
        if (allClaimsMatch && claimsAgree) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(
                error("invalid_token",
                        "The token authorized party/client ID is not an allowed Weave caller."));
    }

    private static org.springframework.security.oauth2.core.OAuth2Error error(String code, String description) {
        return new org.springframework.security.oauth2.core.OAuth2Error(code, description, null);
    }
}
