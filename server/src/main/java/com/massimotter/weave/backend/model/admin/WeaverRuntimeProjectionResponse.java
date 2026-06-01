package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Signed Weaver RuntimeProfile projection preview; contains hashes/refs only, never runtime config or secrets.")
public record WeaverRuntimeProjectionResponse(
        String profileVersion,
        String runtimeProfileHash,
        String expiresAt,
        String regeneratedAt,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        boolean rawRuntimeInternalsExposed,
        boolean disabledByDefault,
        boolean groupChatConsentRequired,
        String sandboxPosture,
        List<String> pendingRevocationRefs,
        List<String> auditReceiptRefs,
        List<WeaverRuntimeProjectionItemResponse> items) {
    public WeaverRuntimeProjectionResponse {
        pendingRevocationRefs = pendingRevocationRefs == null ? List.of() : List.copyOf(pendingRevocationRefs);
        auditReceiptRefs = auditReceiptRefs == null ? List.of() : List.copyOf(auditReceiptRefs);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
