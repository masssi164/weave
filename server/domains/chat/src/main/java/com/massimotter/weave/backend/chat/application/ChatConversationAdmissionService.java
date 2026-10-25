package com.massimotter.weave.backend.chat.application;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission;

import com.massimotter.weave.backend.chat.port.ChatConversationAdmission.AuthorizedContext;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.chat.domain.ChatAccessDeniedException;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.chat.capability.ChatWorkspaceCapabilityProperties;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationRequest;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public class ChatConversationAdmissionService implements ChatConversationAdmission {
    private static final String DEFAULT_CONTEXT_ID = "workspace-default";
    private static final String DOMAIN = "chat";
    private final ChatWorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final OrganizationIdentityContextResolver identityContexts;
    private final ChatDomainFacadeService chatDomainFacadeService;

    public ChatConversationAdmissionService(ChatWorkspaceCapabilityProperties workspaceCapabilityProperties,
            WorkspaceCapabilityService workspaceCapabilityService, ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            OrganizationIdentityContextResolver identityContexts, ChatDomainFacadeService chatDomainFacadeService) {
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.identityContexts = java.util.Objects.requireNonNull(identityContexts);
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    @Override
    public AuthorizedContext authorize(
            Jwt jwt, String capability, String operation, ContextPermission permission) {
        requireChatReady(jwt, capability, operation);
        return requireContextPermission(jwt, permission);
    }

    @Override
    public String requireConversation(Jwt jwt, String conversationId) {
        try {
            return chatDomainFacadeService.conversation(conversationId, jwt).conversationId();
        } catch (ChatAccessDeniedException | IllegalArgumentException ignored) {
            throw new ApiErrorException(HttpStatus.NOT_FOUND, "chat-not_found",
                    "Chat conversation was not found in this Context/Space.",
                    Map.of("module", DOMAIN, "resource", "conversation", "diagnosticsRedacted", true));
        }
    }

    private void requireChatReady(Jwt jwt, String capability, String operation) {
        requireOrganizationIdentity(jwt);
        workspaceCapabilityService.requireCapability(jwt, capability, DOMAIN, operation);
        CapabilitySettings chat = workspaceCapabilityProperties.chat();
        if (!chat.enabled()) {
            throw chatUnavailable("disabled_by_policy", "Chat is disabled by workspace policy.", operation);
        }
        WorkspaceCapabilityReadiness configured = chat.readiness();
        if (configured == WorkspaceCapabilityReadiness.READY || (configured == null && hasText(chat.dependencyUrl()))) {
            return;
        }
        String impact = configured == WorkspaceCapabilityReadiness.UNAVAILABLE ? "unavailable" : "degraded";
        throw chatUnavailable(impact, "Chat is not ready through the Weave Chat facade.", operation);
    }

    private void requireOrganizationIdentity(Jwt jwt) {
        if (jwt == null) {
            throw invalidAuthentication("JWT is missing");
        }
        try {
            identityContexts.resolve(jwt);
        } catch (ApiErrorException exception) {
            throw invalidAuthentication("organization identity is missing or invalid");
        }
    }

    private AuthorizedContext requireContextPermission(Jwt jwt, ContextPermission permission) {
        AuthorizedContext principal = principalContext(jwt);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principal.tenantId(),
                principal.contextId(),
                principal.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "chat-forbidden",
                    "Chat access is not allowed for this Context/Space.",
                    Map.of(
                            "module", DOMAIN,
                            "contextId", principal.contextId(),
                            "permission", permission.name().toLowerCase(Locale.ROOT),
                            "policyState", "disabled_by_policy",
                            "reason", decision.reason(),
                            "diagnosticsRedacted", true));
        }
        return principal;
    }

    private AuthorizedContext principalContext(Jwt jwt) {
        if (jwt == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Chat access requires an authenticated principal.",
                    Map.of("module", DOMAIN));
        }
        String tenantId;
        try {
            tenantId = identityContexts.resolve(jwt).organizationId();
        } catch (ApiErrorException exception) {
            throw invalidAuthentication("organization identity is missing or invalid");
        }
        String configuredClaim = jwtClaim(jwt, contextAuthorizationProperties.principalClaim());
        String principalRef = contextAuthorizationProperties.principalRef(configuredClaim != null ? configuredClaim : jwt.getSubject());
        if (principalRef == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Chat access requires an authenticated principal.",
                    Map.of("module", DOMAIN, "reason", "principal claim is missing"));
        }
        String contextId = claimOrDefault(jwt, "weave_context_id", "context_id", DEFAULT_CONTEXT_ID);
        return new AuthorizedContext(tenantId, contextId, principalRef);
    }

    private ApiErrorException invalidAuthentication(String reason) {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Chat access requires an authenticated principal.",
                Map.of("module", DOMAIN, "reason", reason));
    }

    private String claimOrDefault(Jwt jwt, String primaryClaim, String fallbackClaim, String defaultValue) {
        String primary = jwtClaim(jwt, primaryClaim);
        if (primary != null) {
            return primary;
        }
        String fallback = jwtClaim(jwt, fallbackClaim);
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiErrorException chatUnavailable(String impactState, String message, String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "chat-unavailable",
                message,
                Map.of(
                        "module", DOMAIN,
                        "operation", operation,
                        "impactState", impactState,
                        "diagnosticsRedacted", true));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
