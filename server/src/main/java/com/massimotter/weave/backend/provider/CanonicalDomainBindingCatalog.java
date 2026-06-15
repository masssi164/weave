package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistry;
import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistryEntryResponse;
import java.util.List;
import java.util.Optional;

/**
 * Maps provider category taxonomy to canonical domain identifiers.
 *
 * <p>Provider categories are adapter/admin grouping metadata. Public domain binding and adapter registry surfaces
 * must use canonical domain keys from {@link CanonicalDomainRegistry}; compatibility aliases in that registry are the
 * only bridge from legacy/category keys to stable domain identities.</p>
 */
final class CanonicalDomainBindingCatalog {

    private static final List<String> INTERNAL_OR_NON_BINDING_DOMAINS = List.of("health", "weaver");

    private CanonicalDomainBindingCatalog() {
    }

    static List<CanonicalDomainRegistryEntryResponse> domainsForCategory(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return List.of();
        }
        return CanonicalDomainRegistry.domains().stream()
                .filter(domain -> !INTERNAL_OR_NON_BINDING_DOMAINS.contains(domain.key()))
                .filter(domain -> domain.key().equals(categoryKey) || domain.compatibilityAliases().contains(categoryKey))
                .toList();
    }

    static Optional<CanonicalDomainRegistryEntryResponse> primaryDomainForCategory(String categoryKey) {
        return domainsForCategory(categoryKey).stream().findFirst();
    }

    static String stableBindingId(CanonicalDomainRegistryEntryResponse domain, String providerKey) {
        return "binding:" + requireDomain(domain).key() + ":provider:" + requireText(providerKey, "providerKey");
    }

    static String stableConnectionId(String providerKey) {
        return "provider-connection:" + requireText(providerKey, "providerKey");
    }

    private static CanonicalDomainRegistryEntryResponse requireDomain(CanonicalDomainRegistryEntryResponse domain) {
        if (domain == null || domain.key() == null || domain.key().isBlank()) {
            throw new IllegalArgumentException("domain must have a canonical key");
        }
        return domain;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
