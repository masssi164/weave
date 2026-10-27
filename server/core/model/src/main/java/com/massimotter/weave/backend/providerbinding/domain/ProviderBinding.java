package com.massimotter.weave.backend.providerbinding.domain;

import java.time.Instant;

public record ProviderBinding(
        String organizationRef,
        String domain,
        long revision,
        String adapterKey,
        String configurationRef,
        State state,
        Instant activatedAt) {

    public ProviderBinding {
        organizationRef = required(organizationRef, "organizationRef");
        domain = required(domain, "domain");
        adapterKey = required(adapterKey, "adapterKey");
        configurationRef = required(configurationRef, "configurationRef");
        state = java.util.Objects.requireNonNull(state, "state must not be null");
        activatedAt = java.util.Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
    }

    public enum State { ACTIVE, RETIRED, REVOKED }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
