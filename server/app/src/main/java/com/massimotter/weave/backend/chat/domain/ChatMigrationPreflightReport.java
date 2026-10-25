package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatMigrationPreflightReport(
        String preflightId,
        String mode,
        String sourceProviderKey,
        String targetProviderKey,
        ChatMemberState readinessState,
        boolean destructiveApplyAvailable,
        boolean auditEventPublished,
        String auditEventId,
        Map<String, Integer> objectCounts,
        List<String> conflictCategories,
        List<String> lossyFieldWarnings,
        List<String> blockedOperations,
        List<String> supportSafeWarnings,
        Instant checkedAt) {
    public ChatMigrationPreflightReport {
        objectCounts = objectCounts == null ? Map.of() : Map.copyOf(objectCounts);
        conflictCategories = conflictCategories == null ? List.of() : List.copyOf(conflictCategories);
        lossyFieldWarnings = lossyFieldWarnings == null ? List.of() : List.copyOf(lossyFieldWarnings);
        blockedOperations = blockedOperations == null ? List.of() : List.copyOf(blockedOperations);
        supportSafeWarnings = supportSafeWarnings == null ? List.of() : List.copyOf(supportSafeWarnings);
    }
}
