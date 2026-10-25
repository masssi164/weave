package com.massimotter.weave.backend.context.authz;

/**
 * ReBAC tuple shape from the Context Graph contract.
 */
public record ContextRelationTuple(
        String tenantId,
        String objectRef,
        String relation,
        String subjectRef,
        String caveat) {

    public ContextRelationTuple {
        tenantId = required("tenantId", tenantId);
        objectRef = required("objectRef", objectRef);
        relation = required("relation", relation);
        subjectRef = required("subjectRef", subjectRef);
        if (caveat != null && caveat.isBlank()) {
            caveat = null;
        }
    }

    public static ContextRelationTuple contextTuple(
            String tenantId,
            String contextId,
            ContextRelation relation,
            String subjectRef) {
        return new ContextRelationTuple(
                tenantId,
                ContextAuthorizationRequest.contextObjectRef(contextId),
                relation.wireValue(),
                subjectRef,
                null);
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
