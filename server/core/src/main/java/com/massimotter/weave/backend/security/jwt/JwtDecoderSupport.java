package com.massimotter.weave.backend.security.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Shared bounded JWT verification and exact claim policies used by human and workload resources. */
public final class JwtDecoderSupport {
    private JwtDecoderSupport() {}

    public static JwtDecoder configuredDecoder(
            OAuth2ResourceServerProperties resourceServerProperties,
            OAuth2TokenValidator<Jwt> validator) {
        return configuredDecoder(resourceServerProperties, validator, null);
    }

    public static JwtDecoder configuredRfc9068Decoder(
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
        if (!StringUtils.hasText(jwkSetUri)) {
            // Issuer discovery performs I/O inside Nimbus build(). Resolve once on first
            // authenticated use so AOT/schema metadata construction stays offline.
            return new SupplierJwtDecoder(() -> buildDecoder(issuerUri, jwkSetUri, validator, requiredType));
        }
        return buildDecoder(issuerUri, jwkSetUri, validator, requiredType);
    }

    private static JwtDecoder buildDecoder(
            String issuerUri,
            String jwkSetUri,
            OAuth2TokenValidator<Jwt> validator,
            JOSEObjectType requiredType) {
        var builder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                : NimbusJwtDecoder.withIssuerLocation(issuerUri);
        // Keep signing-key retrieval bounded while allowing the identity service
        // to respond under concurrent native authentication. Nimbus keeps its
        // existing key cache; signature, claims and revocation validation follow.
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        builder.restOperations(new RestTemplate(requestFactory));
        if (requiredType != null) {
            builder.jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                    new DefaultJOSEObjectTypeVerifier<>(requiredType)));
        }
        NimbusJwtDecoder jwtDecoder = builder.build();
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    public static OAuth2TokenValidator<Jwt> rfc9068AccessTokenTypeValidator() {
        return jwt -> {
            Object type = jwt == null ? null : jwt.getHeaders().get("typ");
            if (type instanceof String value && "at+jwt".equalsIgnoreCase(value)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token", "The workload resource accepts only RFC 9068 at+jwt access tokens."));
        };
    }

    public static OAuth2TokenValidator<Jwt> requiredHumanSubjectValidator() {
        return jwt -> StringUtils.hasText(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        error("invalid_token", "The human access token is missing a subject."));
    }

    public static OAuth2TokenValidator<Jwt> requiredAudienceValidator(String requiredAudience) {
        String normalizedRequiredAudience = requiredAudience.trim();
        return jwt -> hasRequiredAudience(jwt, normalizedRequiredAudience);
    }

    public static OAuth2TokenValidator<Jwt> exactAudienceValidator(Set<String> requiredAudiences) {
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

    public static OAuth2TokenValidator<Jwt> requiredAuthorizedPartyValidator(String requiredAuthorizedParty) {
        String normalizedRequiredAuthorizedParty = requiredAuthorizedParty.trim();
        return jwt -> hasRequiredAuthorizedParty(jwt, normalizedRequiredAuthorizedParty);
    }

    public static OAuth2TokenValidator<Jwt> exactScopesValidator(Set<String> requiredScopes) {
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

    public static OAuth2TokenValidator<Jwt> oneOfExactScopesValidator(Set<String> allowedScopes) {
        Set<String> allowed = Set.copyOf(allowedScopes);
        return jwt -> {
            String claim = jwt.getClaimAsString("scope");
            if (claim != null && allowed.contains(claim.trim())
                    && claim.trim().split("\\s+").length == 1) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    error("invalid_token", "The token scope is not an admitted exact domain scope."));
        };
    }

    public static OAuth2TokenValidator<Jwt> allowedAuthorizedPartiesValidator(List<String> allowedAuthorizedParties) {
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
