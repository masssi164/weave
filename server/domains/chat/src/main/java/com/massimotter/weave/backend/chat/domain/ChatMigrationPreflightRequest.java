package com.massimotter.weave.backend.chat.domain;

import java.util.List;
import java.util.Map;

public record ChatMigrationPreflightRequest(
        String sourceProviderKey,
        String targetProviderKey,
        boolean dryRun,
        Map<String, Integer> inventoryCounts,
        List<String> expectedLossyFields,
        List<String> conflictHints,
        String reason) {
    public ChatMigrationPreflightRequest {
        inventoryCounts = inventoryCounts == null ? Map.of() : Map.copyOf(inventoryCounts);
        expectedLossyFields = expectedLossyFields == null ? List.of() : List.copyOf(expectedLossyFields);
        conflictHints = conflictHints == null ? List.of() : List.copyOf(conflictHints);
    }
}
