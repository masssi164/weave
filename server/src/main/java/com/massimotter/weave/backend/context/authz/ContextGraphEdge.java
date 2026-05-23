package com.massimotter.weave.backend.context.authz;

import java.util.Objects;

/**
 * Tenant-scoped edge between Context IDs from the Context Graph contract.
 */
public record ContextGraphEdge(
        String tenantId,
        String fromContextId,
        String toContextId,
        ContextGraphRelation relation) {

    public ContextGraphEdge {
        tenantId = required("tenantId", tenantId);
        fromContextId = required("fromContextId", fromContextId);
        toContextId = required("toContextId", toContextId);
        Objects.requireNonNull(relation, "relation must not be null");
    }

    boolean projectsMembership() {
        return relation == ContextGraphRelation.CONTAINS;
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
