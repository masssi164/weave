package com.massimotter.weave.backend.service;

import java.util.Map;

public record ProductProfileOverride(
        String displayName,
        String avatar,
        String locale,
        String timezone,
        Map<String, String> accessibilityPreferences,
        String profileVisibility) {

    public ProductProfileOverride {
        accessibilityPreferences = accessibilityPreferences == null ? Map.of() : Map.copyOf(accessibilityPreferences);
    }
}
