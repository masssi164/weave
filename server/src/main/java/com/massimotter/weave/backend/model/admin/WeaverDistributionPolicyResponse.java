package com.massimotter.weave.backend.model.admin;

import java.util.List;

public record WeaverDistributionPolicyResponse(
        boolean enabledByDefault,
        String chatProviderKey,
        String chatReadinessState,
        List<String> chatMigrationConsequences,
        List<String> profileRegenerationBlockedReasons,
        List<WeaverModelAliasResponse> modelAliases,
        String defaultModelAlias,
        List<String> fallbackModelAliases,
        List<String> allowedTools,
        List<String> allowedSkills,
        List<WeaverMcpGrantResponse> mcpServers,
        List<String> deniedTools,
        List<String> approvalRequiredFor,
        List<String> effectivePolicyPreview,
        String runtimeProfileHash,
        String pendingRuntimeProfileHash,
        String revocationState,
        String rollbackProfileHash,
        List<String> auditRefs,
        List<WeaverRuntimeProfileChangeResponse> changeHistory) {
    public WeaverDistributionPolicyResponse {
        chatProviderKey = normalize(chatProviderKey, "selected-chat-provider");
        chatReadinessState = normalize(chatReadinessState, "admin-action-required");
        chatMigrationConsequences = copyText(chatMigrationConsequences);
        profileRegenerationBlockedReasons = copyText(profileRegenerationBlockedReasons);
        modelAliases = modelAliases == null ? List.of() : List.copyOf(modelAliases);
        defaultModelAlias = normalize(defaultModelAlias, modelAliases.isEmpty() ? "" : modelAliases.getFirst().alias());
        fallbackModelAliases = copyText(fallbackModelAliases);
        allowedTools = copyText(allowedTools);
        allowedSkills = copyText(allowedSkills);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        deniedTools = copyText(deniedTools);
        approvalRequiredFor = copyText(approvalRequiredFor);
        effectivePolicyPreview = copyText(effectivePolicyPreview);
        runtimeProfileHash = normalize(runtimeProfileHash, "hash-missing");
        pendingRuntimeProfileHash = emptyToNull(pendingRuntimeProfileHash);
        revocationState = normalize(revocationState, "not_revoked");
        rollbackProfileHash = emptyToNull(rollbackProfileHash);
        auditRefs = copyText(auditRefs);
        changeHistory = changeHistory == null ? List.of() : List.copyOf(changeHistory);
    }

    private static List<String> copyText(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
