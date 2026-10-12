package com.massimotter.weave.backend.security;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/** Binds a native organization role and canonical data scope to one deployment coordinate. */
public final class DeploymentOrganizationAdmission {
    private final DeploymentOrganizationProperties primary;
    private final OrganizationIdentityContextResolver context;

    public DeploymentOrganizationAdmission(
            DeploymentOrganizationProperties primary, ContextAuthorizationProperties context) {
        this.primary = Objects.requireNonNull(primary);
        this.context = OrganizationIdentityContextResolver.configured(Objects.requireNonNull(context));
    }

    public boolean allows(Jwt jwt) {
        return configuredTenantMatches(jwt) && nativeOrganizationMatches(jwt);
    }

    public boolean allowsReconciliation(Jwt jwt) {
        // A newly invited identity can need reconciliation before Keycloak projects its
        // organization/role. A present organization claim must never bypass the boundary.
        return configuredTenantMatches(jwt)
                && (!jwt.getClaims().containsKey("organization") || nativeOrganizationMatches(jwt));
    }

    public String canonicalOrganizationId() {
        return context.configuredOrganizationId();
    }

    private boolean configuredTenantMatches(Jwt jwt) {
        return primary.configured() && context.matchesConfiguredTenant(jwt);
    }

    private boolean nativeOrganizationMatches(Jwt jwt) {
        return jwt.getClaims().get("organization") instanceof Map<?, ?> organizations
                && organizations.size() == 1
                && organizations.get(primary.keycloakAlias()) instanceof Map<?, ?> organization
                && primary.keycloakId().equals(organization.get("id"));
    }
}
