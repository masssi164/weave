package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Provider-neutral Weave domain contract. Adapter names are implementation candidates, not product vocabulary.")
public record CanonicalDomainContract(
        String contractVersion,
        String domain,
        String label,
        List<String> providerCategoryKeys,
        List<String> readCapabilities,
        List<String> writeCapabilities,
        List<String> adapterBoundaryOperations,
        List<String> unsupportedUntilAdapterMapped,
        List<String> canonicalObjectKinds,
        boolean policyEvaluatedBeforeProviderAccess,
        boolean unknownCapabilitiesFailClosed,
        boolean normalMembersConfigureProviders) {

    public CanonicalDomainContract {
        contractVersion = text(contractVersion, "contractVersion");
        domain = text(domain, "domain");
        label = text(label, "label");
        providerCategoryKeys = providerCategoryKeys == null ? List.of() : List.copyOf(providerCategoryKeys);
        readCapabilities = readCapabilities == null ? List.of() : List.copyOf(readCapabilities);
        writeCapabilities = writeCapabilities == null ? List.of() : List.copyOf(writeCapabilities);
        adapterBoundaryOperations = adapterBoundaryOperations == null ? List.of() : List.copyOf(adapterBoundaryOperations);
        unsupportedUntilAdapterMapped = unsupportedUntilAdapterMapped == null ? List.of() : List.copyOf(unsupportedUntilAdapterMapped);
        canonicalObjectKinds = canonicalObjectKinds == null ? List.of() : List.copyOf(canonicalObjectKinds);
        policyEvaluatedBeforeProviderAccess = true;
        unknownCapabilitiesFailClosed = true;
        normalMembersConfigureProviders = false;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
