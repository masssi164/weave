package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;
import java.util.Map;

public record ChatReadiness(
        String contractVersion,
        String domain,
        ChatMemberState memberState,
        String memberImpact,
        boolean failClosed,
        boolean supportSafe,
        boolean memberClientMayConfigureProvider,
        boolean downstreamDiagnosticsExposedToMember,
        boolean migrationDryRunRequired,
        ChatProviderMappingRecord providerMapping,
        ChatHistoryPolicy defaultHistoryPolicy,
        Map<String, Object> supportSafeDiagnostics,
        Instant checkedAt) {
    public ChatReadiness {
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
    }
}
