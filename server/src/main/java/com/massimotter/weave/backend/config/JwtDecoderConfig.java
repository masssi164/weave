package com.massimotter.weave.backend.config;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties) {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (!StringUtils.hasText(issuerUri)) {
            return token -> {
                throw new BadJwtException("The backend JWT issuer is not configured.");
            };
        }

        String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
        NimbusJwtDecoder jwtDecoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

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

        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    static OAuth2TokenValidator<Jwt> requiredAudienceValidator(String requiredAudience) {
        String normalizedRequiredAudience = requiredAudience.trim();
        return jwt -> hasRequiredAudience(jwt, normalizedRequiredAudience);
    }

    static OAuth2TokenValidator<Jwt> requiredAuthorizedPartyValidator(String requiredAuthorizedParty) {
        String normalizedRequiredAuthorizedParty = requiredAuthorizedParty.trim();
        return jwt -> hasRequiredAuthorizedParty(jwt, normalizedRequiredAuthorizedParty);
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
                            "The token is missing required authorized party/client ID '"
                                    + requiredAuthorizedParty + "'."));
        }

        boolean allClaimsMatch = authorizedPartyClaims.stream()
                .allMatch(requiredAuthorizedParty::equals);
        if (allClaimsMatch) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(
                error("invalid_token",
                        "The token authorized party/client ID must be '" + requiredAuthorizedParty + "'."));
    }

    private static org.springframework.security.oauth2.core.OAuth2Error error(String code, String description) {
        return new org.springframework.security.oauth2.core.OAuth2Error(code, description, null);
    }
}
