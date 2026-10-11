package com.massimotter.weave.backend.security;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Checks the selected organization role for every Admin API request. */
public final class AdminApiAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Set<String> PRODUCT_ROLES = Set.of("owner", "admin", "member", "guest");
    private final DeploymentOrganizationAdmission organizationAdmission;

    public AdminApiAuthorizationManager(DeploymentOrganizationAdmission organizationAdmission) {
        this.organizationAdmission = java.util.Objects.requireNonNull(organizationAdmission);
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "SCOPE_weave:workspace".equals(authority.getAuthority()))) {
            return new AuthorizationDecision(false);
        }

        List<String> roles = NativeOrganizationClaims.clientRoles(jwtAuthentication.getToken(), "weave-app");
        List<String> productRoles = roles.stream().filter(PRODUCT_ROLES::contains).toList();
        return new AuthorizationDecision(productRoles.size() == 1
                && organizationAdmission.allows(jwtAuthentication.getToken())
                && (productRoles.contains("owner") || productRoles.contains("admin")));
    }
}
