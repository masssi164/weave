package com.massimotter.weave.backend.context.authz;

import java.util.Objects;

/**
 * Context-scoped authorization request. Provider IDs must be resolved to Context IDs before this port is called.
 */
public record ContextAuthorizationRequest(
        String tenantId,
        String contextId,
        String principalRef,
        ContextPermission permission) {

    private static final String CONTEXT_REF_PREFIX = "context:";

    public ContextAuthorizationRequest {
        tenantId = required("tenantId", tenantId);
        contextId = required("contextId", contextId);
        principalRef = required("principalRef", principalRef);
        Objects.requireNonNull(permission, "permission must not be null");
        if (contextId.startsWith("provider:") || contextId.startsWith("provider_binding:")) {
            throw new IllegalArgumentException("contextId must be a Weave Context ID, not a provider binding");
        }
    }

    static String contextObjectRef(String contextId) {
        return CONTEXT_REF_PREFIX + required("contextId", contextId);
    }

    boolean matchesContextObjectRef(String objectRef) {
        return contextObjectRef(contextId).equals(objectRef);
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
