package com.massimotter.weave.backend.agentruntime.domain;

import java.net.URI;
import java.util.regex.Pattern;

public record RuntimeWorkloadPrincipal(String issuer, String subject, String clientId) {
    private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");

    public RuntimeWorkloadPrincipal {
        if (issuer == null || subject == null || subject.isBlank()
                || clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalArgumentException("complete per-cell workload identity is required");
        }
        URI issuerUri = URI.create(issuer);
        if (!"https".equalsIgnoreCase(issuerUri.getScheme()) || issuerUri.getHost() == null
                || issuerUri.getUserInfo() != null || issuerUri.getQuery() != null
                || issuerUri.getFragment() != null) {
            throw new IllegalArgumentException("workload issuer must be an HTTPS issuer URI");
        }
    }
}
