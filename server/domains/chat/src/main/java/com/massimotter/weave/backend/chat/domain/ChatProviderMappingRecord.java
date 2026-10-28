package com.massimotter.weave.backend.chat.domain;

import java.util.List;
import java.util.Map;

public record ChatProviderMappingRecord(
        String category,
        String selectedProviderKey,
        String selectionSource,
        boolean selectedByAdmin,
        boolean configured,
        ChatMemberState readinessState,
        boolean failClosed,
        boolean supportSafe,
        boolean secretsReturned,
        boolean downstreamErrorsReturned,
        List<String> lossyMappingWarnings,
        Map<String, Object> supportSafeDiagnostics) {
    public ChatProviderMappingRecord {
        lossyMappingWarnings = lossyMappingWarnings == null ? List.of() : List.copyOf(lossyMappingWarnings);
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
    }
}
