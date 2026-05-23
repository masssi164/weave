package com.massimotter.weave.backend.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record ProviderStatusResponse(
        ProviderModule module,
        String providerKey,
        ProviderState state,
        String readiness,
        boolean enabled,
        boolean configured,
        boolean readOnly,
        boolean failClosed,
        boolean supportSafe,
        boolean paidFeaturesRequired,
        String summary,
        Set<String> supportedCapabilities,
        Set<String> unsupportedOperations,
        List<String> supportSafeErrorCodes,
        String redactionPolicy,
        List<String> candidates,
        Map<String, Object> diagnostics) {

    public ProviderStatusResponse {
        module = requireNonNull(module, "module must not be null");
        providerKey = requireText(providerKey, "providerKey");
        state = requireNonNull(state, "state must not be null");
        readiness = requireText(readiness, "readiness");
        summary = requireText(summary, "summary");
        supportedCapabilities = supportedCapabilities == null ? Set.of() : Set.copyOf(supportedCapabilities);
        unsupportedOperations = unsupportedOperations == null ? Set.of() : Set.copyOf(unsupportedOperations);
        supportSafeErrorCodes = supportSafeErrorCodes == null ? List.of() : List.copyOf(supportSafeErrorCodes);
        redactionPolicy = requireText(redactionPolicy == null
                ? "support-safe: no tokens, passwords, app passwords, credentials, authorization headers, or raw provider errors"
                : redactionPolicy, "redactionPolicy");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
