package com.massimotter.weave.backend.context.authz;

import java.util.Objects;

/**
 * Tenant/context membership entry aligned with the Context Graph contract.
 */
public record ContextMembership(
        String tenantId,
        String contextId,
        String principalRef,
        ContextRole role,
        String source) {

    public ContextMembership {
        tenantId = required("tenantId", tenantId);
        contextId = required("contextId", contextId);
        principalRef = required("principalRef", principalRef);
        Objects.requireNonNull(role, "role must not be null");
        source = required("source", source);
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
