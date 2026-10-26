package com.massimotter.weave.backend.discovery.catalog;

/** An owning category's metadata-selection limits; this never authorizes provider cutover. */
public record ProviderCategorySelectionPolicy(
        boolean bindingScoped,
        boolean directApplyAllowed,
        String previewReadiness,
        String deniedCode,
        String deniedMessage) {
    public ProviderCategorySelectionPolicy {
        if (!directApplyAllowed && (deniedCode == null || deniedCode.isBlank()
                || deniedMessage == null || deniedMessage.isBlank())) {
            throw new IllegalArgumentException("Blocked metadata apply requires support-safe denial details");
        }
    }

    public static ProviderCategorySelectionPolicy metadataOnly() {
        return new ProviderCategorySelectionPolicy(false, true, null, null, null);
    }

    public static ProviderCategorySelectionPolicy boundMetadataOnly() {
        return new ProviderCategorySelectionPolicy(true, true, null, null, null);
    }
}
