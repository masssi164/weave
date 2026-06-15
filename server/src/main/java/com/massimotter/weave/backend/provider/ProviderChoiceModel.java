package com.massimotter.weave.backend.provider;

import java.util.List;

/** Shared provider selection choice model contract for admin and domain/provider selection flows. */
public final class ProviderChoiceModel {

    public static final String RECOMMENDED_SELF_HOSTED_DEFAULT = "recommended_self_hosted_default";
    public static final String EXTERNAL_EXISTING_PROVIDER = "external_existing_provider";
    public static final String MANAGED_CLOUD_PROVIDER = "managed_cloud_provider";
    public static final String HYBRID_COMPOSITE = "hybrid_composite";

    private static final List<String> SUPPORTED = List.of(
            RECOMMENDED_SELF_HOSTED_DEFAULT,
            EXTERNAL_EXISTING_PROVIDER,
            MANAGED_CLOUD_PROVIDER,
            HYBRID_COMPOSITE);

    private ProviderChoiceModel() {
    }

    public static String defaultValue() {
        return RECOMMENDED_SELF_HOSTED_DEFAULT;
    }

    public static List<String> supportedValues() {
        return SUPPORTED;
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return defaultValue();
        }
        String normalized = value.trim();
        if (SUPPORTED.contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("choiceModel must be a provider choice contract value");
    }
}
