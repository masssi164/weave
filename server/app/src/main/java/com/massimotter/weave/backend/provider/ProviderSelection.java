package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record ProviderSelection(
        String category,
        String providerKey,
        String choiceModel,
        String secretRef,
        String selectedBy,
        Instant selectedAt,
        boolean applied,
        boolean supportSafe,
        boolean migrationDryRunRequired,
        List<String> lossyMappingNotes) {

    public ProviderSelection {
        category = requireText(category, "category");
        providerKey = requireText(providerKey, "providerKey");
        choiceModel = normalizeChoiceModel(choiceModel);
        secretRef = normalizeSecretRef(secretRef);
        selectedBy = selectedBy == null || selectedBy.isBlank() ? "actor:system" : selectedBy.trim();
        selectedAt = selectedAt == null ? Instant.EPOCH : selectedAt;
        supportSafe = true;
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
    }

    public boolean hasSecretRef() {
        return secretRef != null && !secretRef.isBlank() && secretRef.toLowerCase(Locale.ROOT).startsWith("secretref://");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeChoiceModel(String value) {
        if (value == null || value.isBlank()) {
            return "recommended_self_hosted_default";
        }
        String normalized = value.trim();
        if (normalized.equals("recommended_self_hosted_default")
                || normalized.equals("external_existing_provider")
                || normalized.equals("managed_cloud_provider")) {
            return normalized;
        }
        throw new IllegalArgumentException("choiceModel must be a provider choice contract value");
    }

    private static String normalizeSecretRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            throw new IllegalArgumentException("secretRef must use the SecretRef URI contract");
        }
        return trimmed;
    }
}
