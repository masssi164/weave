package com.massimotter.weave.backend.security;

import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Closed northbound workspace authorization gate.
 *
 * <p>Human OIDC callers need the exact workspace scope and exactly one product role from the
 * selected native Keycloak organization. Weave-issued DAV device credentials are the only
 * alternative and remain path-bound to the protocol domain that authenticates them.
 */
public final class WorkspaceAccessAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String WORKSPACE_SCOPE_AUTHORITY = "SCOPE_weave:workspace";
    private static final String DEVICE_AUTH_METHOD = "device_credential";
    private static final Set<String> PRODUCT_ROLES =
            Set.of("owner", "admin", "member", "guest");

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !hasWorkspaceScope(authentication)
                || !(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }

        if (isPathBoundDavDeviceCredential(jwtAuthentication, context)) {
            return new AuthorizationDecision(true);
        }

        long selectedProductRoleCount =
                NativeOrganizationClaims.clientRoles(jwtAuthentication.getToken(), "weave-app")
                        .stream()
                        .filter(PRODUCT_ROLES::contains)
                        .count();
        return new AuthorizationDecision(selectedProductRoleCount == 1);
    }

    private boolean hasWorkspaceScope(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> WORKSPACE_SCOPE_AUTHORITY.equals(authority.getAuthority()));
    }

    private boolean isPathBoundDavDeviceCredential(
            JwtAuthenticationToken authentication, RequestAuthorizationContext context) {
        if (!DEVICE_AUTH_METHOD.equals(
                authentication.getToken().getClaimAsString("weave_auth_method"))) {
            return false;
        }
        String path = context.getRequest().getRequestURI();
        return path.equals("/dav/files")
                || path.startsWith("/dav/files/")
                || path.equals("/caldav")
                || path.startsWith("/caldav/");
    }
}
