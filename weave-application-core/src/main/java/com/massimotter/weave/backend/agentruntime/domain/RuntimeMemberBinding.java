package com.massimotter.weave.backend.agentruntime.domain;

import java.net.URI;

public record RuntimeMemberBinding(String issuer, String subject) {
    public RuntimeMemberBinding {
        requireHttpsUri(issuer, "member issuer");
        requireText(subject, "member subject");
    }

    static void requireHttpsUri(String value, String field) {
        requireText(value, field);
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(field + " must be an HTTPS issuer without user info, query, or fragment");
        }
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
