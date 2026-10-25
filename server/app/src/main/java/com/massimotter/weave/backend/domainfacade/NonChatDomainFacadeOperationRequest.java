package com.massimotter.weave.backend.domainfacade;

import com.massimotter.weave.backend.context.authz.ContextPermission;

/**
 * Canonical operation gate input shared by Files, Calendar, and Boards facades.
 *
 * The request names Weave domain/context identifiers only. Provider-native IDs must already be mapped to canonical
 * references before product/runtime callers reach this seam.
 */
public record NonChatDomainFacadeOperationRequest(
        String tenantId,
        String contextId,
        String actorRef,
        String capability,
        String operation,
        ContextPermission permission,
        boolean writeOrDelete,
        String canonicalObjectRef,
        String provenanceRef,
        boolean dryRun) {

    public NonChatDomainFacadeOperationRequest {
        tenantId = required("tenantId", tenantId);
        contextId = required("contextId", contextId);
        actorRef = required("actorRef", actorRef);
        capability = required("capability", capability);
        operation = required("operation", operation);
        permission = java.util.Objects.requireNonNull(permission, "permission must not be null");
        canonicalObjectRef = canonicalObjectRef == null || canonicalObjectRef.isBlank() ? "pending-canonical-ref" : canonicalObjectRef;
        provenanceRef = provenanceRef == null || provenanceRef.isBlank() ? "mapping:pending" : provenanceRef;
        if (canonicalObjectRef.startsWith("provider:") || provenanceRef.startsWith("provider:")) {
            throw new IllegalArgumentException("public facade decisions must use canonical refs, not provider-native refs");
        }
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
