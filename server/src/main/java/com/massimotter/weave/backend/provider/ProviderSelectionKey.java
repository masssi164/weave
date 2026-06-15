package com.massimotter.weave.backend.provider;

import java.util.Locale;

/** Stable storage/lookup key normalization for provider selection categories. */
public final class ProviderSelectionKey {

    private ProviderSelectionKey() {
    }

    public static String category(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }
}
