package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationRequest;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import com.massimotter.weave.backend.calendar.domain.CalendarPrincipal;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Shared Calendar authorization and actor identity; no provider or transport state. */
public final class CalendarFacadeAuthorization {
    private static final String DEFAULT_CONTEXT_ID = "workspace-default";
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final OrganizationIdentityContextResolver identityContexts;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public CalendarFacadeAuthorization(ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContexts, WorkspaceCapabilityService workspaceCapabilityService) {
        this.contextAuthorizationPort = Objects.requireNonNull(contextAuthorizationPort);
        this.contextAuthorizationProperties = Objects.requireNonNull(contextAuthorizationProperties);
        this.identityContexts = Objects.requireNonNull(identityContexts);
        this.workspaceCapabilityService = Objects.requireNonNull(workspaceCapabilityService);
    }

    CalendarPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar"));
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            return new CalendarPrincipal(jwt.getSubject(), userId);
        }
        return new CalendarPrincipal(authentication.getName(), authentication.getName());
    }

    void requireContextPermission(
            CalendarScopeResponse scope,
            ContextPermission permission,
            String operation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar", "operation", operation));
        }
        requireCalendarCapability(authentication, permission, operation);
        PrincipalContext principalContext = principalContext(authentication);
        String contextId = contextId(scope);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principalContext.tenantId(),
                contextId,
                principalContext.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "calendar-forbidden",
                    "Calendar access is not allowed for this Context/Space.",
                    Map.of(
                            "module", "calendar",
                            "operation", operation,
                            "reason", decision.reason(),
                            "contextId", contextId,
                            "permission", permission.name().toLowerCase()));
        }
    }

    private void requireCalendarCapability(
            Authentication authentication,
            ContextPermission permission,
            String operation) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw invalidAuthentication("JWT principal is required");
        }
        String required = permission == ContextPermission.VIEW
                ? "calendar.read"
                : "calendar.manage_events";
        workspaceCapabilityService.requireCapability(jwt, required, "calendar", operation);
    }

    private PrincipalContext principalContext(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return new PrincipalContext(
                    identityContexts.resolve(jwt).organizationId(),
                    jwtPrincipalRef(jwt));
        }
        String principalRef = contextAuthorizationProperties.principalRef(authentication.getName());
        if (principalRef == null) {
            throw invalidAuthentication("principal claim is missing");
        }
        return new PrincipalContext(contextAuthorizationProperties.defaultTenantId(), principalRef);
    }

    PrincipalContext currentPrincipalContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar"));
        }
        return principalContext(authentication);
    }

    private String jwtPrincipalRef(Jwt jwt) {
        String configuredClaim = jwtClaim(jwt, contextAuthorizationProperties.principalClaim());
        if (configuredClaim != null) {
            return contextAuthorizationProperties.principalRef(configuredClaim);
        }
        String principalRef = contextAuthorizationProperties.principalRef(jwt.getSubject());
        if (principalRef == null) {
            throw invalidAuthentication("principal claim is missing");
        }
        return principalRef;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiErrorException invalidAuthentication(String reason) {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication is required.",
                Map.of("module", "calendar", "reason", reason));
    }

    String contextId(CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = scope == null ? CalendarScopeResponse.workspace() : scope;
        return switch (normalizedScope.type() == null ? "workspace" : normalizedScope.type()) {
            case "team" -> "team-" + firstNonBlank(normalizedScope.teamId(), "engineering");
            case "channel" -> "channel-" + firstNonBlank(normalizedScope.channelId(), "engineering-general");
            default -> DEFAULT_CONTEXT_ID;
        };
    }

    record PrincipalContext(String tenantId, String principalRef) {
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

}
