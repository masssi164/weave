package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

final class OrganizationIdentityContextFactory {

    private static final List<String> WEAVE_ORG_ROLES = List.of("owner", "admin", "operator", "member", "guest");

    private static final int MAX_IDENTITY_ISSUER_LENGTH = com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_ISSUER_LENGTH;
    private static final int MAX_IDENTITY_SUBJECT_LENGTH = com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_SUBJECT_LENGTH;

    private OrganizationIdentityContextFactory() {
    }

    static OrganizationIdentityContext fromJwt(Jwt jwt) {
        String subject = requireSubject(jwt);
        String issuer = issuer(jwt);
        String organizationId = firstText(
                claim(jwt, "weave_tenant"),
                claim(jwt, "weave_tenant_id"),
                claim(jwt, "tenant"),
                claim(jwt, "tenant_id"),
                claim(jwt, "tid"),
                claim(jwt, "org_id"),
                "weave-dogfood");
        String primaryIdentityKey = "issuer+subject:" + issuer + "#" + subject;
        String accountId = stableAccountId(primaryIdentityKey);
        List<String> roles = canonicalRoles(jwt);
        List<String> groups = stringClaims(jwt, "weave_groups", "groups");
        List<String> contextRoles = stringClaims(jwt, "weave_context_roles").stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .sorted()
                .toList();

        return new OrganizationIdentityContext(
                organizationId,
                issuer,
                subject,
                primaryIdentityKey,
                accountId,
                roles,
                groups,
                contextRoles,
                providerRoleMappings(roles, groups));
    }

    private static String requireSubject(Jwt jwt) {
        if (jwt == null || !hasText(jwt.getSubject())) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "missing-subject",
                    "Authenticated token is missing a stable subject.",
                    Map.of("claim", "sub"));
        }
        String subject = jwt.getSubject().trim();
        validateIdentitySegment(subject, "sub", MAX_IDENTITY_SUBJECT_LENGTH);
        return subject;
    }

    private static String issuer(Jwt jwt) {
        if (jwt != null && jwt.getIssuer() != null && hasText(jwt.getIssuer().toString())) {
            String issuer = jwt.getIssuer().toString().trim();
            validateIdentitySegment(issuer, "iss", MAX_IDENTITY_ISSUER_LENGTH);
            return issuer;
        }
        String issuer = Optional.ofNullable(claim(jwt, "iss"))
                .orElseThrow(() -> new ApiErrorException(
                        HttpStatus.BAD_REQUEST,
                        "missing-issuer",
                        "Authenticated token is missing a stable issuer.",
                        Map.of("claim", "iss")));
        validateIdentitySegment(issuer, "iss", MAX_IDENTITY_ISSUER_LENGTH);
        return issuer;
    }

    private static void validateIdentitySegment(String value, String claim, int maxLength) {
        if (value.length() > maxLength || value.indexOf('#') >= 0 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-identity-claim",
                    "Authenticated token identity claims are not support-safe issuer+subject components.",
                    Map.of("claim", claim));
        }
    }

    private static List<String> canonicalRoles(Jwt jwt) {
        return Stream.of(
                        stringClaims(jwt, "weave_roles"),
                        stringClaims(jwt, "roles"),
                        realmRoles(jwt),
                        clientRoles(jwt))
                .flatMap(Collection::stream)
                .map(role -> role.toLowerCase(Locale.ROOT))
                .filter(WEAVE_ORG_ROLES::contains)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt == null ? null : jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleValues)) {
            return List.of();
        }
        return stringValues(roleValues);
    }

    private static List<String> clientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt == null ? null : jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        Object weaveClient = Stream.of("weave", "weave-app", claim(jwt, "azp"), claim(jwt, "client_id"))
                .filter(OrganizationIdentityContextFactory::hasText)
                .map(resourceAccess::get)
                .filter(Map.class::isInstance)
                .findFirst()
                .orElse(null);
        if (!(weaveClient instanceof Map<?, ?> clientAccess)) {
            return List.of();
        }
        Object roles = clientAccess.get("roles");
        if (!(roles instanceof Collection<?> roleValues)) {
            return List.of();
        }
        return stringValues(roleValues);
    }

    private static List<String> providerRoleMappings(List<String> roles, List<String> groups) {
        return Stream.concat(
                        roles.stream().map(role -> "role_claim:" + role),
                        groups.stream().map(group -> "group_claim:" + group))
                .sorted()
                .toList();
    }

    private static List<String> stringClaims(Jwt jwt, String... claimNames) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (jwt == null) {
            return List.of();
        }
        for (String claimName : claimNames) {
            Object claim = jwt.getClaims().get(claimName);
            if (claim instanceof Collection<?> collection) {
                values.addAll(stringValues(collection));
            } else if (claim instanceof String text && hasText(text)) {
                values.add(text.trim());
            }
        }
        return values.stream()
                .filter(OrganizationIdentityContextFactory::hasText)
                .map(String::trim)
                .sorted()
                .toList();
    }

    private static List<String> stringValues(Collection<?> values) {
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(OrganizationIdentityContextFactory::hasText)
                .toList();
    }

    private static String claim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        return value instanceof String text && hasText(text) ? text.trim() : null;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String stableAccountId(String primaryIdentityKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(primaryIdentityKey.getBytes(StandardCharsets.UTF_8));
            return "acct_" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is required for stable account identifiers", exception);
        }
    }
}
