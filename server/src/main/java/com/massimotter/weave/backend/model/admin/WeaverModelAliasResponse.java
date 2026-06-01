package com.massimotter.weave.backend.model.admin;

import java.util.Objects;

public record WeaverModelAliasResponse(
        String alias,
        String provider,
        String model,
        boolean userSelectable) {
    public WeaverModelAliasResponse {
        alias = requireText(alias, "alias");
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Weaver model alias " + field + " is required.");
        }
        return Objects.requireNonNull(value).trim();
    }
}
