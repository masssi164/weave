package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistryEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Builds the generic domain-binding surface from provider-category readiness.
 *
 * <p>Provider categories are adapter/control-plane taxonomy. This service keeps the domain binding contract centered on
 * canonical domain registry entries, stable connection identifiers, and redacted connection handles so future domains can
 * reuse the same mapping path without adding provider-specific controller logic.</p>
 */
@Service
public class DomainBindingService {

    public DomainBindingsResponse bindings(ProviderRegistryResponse snapshot, String requestedDomainKey) {
        if (snapshot == null) {
            throw new IllegalArgumentException("provider registry snapshot must not be null");
        }
        String normalizedDomainKey = normalize(requestedDomainKey);
        List<DomainBindingResponse> bindings = snapshot.categories().stream()
                .flatMap(category -> CanonicalDomainBindingCatalog.domainsForCategory(category.category()).stream()
                        .filter(domain -> matchesRequestedDomain(domain, normalizedDomainKey))
                        .map(domain -> binding(
                                domain,
                                category,
                                snapshot.selectedProviderMappings(),
                                snapshot.generatedAt())))
                .toList();
        return new DomainBindingsResponse(
                "domain-binding-provider-connection-v1",
                ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                true,
                false,
                bindings.stream().allMatch(DomainBindingResponse::supportSafe),
                snapshot.generatedAt(),
                bindings);
    }

    private boolean matchesRequestedDomain(CanonicalDomainRegistryEntryResponse domain, String requestedDomainKey) {
        return requestedDomainKey == null || domain.key().equals(requestedDomainKey);
    }

    private DomainBindingResponse binding(
            CanonicalDomainRegistryEntryResponse domain,
            ProviderCategoryStatusResponse category,
            List<ProviderSelection> selections,
            Instant generatedAt) {
        String activeBinding = category.selectedByAdmin()
                ? CanonicalDomainBindingCatalog.stableBindingId(domain, category.selectedProviderKey())
                : null;
        ProviderConnectionRefResponse connectionRef = category.selectedByAdmin()
                ? connectionRef(domain, category, selections, generatedAt)
                : null;
        List<String> transitionArtifacts = category.contract().replacementRequirement() == null
                ? List.of("preflight_or_impact_report_required_for_attach_existing_switch_export_import_migration_cutover_rollback")
                : List.of(category.contract().replacementRequirement());
        return new DomainBindingResponse(
                domain.key(),
                domain.displayName(),
                category.readiness(),
                activeBinding,
                connectionRef,
                DomainAdapterRegistryMapper.fromCategory(category).candidates(),
                category.adapterEvidence(),
                transitionArtifacts,
                true,
                true);
    }

    private ProviderConnectionRefResponse connectionRef(
            CanonicalDomainRegistryEntryResponse domain,
            ProviderCategoryStatusResponse category,
            List<ProviderSelection> selections,
            Instant generatedAt) {
        Optional<ProviderSelection> selection = selections.stream()
                .filter(value -> value.category().equals(category.category()))
                .findFirst();
        boolean hasSecretRef = selection.map(ProviderSelection::hasSecretRef).orElse(false);
        return new ProviderConnectionRefResponse(
                category.selectedProviderKey(),
                CanonicalDomainBindingCatalog.stableConnectionId(category.selectedProviderKey()),
                List.of(domain.key()),
                category.readiness(),
                hasSecretRef ? "SecretRef" : "GrantRef-or-none",
                hasSecretRef,
                domain.capabilityKeys(),
                generatedAt,
                true,
                true);
    }

    private String normalize(String requestedDomainKey) {
        if (requestedDomainKey == null || requestedDomainKey.isBlank()) {
            return null;
        }
        return requestedDomainKey.trim();
    }
}
