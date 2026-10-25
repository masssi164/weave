package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Support-safe Chat provider replacement dry-run report for admins/operators.")
public record ChatProviderReplacementDryRunResponse(
        @Schema(description = "Stable dry-run identifier for audit/support correlation.")
        String dryRunId,
        @Schema(description = "Provider category under migration.", example = "chat")
        String category,
        @Schema(description = "Dry-run status.", example = "requires-admin-review")
        String status,
        @Schema(description = "Source adapter key. Admin-side only and never a raw URL or credential.")
        String sourceAdapter,
        @Schema(description = "Target adapter key. Admin-side only and never a raw URL or credential.")
        String targetAdapter,
        Map<String, Integer> inventory,
        List<String> preflight,
        List<String> lossyWarnings,
        List<String> conflicts,
        List<String> reversibleEvidence,
        @Schema(description = "Whether this report is safe for support bundles.", example = "true")
        boolean supportSafe,
        @Schema(description = "Whether provider object identifiers, URLs, usernames, and raw errors are redacted.", example = "true")
        boolean providerDiagnosticsRedacted) {
}
