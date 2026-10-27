package com.massimotter.weave.backend.security.workload.domain;

import java.net.URI;

public record MemberIdentityBinding(String issuer, String subject) {
    public MemberIdentityBinding {
        requireHttpsUri(issuer, "member issuer");
        requireText(subject, "member subject");
    }

    public static void requireHttpsUri(String value, String field) {
        requireText(value, field);
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(field + " must be an HTTPS issuer without user info, query, or fragment");
        }
    }

    public static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
