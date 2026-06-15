package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Support-safe provider connection reference for Admin domain bindings. Contains SecretRef/GrantRef metadata only; never raw credentials, endpoints, or provider payloads.")
public record ProviderConnectionRefResponse(
        String providerKey,
        String connectionId,
        List<String> domainKeys,
        ProviderCategoryReadiness status,
        String credentialRefKind,
        boolean credentialRefConfigured,
        List<String> scopes,
        Instant lastReadinessCheck,
        boolean readOnlyDiscovery,
        boolean supportSafe) {

    public ProviderConnectionRefResponse {
        providerKey = requireText(providerKey, "providerKey");
        connectionId = requireText(connectionId, "connectionId");
        domainKeys = domainKeys == null ? List.of() : List.copyOf(domainKeys);
        status = status == null ? ProviderCategoryReadiness.DISABLED : status;
        credentialRefKind = credentialRefKind == null || credentialRefKind.isBlank() ? "none" : credentialRefKind.trim();
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        lastReadinessCheck = lastReadinessCheck == null ? Instant.EPOCH : lastReadinessCheck;
        readOnlyDiscovery = true;
        supportSafe = true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
