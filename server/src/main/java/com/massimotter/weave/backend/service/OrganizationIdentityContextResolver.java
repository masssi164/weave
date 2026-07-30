package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.security.NativeOrganizationClaims;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the one canonical, deployment-configured identity context for a human request.
 *
 * <p>This bean is the only owner of tenant-claim precedence. Provider adapters and product
 * services consume the canonical result and must not invent tenant aliases or local fallbacks.
 */
@Component
public final class OrganizationIdentityContextResolver {

    private static final List<String> WEAVE_ORG_ROLES =
            List.of("owner", "admin", "member", "guest");
    private static final int MAX_IDENTITY_ISSUER_LENGTH =
            com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_ISSUER_LENGTH;
    private static final int MAX_IDENTITY_SUBJECT_LENGTH =
            com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_SUBJECT_LENGTH;

    private final ContextAuthorizationProperties properties;

    @Autowired
    public OrganizationIdentityContextResolver(
            ObjectProvider<ContextAuthorizationProperties> propertiesProvider) {
        this(propertiesProvider.getIfAvailable(OrganizationIdentityContextResolver::defaultProperties));
    }

    private OrganizationIdentityContextResolver(ContextAuthorizationProperties properties) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
    }

    /**
     * Test-only convenience using the same defaults as the configuration-properties contract.
     */
    static OrganizationIdentityContextResolver defaults() {
        return new OrganizationIdentityContextResolver(defaultProperties());
    }

    static OrganizationIdentityContextResolver configured(
            ContextAuthorizationProperties properties) {
        return new OrganizationIdentityContextResolver(properties);
    }

    private static ContextAuthorizationProperties defaultProperties() {
        return new ContextAuthorizationProperties(
                null, null, null, null, null, null, null, null);
    }

    public OrganizationIdentityContext resolve(Jwt jwt) {
        String subject = requireSubject(jwt);
        String issuer = issuer(jwt);
        String organizationId = firstText(
                claim(jwt, properties.tenantClaim()),
                claim(jwt, properties.tenantFallbackClaim()),
                properties.defaultTenantId());
        String primaryIdentityKey = IdentityReferences.primaryIdentityKey(issuer, subject);
        String accountId = IdentityReferences.accountId(issuer, subject);
        List<String> roles = canonicalRoles(jwt);
        List<String> groups = selectedOrganizationGroups(jwt);
        List<String> contextRoles = stringClaims(jwt, "weave_context_roles").stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .distinct()
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
        if (value.length() > maxLength
                || value.indexOf('#') >= 0
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-identity-claim",
                    "Authenticated token identity claims are not support-safe issuer+subject components.",
                    Map.of("claim", claim));
        }
    }

    private static List<String> canonicalRoles(Jwt jwt) {
        return clientRoles(jwt).stream()
                .map(role -> role.toLowerCase(Locale.ROOT))
                .filter(WEAVE_ORG_ROLES::contains)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> clientRoles(Jwt jwt) {
        return NativeOrganizationClaims.clientRoles(jwt, "weave-app");
    }

    private static List<String> providerRoleMappings(List<String> roles, List<String> groups) {
        return Stream.concat(
                        roles.stream().map(role -> "role_claim:" + role),
                        groups.stream().map(group -> "group_claim:" + group))
                .sorted()
                .toList();
    }

    private static List<String> selectedOrganizationGroups(Jwt jwt) {
        return NativeOrganizationClaims.groups(jwt);
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
                .filter(OrganizationIdentityContextResolver::hasText)
                .map(String::trim)
                .sorted()
                .toList();
    }

    private static List<String> stringValues(Collection<?> values) {
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(OrganizationIdentityContextResolver::hasText)
                .toList();
    }

    private static String claim(Jwt jwt, String claimName) {
        if (jwt == null || !hasText(claimName)) {
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
        throw new IllegalStateException("Identity tenant resolution is not configured");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
