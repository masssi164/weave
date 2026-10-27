package com.massimotter.weave.backend.audit;

final class AuditEventContract {

    private AuditEventContract() {
    }

    static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static String optionalContextId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("provider:") || value.startsWith("provider_binding:")) {
            throw new IllegalArgumentException("contextId must be a Weave Context ID, not a provider binding");
        }
        return value;
    }
}
