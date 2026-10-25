package com.massimotter.weave.backend.security.device;

import java.time.Instant;
import java.util.Set;

public record DeviceCredential(
        String credentialId,
        String domain,
        String tenantId,
        String principalRef,
        String subject,
        String username,
        String clientType,
        String label,
        Set<String> capabilities,
        String secretHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt) {

    public DeviceCredential {
        credentialId = requireText(credentialId, "credential id");
        domain = requireText(domain, "credential domain");
        tenantId = requireText(tenantId, "tenant id");
        principalRef = requireText(principalRef, "principal reference");
        subject = requireText(subject, "subject");
        username = requireText(username, "username");
        clientType = requireText(clientType, "client type");
        label = requireText(label, "credential label");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        secretHash = requireText(secretHash, "secret hash");
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("device credential requires a valid lifetime");
        }
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && now != null && now.isBefore(expiresAt);
    }

    public DeviceCredential revoke(Instant revokedAt) {
        return new DeviceCredential(
                credentialId,
                domain,
                tenantId,
                principalRef,
                subject,
                username,
                clientType,
                label,
                capabilities,
                secretHash,
                issuedAt,
                expiresAt,
                revokedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
