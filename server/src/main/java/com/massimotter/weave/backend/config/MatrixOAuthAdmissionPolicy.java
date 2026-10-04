package com.massimotter.weave.backend.config;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Admission contract for the separately audience-bound Matrix Client-Server resource. */
public final class MatrixOAuthAdmissionPolicy implements OAuth2TokenValidator<Jwt> {

    public static final String API_SCOPE = "urn:matrix:client:api:*";
    private static final String DEVICE_SCOPE_PREFIX = "urn:matrix:client:device:";
    private static final String WORKSPACE_SCOPE = "weave:workspace";
    private static final Set<String> NON_MATRIX_CLIENTS =
            Set.of("weave-app", "weave-admin-console", "weave-mcp-server",
                    "weave-agent-runtime-admin", "weave-identity-admin", "matrix-mas");

    private final String requiredAudience;
    private final Set<String> allowedClientIds;
    private final boolean configured;

    public MatrixOAuthAdmissionPolicy(
            String requiredAudience,
            String allowedClientIds,
            String facadeBaseUrl,
            String userApiAudience,
            String userApiClientId) {
        this.requiredAudience = trimmed(requiredAudience);
        this.allowedClientIds = Arrays.stream(trimmed(allowedClientIds).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        String expectedAudience = audienceForFacade(facadeBaseUrl);
        this.configured = !this.requiredAudience.isEmpty()
                && this.requiredAudience.equals(expectedAudience)
                && !this.requiredAudience.equals(trimmed(userApiAudience))
                && !this.allowedClientIds.isEmpty()
                && this.allowedClientIds.stream().noneMatch(id ->
                        NON_MATRIX_CLIENTS.contains(id) || id.equals(trimmed(userApiClientId)));
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!configured || token == null || token.getSubject() == null || token.getSubject().isBlank()
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(Instant.now())
                || token.getAudience() == null || token.getAudience().size() != 1
                || !requiredAudience.equals(token.getAudience().getFirst())
                || !authorizedClient(token)
                || !validScopes(token)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Matrix OAuth admission requirements were not met.", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    public static String requiredDeviceId(Jwt token) {
        Object rawScope = token == null ? null : token.getClaims().get("scope");
        if (!(rawScope instanceof String scope) || scope.isBlank()) {
            throw new IllegalArgumentException("A Matrix device scope is required.");
        }
        List<String> devices = Arrays.stream(scope.trim().split("\\s+"))
                .filter(value -> value.startsWith(DEVICE_SCOPE_PREFIX))
                .map(value -> value.substring(DEVICE_SCOPE_PREFIX.length()))
                .toList();
        if (devices.size() != 1 || !devices.getFirst().matches("[A-Za-z0-9._~-]{8,128}")) {
            throw new IllegalArgumentException("Exactly one valid Matrix device scope is required.");
        }
        return devices.getFirst();
    }

    private boolean authorizedClient(Jwt token) {
        List<String> claims = java.util.stream.Stream.of(
                        token.getClaims().get("azp"), token.getClaims().get("client_id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isBlank())
                .toList();
        if ((token.getClaims().containsKey("azp")
                && !(token.getClaims().get("azp") instanceof String))
                || (token.getClaims().containsKey("client_id")
                        && !(token.getClaims().get("client_id") instanceof String))) {
            return false;
        }
        return !claims.isEmpty() && claims.stream().distinct().count() == 1
                && allowedClientIds.contains(claims.getFirst());
    }

    private boolean validScopes(Jwt token) {
        Object rawScope = token.getClaims().get("scope");
        if (!(rawScope instanceof String scope) || scope.isBlank()) {
            return false;
        }
        List<String> values = Arrays.asList(scope.trim().split("\\s+"));
        if (Set.copyOf(values).size() != values.size()
                || !values.contains(WORKSPACE_SCOPE)
                || !values.contains(API_SCOPE)) {
            return false;
        }
        try {
            requiredDeviceId(token);
            return true;
        } catch (IllegalArgumentException invalidDeviceScope) {
            return false;
        }
    }

    private static String audienceForFacade(String facadeBaseUrl) {
        try {
            URI origin = URI.create(trimmed(facadeBaseUrl));
            if (!"https".equalsIgnoreCase(origin.getScheme())
                    || origin.getHost() == null || origin.getHost().isBlank()
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                    || origin.getRawFragment() != null
                    || (origin.getRawPath() != null && !origin.getRawPath().isEmpty()
                            && !"/".equals(origin.getRawPath()))) {
                return "";
            }
            return origin.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                    + origin.getRawAuthority() + "/_matrix/client";
        } catch (IllegalArgumentException invalidUri) {
            return "";
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
