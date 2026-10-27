package com.massimotter.weave.backend.security.workload.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Closed, server-resolved principal used only for exchanged MCP workload calls.
 * The governing member remains an attribution/authorization lookup and is never
 * represented as the OAuth subject.
 */
public record WeaverWorkloadPrincipal(
        String issuer,
        String workloadSubject,
        String workloadClientId,
        String mcpEdgeClientId,
        String organizationRef,
        String personRef,
        MemberIdentityBinding memberBinding,
        String contextPrincipalClaim,
        String bindingRef,
        String bindingAuditKey,
        String policyRevision,
        String memberEntitlementRevision,
        Instant authorizationExpiresAt,
        Set<String> scopes,
        Set<String> visibleToolClasses) {

    public WeaverWorkloadPrincipal {
        requireText(issuer, "issuer");
        requireText(workloadSubject, "workloadSubject");
        requireText(workloadClientId, "workloadClientId");
        if (!"weave-mcp-server".equals(mcpEdgeClientId)) {
            throw new IllegalArgumentException("mcpEdgeClientId must be weave-mcp-server");
        }
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        Objects.requireNonNull(memberBinding, "memberBinding");
        requireText(contextPrincipalClaim, "contextPrincipalClaim");
        requireText(bindingRef, "bindingRef");
        requireText(bindingAuditKey, "bindingAuditKey");
        requireText(policyRevision, "policyRevision");
        requireText(memberEntitlementRevision, "memberEntitlementRevision");
        Objects.requireNonNull(authorizationExpiresAt, "authorizationExpiresAt");
        scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
        visibleToolClasses = Set.copyOf(visibleToolClasses == null ? Set.of() : visibleToolClasses);
        if (scopes.isEmpty() || visibleToolClasses.isEmpty()) {
            throw new IllegalArgumentException("workload scopes and tool classes must be non-empty");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 500) {
            throw new IllegalArgumentException(field + " is required and bounded");
        }
    }
}
