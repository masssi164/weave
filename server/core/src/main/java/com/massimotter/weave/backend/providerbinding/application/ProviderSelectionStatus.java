package com.massimotter.weave.backend.providerbinding.application;

import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Shared status policy for an explicitly declared category; domains own category/adapter catalogs. */
public final class ProviderSelectionStatus {
    public static final String CONFIGURATION_SOURCE = "admin-control-plane-selected-provider-mappings";

    private ProviderSelectionStatus() {}

    public static ProviderStatusResponse unassigned(ProviderStatusResponse status) {
        return withDiagnostics(status, Map.of(
                "providerConfigSource", CONFIGURATION_SOURCE,
                "selectedByAdmin", false,
                "bootstrapSuggestionOnly", true));
    }

    public static ProviderStatusResponse project(ProviderStatusResponse status, String category,
            Optional<ProviderSelection> maybeSelection) {
        if (category == null || category.isBlank()
                || maybeSelection.filter(selection -> !category.equals(selection.category())).isPresent()) {
            throw new IllegalArgumentException("Provider selection category does not match its owner");
        }
        if (maybeSelection.isEmpty()) {
            return new ProviderStatusResponse(
                    status.module(),
                    status.providerKey(),
                    ProviderState.DISABLED,
                    "awaiting_admin_selection",
                    false,
                    false,
                    status.readOnly(),
                    true,
                    status.supportSafe(),
                    status.paidFeaturesRequired(),
                    status.summary() + " Bootstrap/profile default only; Admin Console has not selected this category mapping.",
                    status.supportedCapabilities(),
                    status.unsupportedOperations(),
                    status.supportSafeErrorCodes(),
                    status.redactionPolicy(),
                    status.candidates(),
                    status.providerRealityLevel(),
                    diagnostics(status, Map.of(
                            "category", category,
                            "providerConfigSource", CONFIGURATION_SOURCE,
                            "selectedByAdmin", false,
                            "bootstrapSuggestionOnly", true,
                            "selectionRequiredBeforeProviderUse", true)));
        }
        ProviderSelection selection = maybeSelection.get();
        boolean selectedProvider = selection.providerKey().equals(status.providerKey())
                || status.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> value.equals(selection.providerKey().toLowerCase(Locale.ROOT)));
        if (!selectedProvider) {
            return new ProviderStatusResponse(
                    status.module(),
                    status.providerKey(),
                    ProviderState.DISABLED,
                    "not_selected_by_admin",
                    false,
                    false,
                    status.readOnly(),
                    true,
                    status.supportSafe(),
                    status.paidFeaturesRequired(),
                    status.summary() + " This adapter candidate is not the Admin Console-selected mapping for " + category + ".",
                    status.supportedCapabilities(),
                    status.unsupportedOperations(),
                    status.supportSafeErrorCodes(),
                    status.redactionPolicy(),
                    status.candidates(),
                    status.providerRealityLevel(),
                    diagnostics(status, Map.of(
                            "category", category,
                            "providerConfigSource", CONFIGURATION_SOURCE,
                            "selectedByAdmin", false,
                            "selectedCategoryProviderKey", selection.providerKey(),
                            "bootstrapSuggestionOnly", false)));
        }
        boolean configured = status.configured();
        ProviderState state = configured ? status.state() : ProviderState.NOT_CONFIGURED;
        String readiness = configured ? status.readiness() : "admin_selected_pending_backend_configuration";
        return new ProviderStatusResponse(
                status.module(),
                status.providerKey(),
                state,
                readiness,
                true,
                configured,
                status.readOnly(),
                true,
                status.supportSafe(),
                status.paidFeaturesRequired(),
                status.summary() + " Admin Console selection is the source of truth; SecretRefs are control-plane handles only and readiness remains backend-owned/support-safe.",
                status.supportedCapabilities(),
                status.unsupportedOperations(),
                status.supportSafeErrorCodes(),
                status.redactionPolicy(),
                status.candidates(),
                status.providerRealityLevel(),
                diagnostics(status, Map.of(
                        "category", category,
                        "providerConfigSource", CONFIGURATION_SOURCE,
                        "selectedByAdmin", true,
                        "choiceModel", selection.choiceModel(),
                        "bootstrapSuggestionOnly", false,
                        "secretRefConfigured", selection.hasSecretRef(),
                        "secretsReturned", false,
                        "rawProviderErrorsReturned", false,
                        "migrationDryRunRequired", selection.migrationDryRunRequired(),
                        "lossyMappingNoteCount", selection.lossyMappingNotes().size())));
    }

    private static ProviderStatusResponse withDiagnostics(ProviderStatusResponse status, Map<String, Object> extra) {
        return new ProviderStatusResponse(
                status.module(), status.providerKey(), status.state(), status.readiness(), status.enabled(), status.configured(),
                status.readOnly(), status.failClosed(), status.supportSafe(), status.paidFeaturesRequired(), status.summary(),
                status.supportedCapabilities(), status.unsupportedOperations(), status.supportSafeErrorCodes(), status.redactionPolicy(),
                status.candidates(), status.providerRealityLevel(), diagnostics(status, extra));
    }

    private static Map<String, Object> diagnostics(ProviderStatusResponse status, Map<String, Object> extra) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(status.diagnostics());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("providerRealityLevel", status.providerRealityLevel().value());
        diagnostics.putAll(extra);
        return diagnostics;
    }
}
